
import cats.effect._
import cats.effect.std.Semaphore
import configuration.{Configuration, ConfigurationUtils, SystemPropertyReader}
import org.http4s.ember.client.EmberClientBuilder
import utils.HttpClient

object Server extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val maxConcurrentOutgoingRequests = 1

    for {
      semaphore <- Semaphore[IO](maxConcurrentOutgoingRequests)
      configReader = SystemPropertyReader
      clientResource = EmberClientBuilder.default[IO].build
      httpClient = new HttpClient(clientResource, semaphore)
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
