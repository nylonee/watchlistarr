
import cats.effect._
import cats.implicits.catsSyntaxTuple2Parallel
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
      result <- (watchlistSync(memoizedConfigIo, httpClient), plexTokenSync(memoizedConfigIo, httpClient)).parTupled.as(ExitCode.Success)
    } yield result
  }

  private def watchlistSync(configIO: IO[Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- configIO
      _ <- WatchlistSync.run(config, httpClient)
      _ <- IO.sleep(config.refreshInterval)
      _ <- watchlistSync(configIO, httpClient)
    } yield ()

  private def plexTokenSync(configIO: IO[Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- configIO
      _ <- PlexTokenSync.run(config, httpClient)
      _ <- IO.sleep(24.seconds)
      _ <- plexTokenSync(configIO, httpClient)
    } yield ()
}
