
import cats.effect._
import configuration.{Configuration, SystemPropertyReader}
import utils.HttpClient

object Server extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val configReader = SystemPropertyReader
    val httpClient = new HttpClient()
    val config = new Configuration(configReader, httpClient)(runtime)

    def periodicTask: IO[Unit] =
      WatchlistSync.run(config) >>
        IO.sleep(config.refreshInterval) >>
        periodicTask

    periodicTask.foreverM.as(ExitCode.Success)
  }
}
