import cats.effect._
import configuration.{Configuration, ConfigurationRedactor, ConfigurationUtils, MapAndFileAndSystemPropertyReader}
import http.HttpClient
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.dsl.io._
import org.slf4j.LoggerFactory

object Routes {

  private val logger = LoggerFactory.getLogger(getClass)
  private def routes(configRef: Ref[IO, Configuration], client: HttpClient): HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req @ PATCH -> Root / "config" =>
      logger.info(s"Received request: $req")
      val result = for {
        rawConfiguration <- req.as[Map[String, String]]
        _ = logger.info(s"Parsed into $rawConfiguration")
        newConfiguration <- ConfigurationUtils.create(new MapAndFileAndSystemPropertyReader(rawConfiguration), client)
        _ = logger.info(s"New configuration: $newConfiguration")
        _ <- configRef.set(newConfiguration)
      } yield newConfiguration

      Ok(result.map(ConfigurationRedactor.redactToString))
  }

  def service(configRef: Ref[IO, Configuration], client: HttpClient): HttpRoutes[IO] = routes(configRef, client)
}
