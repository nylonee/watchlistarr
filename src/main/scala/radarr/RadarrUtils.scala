package radarr

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import configuration.Configuration
import http.HttpClient
import io.circe.{Decoder, Json}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import model.Item
import org.http4s.{Method, Uri}
import org.slf4j.LoggerFactory

trait RadarrUtils extends RadarrConversions {

  private val logger = LoggerFactory.getLogger(getClass)

  protected def fetchMovies(client: HttpClient)(apiKey: String, baseUrl: Uri, bypass: Boolean): EitherT[IO, Throwable, Set[Item]] =
    for {
      movies <- getToArr[List[RadarrMovie]](client)(baseUrl, apiKey, "movie")
      exclusions <- if (bypass) {
        EitherT.pure[IO, Throwable](List.empty[RadarrMovieExclusion])
      } else {
        getToArr[List[RadarrMovieExclusion]](client)(baseUrl, apiKey, "exclusions")
      }
    } yield (movies.map(toItem) ++ exclusions.map(toItem)).toSet

  protected def addToRadarr(client: HttpClient)(config: Configuration)(item: Item) = {
    val movie = RadarrPost(item.title, item.getTmdbId.getOrElse(0L), config.radarrQualityProfileId, config.radarrRootFolder)

    postToArr[Unit](client)(config.radarrBaseUrl, config.radarrApiKey, "movie")(movie.asJson)
      .fold(
        err => logger.warn(s"Unable to send ${item.title} to Radarr: $err"),
        result => result
      )
  }

  private def getToArr[T: Decoder](client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String): EitherT[IO, Throwable, T] =
    for {
      response <- EitherT(client.httpRequest(Method.GET, baseUrl / "api" / "v3" / endpoint, Some(apiKey)))
      maybeDecoded <- EitherT.pure[IO, Throwable](response.as[T])
      decoded <- EitherT.fromOption[IO](maybeDecoded.toOption, new Throwable("Unable to decode response from Radarr"))
    } yield decoded

  private def postToArr[T: Decoder](client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String)(payload: Json): EitherT[IO, Throwable, T] =
    for {
      response <- EitherT(client.httpRequest(Method.POST, baseUrl / "api" / "v3" / endpoint, Some(apiKey), Some(payload)))
      maybeDecoded <- EitherT.pure[IO, Throwable](response.as[T])
      decoded <- EitherT.fromOption[IO](maybeDecoded.toOption, new Throwable("Unable to decode response from Radarr"))
    } yield decoded
}
