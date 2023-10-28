import cats.effect.IO
import io.circe.Json
import org.http4s.{Header, Method, Request, Uri}
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString

import org.http4s.circe._

object ArrUtils {

  def getToArr(baseUrl: String, apiKey: String, endpoint: String): IO[Either[Throwable, Json]] = {
    EmberClientBuilder.default[IO].build.use { client =>
      val req = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$baseUrl/api/v3/$endpoint")
      ).withHeaders(Header.Raw(CIString("X-Api-Key"), apiKey))

      client.expect[Json](req).attempt
    }
  }

  def postToArr(baseUrl: String, apiKey: String, endpoint: String)(payload: Json): IO[Either[Throwable, Json]] = {
    EmberClientBuilder.default[IO].build.use { client =>
      val req = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString(s"$baseUrl/api/v3/$endpoint")
      ).withHeaders(Header.Raw(CIString("X-Api-Key"), apiKey))
        .withEntity(payload)

      client.expect[Json](req).attempt
    }
  }

}
