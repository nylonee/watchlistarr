
import cats.effect._
import configuration.{Configuration, ConfigurationUtils, SystemPropertyReader}
import utils.HttpClient

object Server extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val configReader = SystemPropertyReader
    val httpClient = new HttpClient()
    val configIO: IO[Configuration] = ConfigurationUtils.create(configReader, httpClient)
    val memoizedConfigIO: IO[IO[Configuration]] = configIO.memoize

    def periodicTask(configIO: IO[Configuration]): IO[Unit] =
      for {
        config <- configIO
        _ <- WatchlistSync.run(config, httpClient)
        _ <- IO.sleep(config.refreshInterval)
        _ <- periodicTask(configIO)
      } yield ()

    for {
      getConfig <- memoizedConfigIO
      result <- periodicTask(getConfig).foreverM.as(ExitCode.Success)
    } yield result
  }
}
