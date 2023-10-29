package utils

import cats.effect.IO
import io.circe.Json
import org.http4s.{Method, Uri}

object ArrUtils {

  def getToArr(client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String): IO[Either[Throwable, Json]] = {
    val path = Uri.Path.unsafeFromString(s"/api/v3/$endpoint")
    client.httpRequest(Method.GET, baseUrl.withPath(path), Some(apiKey))
  }

  def postToArr(client: HttpClient)(baseUrl: Uri, apiKey: String, endpoint: String)(payload: Json): IO[Either[Throwable, Json]] = {
    val path = Uri.Path.unsafeFromString(s"/api/v3/$endpoint")
    client.httpRequest(Method.POST, baseUrl.withPath(path), Some(apiKey), Some(payload))
  }

}
