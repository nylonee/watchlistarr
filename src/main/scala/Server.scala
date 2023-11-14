
import cats.effect._
import configuration.{Configuration, ConfigurationUtils, SystemPropertyReader}
import org.slf4j.LoggerFactory
import utils.HttpClient

import java.nio.channels.ClosedChannelException

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
      result <- periodicTask(memoizedConfigIo, httpClient).foreverM.as(ExitCode.Success)
    } yield result
  }

  private def periodicTask(configIO: IO[Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- configIO
      _ <- WatchlistSync.run(config, httpClient)
      _ <- IO.sleep(config.refreshInterval)
      _ <- periodicTask(configIO, httpClient)
    } yield ()
}
