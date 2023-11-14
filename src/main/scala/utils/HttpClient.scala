package utils

import cats.effect.{IO, Resource}
import io.circe.Json
import org.http4s.{Header, Method, Request, Uri}
import org.typelevel.ci.CIString
import org.http4s.circe._
import org.http4s.client.Client
import org.slf4j.LoggerFactory
import cats.effect.std.Semaphore

class HttpClient(http4sClient: Resource[IO, Client[IO]], semaphore: Semaphore[IO]) {
  private val logger = LoggerFactory.getLogger(getClass)

  def httpRequest(method: Method, url: Uri, apiKey: Option[String] = None, payload: Option[Json] = None): IO[Either[Throwable, Json]] = {
    val baseRequest = Request[IO](method = method, uri = url).withHeaders(Header.Raw(CIString("Accept"), "application/json"))
    val requestWithApiKey = apiKey.fold(baseRequest)(key => baseRequest.withHeaders(Header.Raw(CIString("X-Api-Key"), key)))
    val requestWithPayload = payload.fold(requestWithApiKey)(p => requestWithApiKey.withEntity(p))

    logger.info(s"Thread: ${Thread.currentThread()}")

    semaphore.permit.use { _ =>
      http4sClient.use(_.expect[Json](requestWithPayload).attempt)
    }
  }
}
