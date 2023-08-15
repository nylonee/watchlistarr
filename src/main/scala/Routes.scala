import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

object Routes {
  val helloWorldService: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name!")
  }
}
