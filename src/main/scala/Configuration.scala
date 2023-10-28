import cats.effect.IO
import cats.effect.unsafe.implicits.global
import model.QualityProfile
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

class Configuration {

  private val logger = LoggerFactory.getLogger(getClass)

  val refreshInterval: FiniteDuration = getConfigOption("interval.seconds").flatMap(_.toIntOption).getOrElse(60).seconds

  val (sonarrBaseUrl, sonarrApiKey, sonarrQualityProfileId) = getAndTestSonarrUrlAndApiKey.unsafeRunSync()
  val sonarrRootFolder: String = getConfigOption("sonarr.rootFolder").getOrElse("/data/")

  val (radarrBaseUrl, radarrApiKey, radarrQualityProfileId) = getAndTestRadarrUrlAndApiKey.unsafeRunSync()
  val radarrRootFolder: String = getConfigOption("radarr.rootFolder").getOrElse("/data/")

  val plexWatchlistUrls: List[String] = getPlexWatchlistUrls

  private def getAndTestSonarrUrlAndApiKey: IO[(String, String, Int)] = {
    val url = getConfigOption("sonarr.baseUrl").getOrElse("http://localhost:8989")
    val apiKey = getConfig("sonarr.apikey")

    ArrUtils.getToArr(url, apiKey, "qualityprofile").map {
      case Right(res) =>
        logger.info("Successfully connected to Sonarr")
        val allQualityProfiles = res.as[List[QualityProfile]].getOrElse(List.empty)
        val chosenQualityProfile = getConfigOption("sonarr.qualityProfile")
        (url, apiKey, getQualityProfileId(allQualityProfiles, chosenQualityProfile))
      case Left(err) =>
        val message = s"Unable to connect to Sonarr at $url, with error $err"
        logger.error(message)
        throw new IllegalArgumentException(message)
    }
  }

  private def getAndTestRadarrUrlAndApiKey: IO[(String, String, Int)] = {
    val url = getConfigOption("radarr.baseUrl").getOrElse("http://localhost:7878")
    val apiKey = getConfig("radarr.apikey")

    ArrUtils.getToArr(url, apiKey, "qualityprofile").map {
      case Right(res) =>
        logger.info("Successfully connected to Radarr")
        val allQualityProfiles = res.as[List[QualityProfile]].getOrElse(List.empty)
        val chosenQualityProfile = getConfigOption("radarr.qualityProfile")
        (url, apiKey, getQualityProfileId(allQualityProfiles, chosenQualityProfile))
      case Left(err) =>
        val message = s"Unable to connect to Radarr at $url, with error $err"
        logger.error(message)
        throw new IllegalArgumentException(message)
    }
  }

  private def getQualityProfileId(allProfiles: List[QualityProfile], maybeEnvVariable: Option[String]): Int =
    (allProfiles, maybeEnvVariable) match {
      case (Nil, _) =>
        logger.error("Could not find any quality profiles defined, check your Sonarr/Radarr settings")
        throw new IllegalArgumentException("Unable to fetch quality profiles from Sonarr or Radarr")
      case (List(one), _) =>
        logger.debug(s"Only one quality profile defined: ${one.name}")
        one.id
      case (_, None) =>
        logger.debug("Multiple quality profiles found, selecting the first one in the list.")
        allProfiles.head.id
      case (_, Some(profileName)) =>
        allProfiles.find(_.name.toLowerCase == profileName.toLowerCase).map(_.id).getOrElse {
          val message = s"Unable to find quality profile $profileName. Possible values are $allProfiles"
          logger.error(message)
          throw new IllegalArgumentException(message)
        }
    }

  private def getPlexWatchlistUrls: List[String] =
    Set(
      getConfigOption("plex.watchlist1"),
      getConfigOption("plex.watchlist2")
    ).toList.collect {
      case Some(url) => url
    } match {
      case Nil =>
        logger.error("Missing plex watchlist URL")
        throw new IllegalArgumentException("Missing plex watchlist URL")
      case ok => ok
    }

  private def getConfig(key: String): String = getConfigOption(key).getOrElse {
    logger.error(s"Unable to find configuration for $key, have you set the environment variable?")
    throw new IllegalArgumentException(s"Missing argument for $key")
  }

  private def getConfigOption(key: String): Option[String] = Option(System.getProperty(key))
}
