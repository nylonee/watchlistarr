import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

object Routes {
  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name!")
  }

  val service: HttpRoutes[IO] = routes
}
