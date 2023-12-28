package radarr

import cats.effect.IO
import http.HttpClient
import cats.effect.unsafe.implicits.global
import io.circe.parser._
import model.Item
import org.http4s.{Method, Uri}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source

class RadarrUtilsSpec extends AnyFlatSpec with Matchers with RadarrUtils with MockFactory {

  "RadarrUtils" should "successfully fetch a list of movies and exclusions from Radarr" in {
    val movieJsonStr = Source.fromResource("radarr.json").getLines().mkString("\n")
    val exclusionsJsonStr = Source.fromResource("exclusions.json").getLines().mkString("\n")
    val mockClient = mock[HttpClient]
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:7878").withPath(Uri.Path.unsafeFromString("/api/v3/movie")),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse(movieJsonStr))).once()
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:7878").withPath(Uri.Path.unsafeFromString("/api/v3/exclusions")),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse(exclusionsJsonStr))).once()

    val eitherResult = fetchMovies(mockClient)("radarr-api-key", Uri.unsafeFromString("http://localhost:7878"), false).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty)
    result.size shouldBe 157
    result.head shouldBe Item("Moonlight", List("tt4975722", "tmdb://376867", "radarr://32"), "movie")
    result.last shouldBe Item("Oculus", List("tt2388715", "tmdb://157547", "radarr://21"), "movie")
    // Check that exclusions are added
    result.find(_.title == "Monty Python and the Holy Grail") shouldBe Some(Item("Monty Python and the Holy Grail", List("tmdb://762", "radarr://2"), "movie"))
  }

  it should "not fail when the list returned is empty" in {
    val mockClient = mock[HttpClient]
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:7878").withPath(Uri.Path.unsafeFromString("/api/v3/movie")),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse("[]"))).once()
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:7878").withPath(Uri.Path.unsafeFromString("/api/v3/exclusions")),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse("[]"))).once()

    val eitherResult = fetchMovies(mockClient)("radarr-api-key", Uri.unsafeFromString("http://localhost:7878"), false).value.unsafeRunSync()

    eitherResult shouldBe Right(Set.empty)
  }
}
