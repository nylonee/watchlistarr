
import cats.effect._
import cats.implicits.catsSyntaxTuple4Parallel
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import configuration.{Configuration, ConfigurationUtils, SystemPropertyReader}
import http.HttpClient
import model.{Cache, CaffeineCache, Item, ThirdPartyType, WatchlistarrCache}
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
    val cache = makeCache()

    for {
      memoizedConfigIo <- ConfigurationUtils.create(configReader, httpClient).memoize
      result <- (
        watchlistSync(memoizedConfigIo, httpClient),
        pingTokenSync(memoizedConfigIo, httpClient),
        plexTokenSync(memoizedConfigIo, httpClient, cache),
        plexTokenDeleteSync(memoizedConfigIo, httpClient)
      ).parTupled.as(ExitCode.Success)
    } yield result
  }

  private def makeCache(): WatchlistarrCache = {
    val internalCache =
      Scaffeine()
        .recordStats()
        .expireAfterWrite(1.hour)
        .maximumSize(500)
        .build[ThirdPartyType, Seq[Item]]()

    CaffeineCache(internalCache)
  }

  private def watchlistSync(configIO: IO[Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- configIO
      _ <- WatchlistSync.run(config, httpClient)
      _ <- IO.sleep(config.refreshInterval)
      _ <- watchlistSync(configIO, httpClient)
    } yield ()

  private def pingTokenSync(configIO: IO[Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- configIO
      _ <- PingTokenSync.run(config, httpClient)
      _ <- IO.sleep(24.hours)
      _ <- pingTokenSync(configIO, httpClient)
    } yield ()

  private def plexTokenSync(configIO: IO[Configuration], httpClient: HttpClient, cache: WatchlistarrCache): IO[Unit] =
    for {
      config <- configIO
      _ <- PlexTokenSync.run(config, httpClient, cache)
    } yield ()

  private def plexTokenDeleteSync(configIO: IO[Configuration], httpClient: HttpClient): IO[Unit] =
    for {
      config <- configIO
      _ <- PlexTokenDeleteSync.run(config, httpClient)
      _ <- IO.sleep(config.deleteConfiguration.deleteInterval)
      _ <- plexTokenDeleteSync(configIO, httpClient)
    } yield ()
}
