
import cats.effect._
import cats.implicits.catsSyntaxTuple3Parallel
import configuration.{Configuration, ConfigurationUtils, SystemPropertyReader}
import http.HttpClient
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
    val configReader = SystemPropertyReader
    val httpClient = new HttpClient

    for {
      memoizedConfigIo <- ConfigurationUtils.create(configReader, httpClient).memoize
      result <- (
        pingTokenSync(memoizedConfigIo, httpClient),
        plexTokenSync(memoizedConfigIo, httpClient),
        plexTokenDeleteSync(memoizedConfigIo, httpClient)
      ).parTupled.as(ExitCode.Success)
    } yield result
  }

  private def pingTokenSync(configIO: IO[Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- configIO
      _ <- PingTokenSync.run(config, httpClient)
      _ <- IO.sleep(24.hours)
      _ <- pingTokenSync(configIO, httpClient)
    } yield ()

  private def plexTokenSync(configIO: IO[Configuration], httpClient: HttpClient, firstRun: Boolean = true): IO[Unit] =
    for {
      config <- configIO
      _ <- PlexTokenSync.run(config, httpClient, firstRun)
      _ <- IO.sleep(config.refreshInterval)
      _ <- plexTokenSync(configIO, httpClient, firstRun = false)
    } yield ()

  private def plexTokenDeleteSync(configIO: IO[Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- configIO
      _ <- PlexTokenDeleteSync.run(config, httpClient)
      _ <- IO.sleep(config.deleteConfiguration.deleteInterval)
      _ <- plexTokenDeleteSync(configIO, httpClient)
    } yield ()
}
