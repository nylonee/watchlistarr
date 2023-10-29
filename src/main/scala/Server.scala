
import cats.effect._
import configuration.{Configuration, SystemPropertyReader}
import utils.HttpClient

object Server extends IOApp {

  val configReader = SystemPropertyReader
  val httpClient = new HttpClient()
  val config = new Configuration(configReader, httpClient)

  def run(args: List[String]): IO[ExitCode] = {

    def periodicTask: IO[Unit] =
      WatchlistSync.run(config) >>
        IO.sleep(config.refreshInterval) >>
        periodicTask

    periodicTask.foreverM.as(ExitCode.Success)
  }
}
