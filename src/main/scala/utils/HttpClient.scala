package utils

import cats.effect.IO
import io.circe.Json
import org.http4s.{Header, Method, Request, Uri}
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString
import org.http4s.circe._

import scala.concurrent.duration.DurationInt

class HttpClient {
  private val clientResource = EmberClientBuilder
    .default[IO]
    .build

  def httpRequest(method: Method, url: Uri, apiKey: Option[String] = None, payload: Option[Json] = None): IO[Either[Throwable, Json]] = {
    val baseRequest = Request[IO](method = method, uri = url).withHeaders(Header.Raw(CIString("Accept"), "application/json"))
    val requestWithApiKey = apiKey.fold(baseRequest)(key => baseRequest.withHeaders(Header.Raw(CIString("X-Api-Key"), key)))
    val requestWithPayload = payload.fold(requestWithApiKey)(p => requestWithApiKey.withEntity(p))

    clientResource.use(_.expect[Json](requestWithPayload).attempt)
  }
}
