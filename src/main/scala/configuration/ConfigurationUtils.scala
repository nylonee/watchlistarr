package configuration

import cats.effect.IO
import cats.implicits.toTraverseOps
import cats.implicits._
import http.HttpClient
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.Json
import org.http4s.{Method, Uri}
import org.slf4j.LoggerFactory
import plex.RssFeedGenerated

import scala.concurrent.duration._

object ConfigurationUtils {

  private val logger = LoggerFactory.getLogger(getClass)

  private val possibleLocalHosts: Seq[String] = Seq(
    "http://localhost",
    "localhost",
    "127.0.0.1",
    "http://host.docker.internal",
    "host.docker.internal"
  )

  def create(configReader: ConfigurationReader, client: HttpClient): IO[Configuration] = {
    val config = for {
      sonarrConfig <- getSonarrConfig(configReader, client)
      refreshInterval = configReader.getConfigOption(Keys.intervalSeconds).flatMap(_.toIntOption).getOrElse(60).seconds
      (sonarrBaseUrl, sonarrApiKey, sonarrQualityProfileId, sonarrRootFolder, sonarrLanguageProfileId, sonarrTagIds) =
        sonarrConfig
      sonarrBypassIgnored    = configReader.getConfigOption(Keys.sonarrBypassIgnored).exists(_.toBoolean)
      sonarrSeasonMonitoring = configReader.getConfigOption(Keys.sonarrSeasonMonitoring).getOrElse("all")
      radarrConfig <- getRadarrConfig(configReader, client)
      (radarrBaseUrl, radarrApiKey, radarrQualityProfileId, radarrRootFolder, radarrTagIds) = radarrConfig
      radarrMinimumAvailability = configReader.getConfigOption(Keys.radarrMinimumAvailability).getOrElse("released")
      radarrBypassIgnored       = configReader.getConfigOption(Keys.radarrBypassIgnored).exists(_.toBoolean)
      plexTokens                = getPlexTokens(configReader)
      skipFriendSync = configReader.getConfigOption(Keys.skipFriendSync).flatMap(_.toBooleanOption).getOrElse(false)
      plexWatchlistUrls <- getPlexWatchlistUrls(client)(configReader, plexTokens, skipFriendSync)
      deleteMovies     = configReader.getConfigOption(Keys.deleteMovies).flatMap(_.toBooleanOption).getOrElse(false)
      deleteEndedShows = configReader.getConfigOption(Keys.deleteEndedShow).flatMap(_.toBooleanOption).getOrElse(false)
      deleteContinuingShows = configReader
        .getConfigOption(Keys.deleteContinuingShow)
        .flatMap(_.toBooleanOption)
        .getOrElse(false)
      deleteInterval = configReader.getConfigOption(Keys.deleteIntervalDays).flatMap(_.toIntOption).getOrElse(7).days
      deleteFiles    = configReader.getConfigOption(Keys.deleteFiles).flatMap(_.toBooleanOption).getOrElse(true)
      hasPlexPass    = plexWatchlistUrls.nonEmpty
    } yield Configuration(
      if (hasPlexPass) refreshInterval else 19.minutes,
      SonarrConfiguration(
        sonarrBaseUrl,
        sonarrApiKey,
        sonarrQualityProfileId,
        sonarrRootFolder,
        sonarrBypassIgnored,
        sonarrSeasonMonitoring,
        sonarrLanguageProfileId,
        sonarrTagIds
      ),
      RadarrConfiguration(
        radarrBaseUrl,
        radarrApiKey,
        radarrQualityProfileId,
        radarrRootFolder,
        radarrMinimumAvailability,
        radarrBypassIgnored,
        radarrTagIds
      ),
      PlexConfiguration(
        plexWatchlistUrls,
        plexTokens,
        skipFriendSync,
        hasPlexPass
      ),
      DeleteConfiguration(
        deleteMovies,
        deleteEndedShows,
        deleteContinuingShows,
        deleteInterval,
        deleteFiles
      )
    )

    config.map { c =>
      logger.info(ConfigurationRedactor.redactToString(c))
      c
    }
  }

  private def findCorrectUrl(client: HttpClient)(urls: Seq[String], apiKey: String): IO[Uri] =
    urls match {
      case Seq(url) =>
        // We've reached the end of the list, let's skip the check and just pretend this is the good one
        // We'll error later if it isn't
        IO.pure(Uri.unsafeFromString(url))
      case head :: tail =>
        Uri.fromString(head).toOption match {
          case Some(url) =>
            val maybeResponse = toArr(client)(url, apiKey, "health")

            maybeResponse.flatMap {
              case Right(_) =>
                IO.pure(url)
              case Left(_) =>
                findCorrectUrl(client)(tail, apiKey)
            }

          case None =>
            findCorrectUrl(client)(tail, apiKey)
        }
    }

  private def getSonarrConfig(
      configReader: ConfigurationReader,
      client: HttpClient
  ): IO[(Uri, String, Int, String, Int, Set[Int])] = {
    val apiKey = configReader.getConfigOption(Keys.sonarrApiKey).getOrElse(throwError("Unable to find sonarr API key"))
    val configuredUrl = configReader.getConfigOption(Keys.sonarrBaseUrl)
    val possibleUrls: Seq[String] =
      configuredUrl.map("http://" + _).toSeq ++ configuredUrl.toSeq ++ possibleLocalHosts.map(_ + ":8989")

    for {
      url <- findCorrectUrl(client)(possibleUrls, apiKey)
      rootFolder <- toArr(client)(url, apiKey, "rootFolder").map {
        case Right(res) =>
          logger.info("Successfully connected to Sonarr")
          val allRootFolders = res.as[List[RootFolder]].getOrElse(List.empty)
          selectRootFolder(allRootFolders, configReader.getConfigOption(Keys.sonarrRootFolder))
        case Left(err) =>
          throwError(s"Unable to connect to Sonarr at $url, with error $err")
      }
      qualityProfileId <- toArr(client)(url, apiKey, "qualityprofile").map {
        case Right(res) =>
          val allQualityProfiles   = res.as[List[QualityProfile]].getOrElse(List.empty)
          val chosenQualityProfile = configReader.getConfigOption(Keys.sonarrQualityProfile)
          getQualityProfileId(allQualityProfiles, chosenQualityProfile)
        case Left(err) =>
          throwError(s"Unable to connect to Sonarr at $url, with error $err")
      }
      languageProfileId <- toArr(client)(url, apiKey, "languageprofile").map {
        case Right(res) =>
          val allLanguageProfiles = res.as[List[LanguageProfile]].getOrElse(List.empty)
          allLanguageProfiles.headOption.map(_.id).getOrElse {
            logger.warn("Unable to find a language profile, using 1 as default")
            1
          }
        case Left(err) =>
          throwError(s"Unable to connect to Sonarr at $url, with error $err")
      }
      tagIds <- configReader
        .getConfigOption(Keys.sonarrTags)
        .map(getTagIdsFromConfig(client, url, apiKey))
        .getOrElse(IO.pure(Set.empty[Int]))
    } yield (url, apiKey, qualityProfileId, rootFolder, languageProfileId, tagIds)
  }

  private def getRadarrConfig(
      configReader: ConfigurationReader,
      client: HttpClient
  ): IO[(Uri, String, Int, String, Set[Int])] = {
    val apiKey = configReader.getConfigOption(Keys.radarrApiKey).getOrElse(throwError("Unable to find radarr API key"))
    val configuredUrl = configReader.getConfigOption(Keys.radarrBaseUrl)
    val possibleUrls: Seq[String] =
      configuredUrl.map("http://" + _).toSeq ++ configuredUrl.toSeq ++ possibleLocalHosts.map(_ + ":7878")

    for {
      url <- findCorrectUrl(client)(possibleUrls, apiKey)
      rootFolder <- toArr(client)(url, apiKey, "rootFolder").map {
        case Right(res) =>
          logger.info("Successfully connected to Radarr")
          val allRootFolders = res.as[List[RootFolder]].getOrElse(List.empty)
          selectRootFolder(allRootFolders, configReader.getConfigOption(Keys.radarrRootFolder))
        case Left(err) =>
          throwError(s"Unable to connect to Radarr at $url, with error $err")
      }
      qualityProfileId <- toArr(client)(url, apiKey, "qualityprofile").map {
        case Right(res) =>
          val allQualityProfiles   = res.as[List[QualityProfile]].getOrElse(List.empty)
          val chosenQualityProfile = configReader.getConfigOption(Keys.radarrQualityProfile)
          getQualityProfileId(allQualityProfiles, chosenQualityProfile)
        case Left(err) =>
          throwError(s"Unable to connect to Radarr at $url, with error $err")
      }
      tagIds <- configReader
        .getConfigOption(Keys.radarrTags)
        .map(getTagIdsFromConfig(client, url, apiKey))
        .getOrElse(IO.pure(Set.empty[Int]))
    } yield (url, apiKey, qualityProfileId, rootFolder, tagIds)
  }

  private def getTagIdsFromConfig(client: HttpClient, url: Uri, apiKey: String)(tags: String): IO[Set[Int]] = {
    val tagsSplit = tags.split(',').map(_.trim).toSet

    tagsSplit
      .map { tagName =>
        val json = Json.obj(("label", Json.fromString(tagName.toLowerCase)))
        logger.info(s"Fetching information for tag: $tagName")
        toArr(client)(url, apiKey, "tag", Some(json)).map {
          case Left(err) =>
            logger.warn(s"Attempted to set a tag in an Arr app but got the error: $err")
            None
          case Right(result) =>
            result.hcursor.get[Int]("id").toOption
        }
      }
      .toList
      .sequence
      .map(_.collect { case Some(r) =>
        r
      })
      .map(_.toSet)
  }

  private def getQualityProfileId(allProfiles: List[QualityProfile], maybeEnvVariable: Option[String]): Int =
    (allProfiles, maybeEnvVariable) match {
      case (Nil, _) =>
        throwError("Could not find any quality profiles defined, check your Sonarr/Radarr settings")
      case (List(one), _) =>
        logger.debug(s"Only one quality profile defined: ${one.name}")
        one.id
      case (_, None) =>
        logger.debug("Multiple quality profiles found, selecting the first one in the list.")
        allProfiles.head.id
      case (_, Some(profileName)) =>
        allProfiles
          .find(_.name.toLowerCase == profileName.toLowerCase)
          .map(_.id)
          .getOrElse(
            throwError(s"Unable to find quality profile $profileName. Possible values are $allProfiles")
          )
    }

  private def selectRootFolder(allRootFolders: List[RootFolder], maybeEnvVariable: Option[String]): String =
    (allRootFolders, maybeEnvVariable) match {
      case (Nil, _) =>
        throwError("Could not find any root folders, check your Sonarr/Radarr settings")
      case (_, Some(path)) =>
        allRootFolders
          .filter(_.accessible)
          .find(r => normalizePath(r.path) == normalizePath(path))
          .map(_.path)
          .getOrElse(
            throwError(
              s"Unable to find root folder $path. Possible values are ${allRootFolders.filter(_.accessible).map(_.path)}"
            )
          )
      case (_, None) =>
        allRootFolders
          .find(_.accessible)
          .map(_.path)
          .getOrElse(
            throwError("Found root folders, but they are not accessible by Sonarr/Radarr")
          )
    }

  private def normalizePath(path: String): String =
    (if (path.endsWith("/") && path.length > 1) path.dropRight(1) else path)
      .replace("//", "/")

  private def getPlexWatchlistUrls(
      client: HttpClient
  )(configReader: ConfigurationReader, tokens: Set[String], skipFriendSync: Boolean): IO[Set[Uri]] = {
    val watchlistsFromConfigDeprecated = Set(
      configReader.getConfigOption(Keys.plexWatchlist1),
      configReader.getConfigOption(Keys.plexWatchlist2)
    ).collect { case Some(url) =>
      url
    }

    val watchlistsFromTokenIo = tokens
      .map { token =>
        for {
          selfWatchlist <- getRssFromPlexToken(client)(token, "watchlist")
          _ = logger.info(s"Generated watchlist RSS feed for self: $selfWatchlist")
          otherWatchlist <-
            if (skipFriendSync)
              IO.pure(None)
            else {
              getRssFromPlexToken(client)(token, "friendsWatchlist")
            }
          _ = logger.info(s"Generated watchlist RSS feed for friends: $otherWatchlist")
        } yield Set(selfWatchlist, otherWatchlist).collect { case Some(url) =>
          url
        }
      }
      .toList
      .sequence
      .map(_.flatten)

    watchlistsFromTokenIo.map { watchlistsFromToken =>
      (watchlistsFromConfigDeprecated ++ watchlistsFromToken).toList match {
        case Nil =>
          logger.warn("Missing RSS URL. Are you an active Plex Pass user?")
          logger.warn("Real-time RSS sync disabled")
          Set.empty
        case other =>
          other.map(toPlexUri).toSet
      }
    }
  }

  private def getPlexTokens(configReader: ConfigurationReader): Set[String] =
    configReader.getConfigOption(Keys.plexToken) match {
      case Some(rawToken) =>
        rawToken.split(',').toSet
      case None =>
        logger.warn("Missing plex token")
        Set.empty
    }

  private def toPlexUri(url: String): Uri = {
    val supportedHosts = List(
      "rss.plex.tv"
    ).map(Uri.Host.unsafeFromString)

    val rawUri = Uri
      .fromString(url)
      .getOrElse(
        throwError(s"Plex watchlist $url is not a valid uri")
      )

    val host = rawUri.host.getOrElse(
      throwError(s"Plex watchlist host not found in $rawUri")
    )

    if (!supportedHosts.contains(host))
      throwError(s"Unsupported Uri host on watchlist: ${rawUri.host}. Accepted hosts: $supportedHosts")

    rawUri
  }

  private def throwError(message: String): Nothing = {
    logger.error(message)
    throw new IllegalArgumentException(message)
  }

  private def toArr(
      client: HttpClient
  )(baseUrl: Uri, apiKey: String, endpoint: String, payload: Option[Json] = None): IO[Either[Throwable, Json]] =
    payload match {
      case None =>
        client.httpRequest(Method.GET, baseUrl / "api" / "v3" / endpoint, Some(apiKey))
      case Some(_) =>
        client.httpRequest(Method.POST, baseUrl / "api" / "v3" / endpoint, Some(apiKey), payload)
    }

  private def getRssFromPlexToken(client: HttpClient)(token: String, rssType: String): IO[Option[String]] = {
    val url = Uri
      .unsafeFromString("https://discover.provider.plex.tv/rss")
      .withQueryParam("X-Plex-Token", token)
      .withQueryParam("X-Plex-Client-Identifier", "watchlistarr")

    val body = Json.obj(("feedType", Json.fromString(rssType)))

    client.httpRequest(Method.POST, url, None, Some(body)).map {
      case Left(err) =>
        logger.warn(s"Unable to generate an RSS feed: $err")
        None
      case Right(json) =>
        logger.debug("Got a result from Plex when generating RSS feed, attempting to decode")
        json.as[RssFeedGenerated].map(_.RSSInfo.headOption.map(_.url)) match {
          case Left(err) =>
            logger.warn(s"Unable to decode RSS generation response: $err, returning None instead")
            None
          case Right(url) =>
            url
        }
    }
  }

}
