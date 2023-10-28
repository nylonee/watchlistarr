import cats.effect.IO
import cats.effect.unsafe.implicits.global

import scala.concurrent.duration._

class Configuration {

  val refreshInterval: FiniteDuration = getConfigOption("interval.seconds").flatMap(_.toIntOption).getOrElse(60).seconds

  val (sonarrBaseUrl, sonarrApiKey) = getAndTestSonarrUrlAndApiKey.unsafeRunSync()
  // TODO: Grab the quality profile ID automatically if it's not set
  val sonarrQualityProfileId: Option[Int] = getConfigOption("sonarr.qualityProfile").flatMap(_.toIntOption)
  val sonarrRootFolder: String = getConfigOption("sonarr.rootFolder").getOrElse("/data/")

  val (radarrBaseUrl, radarrApiKey) = getAndTestRadarrUrlAndApiKey.unsafeRunSync()
  val radarrQualityProfileId: Option[Int] = getConfigOption("radarr.qualityProfile").flatMap(_.toIntOption)
  val radarrRootFolder: String = getConfigOption("radarr.rootFolder").getOrElse("/data/")

  val plexWatchlistUrls: List[String] = getPlexWatchlistUrls

  private def getAndTestSonarrUrlAndApiKey: IO[(String, String)] = {
    val url = getConfigOption("sonarr.baseUrl").getOrElse("http://localhost:8989")
    val apiKey = getConfig("sonarr.apikey")

    ArrUtils.getToArr(url, apiKey, "health").map {
      case Right(_) =>
        println("Successfully tested the connection to Sonarr!")
        (url, apiKey)
      case Left(err) =>
        throw new IllegalArgumentException(s"Unable to connect to Sonarr at $url, with error $err")
    }
  }

  private def getAndTestRadarrUrlAndApiKey: IO[(String, String)] = {
    val url = getConfigOption("radarr.baseUrl").getOrElse("http://localhost:7878")
    val apiKey = getConfig("radarr.apikey")

    ArrUtils.getToArr(url, apiKey, "health").map {
      case Right(_) =>
        println("Successfully tested the connection to Radarr!")
        (url, apiKey)
      case Left(err) =>
        throw new IllegalArgumentException(s"Unable to connect to Radarr at $url, with error $err")
    }
  }

  private def getPlexWatchlistUrls: List[String] =
    Set(
      getConfigOption("plex.watchlist1"),
      getConfigOption("plex.watchlist2")
    ).toList.collect {
      case Some(url) => url
    } match {
      case Nil => throw new IllegalArgumentException("Missing plex watchlist URL")
      case ok => ok
    }

  private def getConfig(key: String): String = getConfigOption(key).getOrElse {
    println(s"Unable to find configuration for $key, have you set the environment variable?")
    throw new IllegalArgumentException(s"Missing argument for $key")
  }

  private def getConfigOption(key: String): Option[String] = Option(System.getProperty(key))
}
