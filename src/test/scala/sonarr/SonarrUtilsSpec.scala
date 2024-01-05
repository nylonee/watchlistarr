package sonarr

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

class SonarrUtilsSpec extends AnyFlatSpec with Matchers with SonarrUtils with MockFactory {

  "SonarrUtils" should "successfully fetch a list of series and exclusions from Sonarr" in {
    val seriesJsonStr = Source.fromResource("sonarr.json").getLines().mkString("\n")
    val exclusionsJsonStr = Source.fromResource("importlistexclusion.json").getLines().mkString("\n")
    val mockClient = mock[HttpClient]
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:8989").withPath(Uri.Path.unsafeFromString("/api/v3/series")),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse(seriesJsonStr))).once()
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:8989").withPath(Uri.Path.unsafeFromString("/api/v3/importlistexclusion")),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse(exclusionsJsonStr))).once()

    val eitherResult = fetchSeries(mockClient)("sonarr-api-key", Uri.unsafeFromString("http://localhost:8989"), false).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty)
    result.size shouldBe 76
    result.find(_.title == "The Secret Life of 4, 5 and 6 Year Olds") shouldBe Some(Item("The Secret Life of 4, 5 and 6 Year Olds", List("tt6620876", "tvdb://304746", "sonarr://76"), "show", Some(true)))
    result.find(_.title == "Maternal") shouldBe Some(Item("Maternal", List("tt21636214", "tvdb://424724", "sonarr://70"), "show", Some(true)))
    // Check that exclusions are added
    result.find(_.title == "The Test") shouldBe Some(Item("The Test", List("tvdb://372848", "sonarr://1"), "show", None))
  }

  it should "not fail when the list returned is empty" in {
    val mockClient = mock[HttpClient]
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:8989").withPath(Uri.Path.unsafeFromString("/api/v3/series")),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse("[]"))).once()
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:8989").withPath(Uri.Path.unsafeFromString("/api/v3/importlistexclusion")),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse("[]"))).once()

    val eitherResult = fetchSeries(mockClient)("sonarr-api-key", Uri.unsafeFromString("http://localhost:8989"), false).value.unsafeRunSync()

    eitherResult shouldBe Right(Set.empty)
  }
}
