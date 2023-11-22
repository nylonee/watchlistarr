package http

import cats.effect.IO
import io.circe.Json
import org.http4s.circe._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{Header, Method, Request, Uri}
import org.typelevel.ci.CIString

class HttpClient {

  val client = EmberClientBuilder.default[IO].build

  def httpRequest(method: Method, url: Uri, apiKey: Option[String] = None, payload: Option[Json] = None): IO[Either[Throwable, Json]] = {
    val baseRequest = Request[IO](method = method, uri = url)
      .withHeaders(
        Header.Raw(CIString("Accept"), "application/json"),
        Header.Raw(CIString("Content-Type"), "application/json")
      )
    val requestWithApiKey = apiKey.fold(baseRequest)(key => baseRequest.withHeaders(
      Header.Raw(CIString("X-Api-Key"), key),
      Header.Raw(CIString("X-Plex-Token"), key),
      baseRequest.headers
    ))
    val requestWithPayload = payload.fold(requestWithApiKey)(p => requestWithApiKey.withEntity(p))

    client.use(_.expect[Json](requestWithPayload).attempt)
  }
}
