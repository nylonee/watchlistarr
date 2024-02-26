
import cats.effect._
import cats.implicits.catsSyntaxTuple4Parallel
import com.comcast.ip4s.IpLiteralSyntax
import configuration.{Configuration, ConfigurationUtils, FileAndSystemPropertyReader}
import http.HttpClient
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.CORS
import org.slf4j.LoggerFactory

import java.nio.channels.ClosedChannelException
import scala.concurrent.duration.DurationInt

object Server extends IOApp {

  private val logger = LoggerFactory.getLogger(getClass)

  override protected def reportFailure(err: Throwable): IO[Unit] = err match {
    case _: ClosedChannelException => IO.pure(logger.debug("Suppressing ClosedChannelException error", err))
    case _ => IO.pure(logger.error("Failure caught and handled by IOApp", err))
  }

  def run(args: List[String]): IO[ExitCode] = {
    val configReader = FileAndSystemPropertyReader
    val httpClient = new HttpClient

    for {
      initialConfig <- ConfigurationUtils.create(configReader, httpClient)
      configRef <- Ref.of[IO, Configuration](initialConfig)
      result <- (
        server(configRef, httpClient),
        pingTokenSync(configRef, httpClient),
        plexTokenSync(configRef, httpClient),
        plexTokenDeleteSync(configRef, httpClient)
      ).parTupled.as(ExitCode.Success)
    } yield result
  }

  private def server(configRef: Ref[IO, Configuration], client: HttpClient) = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"3434")
    .withHttpApp(CORS(Routes.service(configRef, client).orNotFound))
    .build
    .use(_ => IO.never)

  private def fetchLatestConfig(configRef: Ref[IO, Configuration]): IO[Configuration] =
    configRef.get

  private def pingTokenSync(configRef: Ref[IO, Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- fetchLatestConfig(configRef)
      _ <- PingTokenSync.run(config, httpClient)
      _ <- IO.sleep(24.hours)
      _ <- pingTokenSync(configRef, httpClient)
    } yield ()

  private def plexTokenSync(configRef: Ref[IO, Configuration], httpClient: HttpClient, firstRun: Boolean = true): IO[Unit] =
    for {
      config <- fetchLatestConfig(configRef)
      _ <- PlexTokenSync.run(config, httpClient, firstRun)
      _ <- IO.sleep(config.refreshInterval)
      _ <- plexTokenSync(configRef, httpClient, firstRun = false)
    } yield ()

  private def plexTokenDeleteSync(configRef: Ref[IO, Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- fetchLatestConfig(configRef)
      _ <- PlexTokenDeleteSync.run(config, httpClient)
      _ <- IO.sleep(config.deleteConfiguration.deleteInterval)
      _ <- plexTokenDeleteSync(configRef, httpClient)
    } yield ()
}
