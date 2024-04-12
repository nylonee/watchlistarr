package http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.http4s.circe._
import org.http4s.client.middleware.FollowRedirect
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{Header, Method, Request, Uri}
import org.typelevel.ci.CIString
import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}

import scala.concurrent.duration._

class HttpClient {

  private val client = EmberClientBuilder
    .default[IO]
    .build
    .map(FollowRedirect(5))

  private val cacheTtl = 5.seconds

  private val cache: AsyncLoadingCache[(Method, Uri, Option[String], Option[Json]), Either[Throwable, Json]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(cacheTtl)
      .maximumSize(1000)
      .buildAsyncFuture { case (method, url, apiKey, payload) =>
        makeHttpRequest(method, url, apiKey, payload).unsafeToFuture()
      }

  def httpRequest(
                   method: Method,
                   url: Uri,
                   apiKey: Option[String] = None,
                   payload: Option[Json] = None
                 ): IO[Either[Throwable, Json]] = IO.fromFuture(IO(cache.get(method, url, apiKey, payload)))

  private def makeHttpRequest(
                               method: Method,
                               url: Uri,
                               apiKey: Option[String] = None,
                               payload: Option[Json] = None
                             ): IO[Either[Throwable, Json]] = {
    val host = s"${url.host.getOrElse(Uri.Host.unsafeFromString("127.0.0.1")).value}"

    val baseRequest = Request[IO](method = method, uri = url)
      .withHeaders(
        Header.Raw(CIString("Accept"), "application/json"),
        Header.Raw(CIString("Content-Type"), "application/json"),
        Header.Raw(CIString("Host"), host)
      )
    val requestWithApiKey = apiKey.fold(baseRequest)(key =>
      baseRequest.withHeaders(
        Header.Raw(CIString("X-Api-Key"), key),
        Header.Raw(CIString("X-Plex-Token"), key),
        baseRequest.headers
      )
    )
    val requestWithPayload = payload.fold(requestWithApiKey)(p => requestWithApiKey.withEntity(p))

    client.use(_.expect[Json](requestWithPayload).attempt)
  }
}
