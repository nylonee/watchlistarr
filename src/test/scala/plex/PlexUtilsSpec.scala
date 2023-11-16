package plex

import cats.effect.IO
import http.HttpClient
import io.circe.parser._
import model.Item
import org.http4s.{Method, Uri}
import org.scalamock.scalatest.MockFactory
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source

class PlexUtilsSpec extends AnyFlatSpec with Matchers with PlexUtils with MockFactory {

  "PlexUtils" should "successfully fetch a watchlist from RSS feeds" in {
    val watchlistStr = Source.fromResource("watchlist.json").getLines().mkString("\n")
    val mockClient = mock[HttpClient]
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:9090"),
      None,
      None
    ).returning(IO.pure(parse(watchlistStr))).once()

    val result = fetchWatchlistFromRss(mockClient)(Uri.unsafeFromString("http://localhost:9090")).unsafeRunSync()

    result.size shouldBe 7
    result.head shouldBe Item("Enola Holmes 2 (2022)", List("imdb://tt14641788", "tmdb://829280", "tvdb://166087"), "movie")
    result.last shouldBe Item("The Wheel of Time (2021)", List("imdb://tt7462410", "tmdb://71914", "tvdb://355730"), "show")
  }

  it should "not fail when the list returned is empty" in {
    val mockClient = mock[HttpClient]
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:9090"),
      None,
      None
    ).returning(IO.pure(parse("{}"))).once()

    val result = fetchWatchlistFromRss(mockClient)(Uri.unsafeFromString("http://localhost:9090")).unsafeRunSync()

    result.size shouldBe 0
  }
}
