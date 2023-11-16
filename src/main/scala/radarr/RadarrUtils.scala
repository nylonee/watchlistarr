package radarr

import cats.effect.IO
import configuration.Configuration
import http.HttpClient
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import model.Item
import org.http4s.{Method, Uri}
import org.slf4j.LoggerFactory

trait RadarrUtils extends RadarrConversions {

  private val logger = LoggerFactory.getLogger(getClass)

  protected def fetchMovies(client: HttpClient)(apiKey: String, baseUrl: Uri, bypass: Boolean): IO[Set[Item]] =
    for {
      movies <- getToArr(client)(baseUrl, apiKey, "movie").map {
        case Right(res) =>
          res.as[List[RadarrMovie]].getOrElse {
            logger.warn("Unable to fetch movies from Radarr - decoding failure. Returning empty list instead")
            List.empty
          }
        case Left(err) =>
          logger.warn(s"Received error while trying to fetch movies from Radarr: $err")
          List.empty
      }
      exclusions <- if (bypass) {
        IO.pure(List.empty)
      } else {
        getToArr(client)(baseUrl, apiKey, "exclusions").map {
          case Right(res) =>
            res.as[List[RadarrMovieExclusion]].getOrElse {
              logger.warn("Unable to fetch movie exclusions from Radarr - decoding failure. Returning empty list instead")
              List.empty
            }
          case Left(err) =>
            logger.warn(s"Received error while trying to fetch movie exclusions from Radarr: $err")
            List.empty
        }
      }
    } yield (movies.map(toItem) ++ exclusions.map(toItem)).toSet

  protected def addToRadarr(client: HttpClient)(config: Configuration)(item: Item): IO[Unit] = {

    val movie = RadarrPost(item.title, item.getTmdbId.getOrElse(0L), config.radarrQualityProfileId, config.radarrRootFolder)

    postToArr(client)(config.radarrBaseUrl, config.radarrApiKey, "movie")(movie.asJson).map {
      case Right(_) =>
        logger.info(s"Successfully added movie ${item.title} to Radarr")
      case Left(err) =>
        logger.error(s"Failed to add movie ${item.title}: $err")
    }
  }

  private def getToArr(client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String): IO[Either[Throwable, Json]] =
    client.httpRequest(Method.GET, baseUrl / "api" / "v3" / endpoint, Some(apiKey))

  private def postToArr(client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String)(payload: Json): IO[Either[Throwable, Json]] =
    client.httpRequest(Method.POST, baseUrl / "api" / "v3" / endpoint, Some(apiKey), Some(payload))
}
