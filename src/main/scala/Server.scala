
import cats.effect._

object Server extends IOApp {

  val config = new Configuration

  def run(args: List[String]): IO[ExitCode] = {

    def periodicTask: IO[Unit] =
      WatchlistSync.run(config) >>
        IO.sleep(config.refreshInterval) >>
        periodicTask

    periodicTask.foreverM.as(ExitCode.Success)
  }
}
