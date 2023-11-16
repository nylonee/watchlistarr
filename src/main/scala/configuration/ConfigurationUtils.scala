package configuration

import cats.effect.IO
import http.HttpClient
import io.circe.generic.auto._
import io.circe.Json
import org.http4s.{Method, Uri}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object ConfigurationUtils {

  private val logger = LoggerFactory.getLogger(getClass)

  def create(configReader: ConfigurationReader, client: HttpClient): IO[Configuration] =
    for {
      sonarrConfig <- getSonarrConfig(configReader, client)
      refreshInterval = configReader.getConfigOption(Keys.intervalSeconds).flatMap(_.toIntOption).getOrElse(60).seconds
      (sonarrBaseUrl, sonarrApiKey, sonarrQualityProfileId, sonarrRootFolder) = sonarrConfig
      sonarrBypassIgnored = configReader.getConfigOption(Keys.sonarrBypassIgnored).exists(_.toBoolean)
      sonarrSeasonMonitoring = configReader.getConfigOption(Keys.sonarrSeasonMonitoring).getOrElse("all")
      radarrConfig <- getRadarrConfig(configReader, client)
      (radarrBaseUrl, radarrApiKey, radarrQualityProfileId, radarrRootFolder) = radarrConfig
      radarrBypassIgnored = configReader.getConfigOption(Keys.radarrBypassIgnored).exists(_.toBoolean)
      plexWatchlistUrls = getPlexWatchlistUrls(configReader)
      plexToken = configReader.getConfigOption(Keys.plexToken)
    } yield Configuration(
      refreshInterval,
      sonarrBaseUrl,
      sonarrApiKey,
      sonarrQualityProfileId,
      sonarrRootFolder,
      sonarrBypassIgnored,
      sonarrSeasonMonitoring,
      radarrBaseUrl,
      radarrApiKey,
      radarrQualityProfileId,
      radarrRootFolder,
      radarrBypassIgnored,
      plexWatchlistUrls,
      plexToken
    )

  private def getSonarrConfig(configReader: ConfigurationReader, client: HttpClient): IO[(Uri, String, Int, String)] = {
    val url = configReader.getConfigOption(Keys.sonarrBaseUrl).flatMap(Uri.fromString(_).toOption).getOrElse {
      val default = "http://localhost:8989"
      logger.warn(s"Unable to fetch sonarr baseUrl, using default $default")
      Uri.unsafeFromString(default)
    }
    val apiKey = configReader.getConfigOption(Keys.sonarrApiKey).getOrElse(throwError("Unable to find sonarr API key"))

    getToArr(client)(url, apiKey, "rootFolder").map {
      case Right(res) =>
        logger.info("Successfully connected to Sonarr")
        val allRootFolders = res.as[List[RootFolder]].getOrElse(List.empty)
        selectRootFolder(allRootFolders, configReader.getConfigOption(Keys.sonarrRootFolder))
      case Left(err) =>
        throwError(s"Unable to connect to Sonarr at $url, with error $err")
    }.flatMap(rootFolder =>
      getToArr(client)(url, apiKey, "qualityprofile").map {
        case Right(res) =>
          val allQualityProfiles = res.as[List[QualityProfile]].getOrElse(List.empty)
          val chosenQualityProfile = configReader.getConfigOption(Keys.sonarrQualityProfile)
          (url, apiKey, getQualityProfileId(allQualityProfiles, chosenQualityProfile), rootFolder)
        case Left(err) =>
          throwError(s"Unable to connect to Sonarr at $url, with error $err")
      }
    )
  }

  private def getRadarrConfig(configReader: ConfigurationReader, client: HttpClient): IO[(Uri, String, Int, String)] = {
    val url = configReader.getConfigOption(Keys.radarrBaseUrl).flatMap(Uri.fromString(_).toOption).getOrElse {
      val default = "http://localhost:7878"
      logger.warn(s"Unable to fetch radarr baseUrl, using default $default")
      Uri.unsafeFromString(default)
    }
    val apiKey = configReader.getConfigOption(Keys.radarrApiKey).getOrElse(throwError("Unable to find radarr API key"))

    getToArr(client)(url, apiKey, "rootFolder").map {
      case Right(res) =>
        logger.info("Successfully connected to Radarr")
        val allRootFolders = res.as[List[RootFolder]].getOrElse(List.empty)
        selectRootFolder(allRootFolders, configReader.getConfigOption(Keys.radarrRootFolder))
      case Left(err) =>
        throwError(s"Unable to connect to Radarr at $url, with error $err")
    }.flatMap(rootFolder =>
      getToArr(client)(url, apiKey, "qualityprofile").map {
        case Right(res) =>
          val allQualityProfiles = res.as[List[QualityProfile]].getOrElse(List.empty)
          val chosenQualityProfile = configReader.getConfigOption(Keys.radarrQualityProfile)
          (url, apiKey, getQualityProfileId(allQualityProfiles, chosenQualityProfile), rootFolder)
        case Left(err) =>
          throwError(s"Unable to connect to Radarr at $url, with error $err")
      }
    )
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
        allProfiles.find(_.name.toLowerCase == profileName.toLowerCase).map(_.id).getOrElse(
          throwError(s"Unable to find quality profile $profileName. Possible values are $allProfiles")
        )
    }

  private def selectRootFolder(allRootFolders: List[RootFolder], maybeEnvVariable: Option[String]): String =
    (allRootFolders, maybeEnvVariable) match {
      case (Nil, _) =>
        throwError("Could not find any root folders, check your Sonarr/Radarr settings")
      case (_, Some(path)) =>
        allRootFolders.filter(_.accessible).find(r => normalizePath(r.path) == normalizePath(path)).map(_.path).getOrElse(
          throwError(s"Unable to find root folder $path. Possible values are ${allRootFolders.filter(_.accessible).map(_.path)}")
        )
      case (_, None) =>
        allRootFolders.find(_.accessible).map(_.path).getOrElse(
          throwError("Found root folders, but they are not accessible by Sonarr/Radarr")
        )
    }

  private def normalizePath(path: String): String = if (path.endsWith("/") && path.length > 1) path.dropRight(1) else path

  private def getPlexWatchlistUrls(configReader: ConfigurationReader): List[Uri] =
    Set(
      configReader.getConfigOption(Keys.plexWatchlist1),
      configReader.getConfigOption(Keys.plexWatchlist2)
    ).toList.collect {
      case Some(url) => url
    } match {
      case Nil =>
        throwError("Missing plex watchlist URL")
      case other => other.map(toPlexUri)
    }

  private def toPlexUri(url: String): Uri = {
    val supportedHosts = List(
      "rss.plex.tv"
    ).map(Uri.Host.unsafeFromString)

    val rawUri = Uri.fromString(url).getOrElse(
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

  private def getToArr(client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String): IO[Either[Throwable, Json]] =
    client.httpRequest(Method.GET, baseUrl / "api" / "v3" / endpoint, Some(apiKey))

}
