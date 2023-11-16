package sonarr

import cats.effect.IO
import configuration.Configuration
import http.HttpClient
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import model.Item
import org.http4s.{Method, Uri}
import org.slf4j.LoggerFactory

trait SonarrUtils extends SonarrConversions {

  private val logger = LoggerFactory.getLogger(getClass)

  protected def fetchSeries(client: HttpClient)(apiKey: String, baseUrl: Uri, bypass: Boolean): IO[Set[Item]] =
    for {
      shows <- getToArr(client)(baseUrl, apiKey, "series").map {
        case Right(res) =>
          res.as[List[SonarrSeries]].getOrElse {
            logger.warn("Unable to fetch series from Sonarr - decoding failure. Returning empty list instead")
            List.empty
          }
        case Left(err) =>
          logger.warn(s"Received error while trying to fetch movies from Radarr: $err")
          List.empty
      }
      exclusions <- if (bypass) {
        IO.pure(List.empty)
      } else {
        getToArr(client)(baseUrl, apiKey, "importlistexclusion").map {
          case Right(res) =>
            res.as[List[SonarrSeries]].getOrElse {
              logger.warn("Unable to fetch show exclusions from Sonarr - decoding failure. Returning empty list instead")
              List.empty
            }
          case Left(err) =>
            logger.warn(s"Received error while trying to fetch show exclusions from Sonarr: $err")
            List.empty
        }
      }
    } yield (shows.map(toItem) ++ exclusions.map(toItem)).toSet

  protected def addToSonarr(client: HttpClient)(config: Configuration)(item: Item): IO[Unit] = {

    val addOptions = SonarrAddOptions(config.sonarrSeasonMonitoring)
    val show = SonarrPost(item.title, item.getTvdbId.getOrElse(0L), config.sonarrQualityProfileId, config.radarrRootFolder, addOptions)

    postToArr(client)(config.sonarrBaseUrl, config.sonarrApiKey, "series")(show.asJson).map {
      case Right(_) =>
        logger.info(s"Successfully added show ${item.title} to Sonarr")
      case Left(err) =>
        logger.info(s"Failed to add show ${item.title}: $err")
    }
  }

  private def getToArr(client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String): IO[Either[Throwable, Json]] =
    client.httpRequest(Method.GET, baseUrl / "api" / "v3" / endpoint, Some(apiKey))

  private def postToArr(client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String)(payload: Json): IO[Either[Throwable, Json]] =
    client.httpRequest(Method.POST, baseUrl / "api" / "v3" / endpoint, Some(apiKey), Some(payload))
}
