package configuration

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import model.QualityProfile
import org.http4s.Uri
import org.slf4j.LoggerFactory
import utils.{ArrUtils, HttpClient}

import scala.concurrent.duration._

class Configuration(configReader: ConfigurationReader, val client: HttpClient) {

  private val logger = LoggerFactory.getLogger(getClass)

  val refreshInterval: FiniteDuration = configReader.getConfigOption(Keys.intervalSeconds).flatMap(_.toIntOption).getOrElse(60).seconds

  val (sonarrBaseUrl, sonarrApiKey, sonarrQualityProfileId) = getAndTestSonarrUrlAndApiKey.unsafeRunSync()
  val sonarrRootFolder: String = configReader.getConfigOption(Keys.sonarrRootFolder).getOrElse("/data/")
  val sonarrBypassIgnored: Boolean = configReader.getConfigOption(Keys.sonarrBypassIgnored).exists(_.toBoolean)

  val (radarrBaseUrl, radarrApiKey, radarrQualityProfileId) = getAndTestRadarrUrlAndApiKey.unsafeRunSync()
  val radarrRootFolder: String = configReader.getConfigOption(Keys.radarrRootFolder).getOrElse("/data/")
  val radarrBypassIgnored: Boolean = configReader.getConfigOption(Keys.radarrBypassIgnored).exists(_.toBoolean)

  val plexWatchlistUrls: List[Uri] = getPlexWatchlistUrls

  private def getAndTestSonarrUrlAndApiKey: IO[(Uri, String, Int)] = {
    val url = configReader.getConfigOption(Keys.sonarrBaseUrl).flatMap(Uri.fromString(_).toOption).getOrElse {
      val default = "http://localhost:8989"
      logger.warn(s"Unable to fetch sonarr baseUrl, using default $default")
      Uri.unsafeFromString(default)
    }
    val apiKey = configReader.getConfigOption(Keys.sonarrApiKey).getOrElse(throwError("Unable to find sonarr API key"))

    ArrUtils.getToArr(client)(url, apiKey, "qualityprofile").map {
      case Right(res) =>
        logger.info("Successfully connected to Sonarr")
        val allQualityProfiles = res.as[List[QualityProfile]].getOrElse(List.empty)
        val chosenQualityProfile = configReader.getConfigOption(Keys.sonarrQualityProfile)
        (url, apiKey, getQualityProfileId(allQualityProfiles, chosenQualityProfile))
      case Left(err) =>
        throwError(s"Unable to connect to Sonarr at $url, with error $err")
    }
  }

  private def getAndTestRadarrUrlAndApiKey: IO[(Uri, String, Int)] = {
    val url = configReader.getConfigOption(Keys.radarrBaseUrl).flatMap(Uri.fromString(_).toOption).getOrElse {
      val default = "http://localhost:7878"
      logger.warn(s"Unable to fetch radarr baseUrl, using default $default")
      Uri.unsafeFromString(default)
    }
    val apiKey = configReader.getConfigOption(Keys.radarrApiKey).getOrElse(throwError("Unable to find radarr API key"))

    ArrUtils.getToArr(client)(url, apiKey, "qualityprofile").map {
      case Right(res) =>
        logger.info("Successfully connected to Radarr")
        val allQualityProfiles = res.as[List[QualityProfile]].getOrElse(List.empty)
        val chosenQualityProfile = configReader.getConfigOption(Keys.radarrQualityProfile)
        (url, apiKey, getQualityProfileId(allQualityProfiles, chosenQualityProfile))
      case Left(err) =>
        throwError(s"Unable to connect to Radarr at $url, with error $err")
    }
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

  private def getPlexWatchlistUrls: List[Uri] =
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
}
