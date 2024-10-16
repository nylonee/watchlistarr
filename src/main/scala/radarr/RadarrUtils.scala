package radarr

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import configuration.RadarrConfiguration
import http.HttpClient
import io.circe.{Decoder, Json}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import model.Item
import org.http4s.{MalformedMessageBodyFailure, Method, Uri}
import org.slf4j.LoggerFactory

trait RadarrUtils extends RadarrConversions {

  private val logger = LoggerFactory.getLogger(getClass)

  protected def fetchMovies(
      client: HttpClient
  )(apiKey: String, baseUrl: Uri, bypass: Boolean): EitherT[IO, Throwable, Set[Item]] =
    for {
      movies <- getToArr[List[RadarrMovie]](client)(baseUrl, apiKey, "movie")
      exclusions <-
        if (bypass) {
          EitherT.pure[IO, Throwable](List.empty[RadarrMovieExclusion])
        } else {
          getToArr[List[RadarrMovieExclusion]](client)(baseUrl, apiKey, "exclusions")
        }
    } yield (movies.map(toItem) ++ exclusions.map(toItem)).toSet

  protected def addToRadarr(client: HttpClient)(config: RadarrConfiguration)(item: Item): IO[Unit] = {
    val movie = RadarrPost(
      item.title,
      item.getTmdbId.getOrElse(0L),
      config.radarrQualityProfileId,
      config.radarrRootFolder,
      config.radarrMinimumAvailability,
      tags = config.radarrTagIds.toList
    )

    val result = postToArr[Unit](client)(config.radarrBaseUrl, config.radarrApiKey, "movie")(movie.asJson)
      .fold(
        err => logger.debug(s"Received warning for sending ${item.title} to Radarr: $err"),
        r => r
      )

    result.map { r =>
      logger.info(s"Sent ${item.title} to Radarr")
      r
    }
  }

  protected def deleteFromRadarr(client: HttpClient, config: RadarrConfiguration, deleteFiles: Boolean)(
      item: Item
  ): EitherT[IO, Throwable, Unit] = {
    val movieId = item.getRadarrId.getOrElse {
      logger.warn(s"Unable to extract Radarr ID from movie to delete: $item")
      0L
    }

    deleteToArr(client)(config.radarrBaseUrl, config.radarrApiKey, movieId, deleteFiles)
      .map { r =>
        logger.info(s"Deleted ${item.title} from Radarr")
        r
      }
  }

  private def getToArr[T: Decoder](
      client: HttpClient
  )(baseUrl: Uri, apiKey: String, endpoint: String): EitherT[IO, Throwable, T] =
    for {
      response     <- EitherT(client.httpRequest(Method.GET, baseUrl / "api" / "v3" / endpoint, Some(apiKey)))
      maybeDecoded <- EitherT.pure[IO, Throwable](response.as[T])
      decoded <- EitherT.fromOption[IO](maybeDecoded.toOption, new Throwable("Unable to decode response from Radarr"))
    } yield decoded

  private def postToArr[T: Decoder](
      client: HttpClient
  )(baseUrl: Uri, apiKey: String, endpoint: String)(payload: Json): EitherT[IO, Throwable, T] =
    for {
      response <- EitherT(
        client.httpRequest(Method.POST, baseUrl / "api" / "v3" / endpoint, Some(apiKey), Some(payload))
      )
      maybeDecoded <- EitherT.pure[IO, Throwable](response.as[T])
      decoded <- EitherT.fromOption[IO](maybeDecoded.toOption, new Throwable("Unable to decode response from Radarr"))
    } yield decoded

  private def deleteToArr(
      client: HttpClient
  )(baseUrl: Uri, apiKey: String, id: Long, deleteFiles: Boolean): EitherT[IO, Throwable, Unit] = {
    val urlWithQueryParams = (baseUrl / "api" / "v3" / "movie" / id)
      .withQueryParam("deleteFiles", deleteFiles)
      .withQueryParam("addImportExclusion", false)

    EitherT(client.httpRequest(Method.DELETE, urlWithQueryParams, Some(apiKey)))
      .recover { case _: MalformedMessageBodyFailure => Json.Null }
      .map(_ => ())
  }
}
