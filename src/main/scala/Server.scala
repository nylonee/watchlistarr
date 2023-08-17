
import cats.effect._
import cats.implicits._
import com.comcast.ip4s._
import org.http4s.ember.server._
import org.http4s.implicits._

import scala.concurrent.duration._

import org.http4s.server.middleware.CORS

object Server extends IOApp {

  private val serviceWithCORS = CORS(Routes.service.orNotFound)

  def run(args: List[String]): IO[ExitCode] = {
    val server = EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(serviceWithCORS)
      .build
      .use(_ => IO.never)

    def periodicTask: IO[Unit] =
      IO.sleep(5.minutes) >>
        IO(println("Server is still alive :) ")) >>
        periodicTask

    (server, periodicTask).parTupled.as(ExitCode.Success)
  }
}
