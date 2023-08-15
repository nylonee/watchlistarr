
import cats.effect._
import com.comcast.ip4s._
import org.http4s.ember.server._
import org.http4s.implicits._

object Server extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(Routes.helloWorldService.orNotFound)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
