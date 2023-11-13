
import cats.effect._
import configuration.{ConfigurationUtils, SystemPropertyReader}
import utils.HttpClient

object Server extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val configReader = SystemPropertyReader
    val httpClient = new HttpClient()
    val config = ConfigurationUtils.create(configReader, httpClient)

    def periodicTask: IO[Unit] =
      config.flatMap(c => WatchlistSync.run(c, httpClient)) >>
        config.flatMap(c => IO.sleep(c.refreshInterval)) >>
        periodicTask

    periodicTask.foreverM.as(ExitCode.Success)
  }
}
