
import cats.effect._

import scala.concurrent.duration._

object Server extends IOApp {

  val config = new Configuration

  def run(args: List[String]): IO[ExitCode] = {

    def periodicTask: IO[Unit] =
      WatchlistSync.run(config) >>
        IO.sleep(2.seconds) >>
        periodicTask

    periodicTask.foreverM.as(ExitCode.Success)
  }
}
