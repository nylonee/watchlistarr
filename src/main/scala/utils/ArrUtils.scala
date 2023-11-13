package utils

import cats.effect.IO
import io.circe.Json
import org.http4s.{Method, Uri}

object ArrUtils {

  def getToArr(client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String): IO[Either[Throwable, Json]] =
    client.httpRequest(Method.GET, baseUrl / "api" / "v3" / endpoint, Some(apiKey))

  def postToArr(client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String)(payload: Json): IO[Either[Throwable, Json]] =
    client.httpRequest(Method.POST, baseUrl / "api" / "v3" / endpoint, Some(apiKey), Some(payload))

}
