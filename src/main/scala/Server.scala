import cats.effect._
import cats.implicits.catsSyntaxTuple4Parallel
import configuration.{Configuration, ConfigurationUtils, FileAndSystemPropertyReader}
import http.HttpClient
import org.slf4j.LoggerFactory

import java.nio.channels.ClosedChannelException
import scala.concurrent.duration.DurationInt

object Server extends IOApp {

  private val logger = LoggerFactory.getLogger(getClass)

  override protected def reportFailure(err: Throwable): IO[Unit] = err match {
    case _: ClosedChannelException => IO.pure(logger.debug("Suppressing ClosedChannelException error", err))
    case _                         => IO.pure(logger.error("Failure caught and handled by IOApp", err))
  }

  def run(args: List[String]): IO[ExitCode] = {
    val configReader = FileAndSystemPropertyReader
    val httpClient   = new HttpClient

    for {
      initialConfig <- ConfigurationUtils.create(configReader, httpClient)
      configRef     <- Ref.of[IO, Configuration](initialConfig)
      result <- (
        pingTokenSync(configRef, httpClient),
        plexRssSync(configRef, httpClient),
        plexTokenDeleteSync(configRef, httpClient),
        plexFullSync(configRef, httpClient)
      ).parTupled.as(ExitCode.Success)
    } yield result
  }

  private def fetchLatestConfig(configRef: Ref[IO, Configuration]): IO[Configuration] =
    configRef.get

  private def pingTokenSync(configRef: Ref[IO, Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- fetchLatestConfig(configRef)
      _      <- PingTokenSync.run(config, httpClient)
      _      <- IO.sleep(24.hours)
      _      <- pingTokenSync(configRef, httpClient)
    } yield ()

  private def plexRssSync(
      configRef: Ref[IO, Configuration],
      httpClient: HttpClient
  ): IO[Unit] =
    for {
      config <- fetchLatestConfig(configRef)
      _      <- PlexTokenSync.run(config, httpClient, runFullSync = false)
      _      <- IO.sleep(config.refreshInterval)
      _      <- plexRssSync(configRef, httpClient)
    } yield ()

  private def plexFullSync(
                             configRef: Ref[IO, Configuration],
                             httpClient: HttpClient
                           ): IO[Unit] =
    for {
      config <- fetchLatestConfig(configRef)
      _      <- PlexTokenSync.run(config, httpClient, runFullSync = true)
      _      <- IO.sleep(19.minutes)
      _      <- plexFullSync(configRef, httpClient)
    } yield ()

  private def plexTokenDeleteSync(configRef: Ref[IO, Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- fetchLatestConfig(configRef)
      _      <- PlexTokenDeleteSync.run(config, httpClient)
      _      <- IO.sleep(config.deleteConfiguration.deleteInterval)
      _      <- plexTokenDeleteSync(configRef, httpClient)
    } yield ()
}
