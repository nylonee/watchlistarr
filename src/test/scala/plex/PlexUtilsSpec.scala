package plex

import cats.effect.IO
import http.HttpClient
import io.circe.parser._
import model.{GraphQLQuery, Item}
import org.http4s.{Method, Uri}
import org.scalamock.scalatest.MockFactory
import cats.effect.unsafe.implicits.global
import configuration.Configuration
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.generic.extras.auto._
import io.circe.syntax.EncoderOps

import scala.concurrent.duration.DurationInt
import scala.io.Source

class PlexUtilsSpec extends AnyFlatSpec with Matchers with PlexUtils with MockFactory {

  "PlexUtils" should "successfully fetch a watchlist from RSS feeds" in {
    val mockClient = mock[HttpClient]
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:9090"),
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("watchlist.json").getLines().mkString("\n")))).once()

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

  it should "successfully ping the Plex server" in {
    val mockClient = mock[HttpClient]
    val config = createConfiguration(Some("test-token"))
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://plex.tv/api/v2/ping?X-Plex-Token=test-token&X-Plex-Client-Identifier=watchlistarr"),
      None,
      None
    ).returning(IO.pure(parse("{}"))).once()

    val result: Unit = ping(mockClient)(config).unsafeRunSync()

    result shouldBe()
  }

  it should "successfully fetch the watchlist using the plex token" in {
    val mockClient = mock[HttpClient]
    val config = createConfiguration(Some("test-token"))
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://metadata.provider.plex.tv/library/sections/watchlist/all?X-Plex-Token=test-token"),
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("self-watchlist-from-token.json").getLines().mkString("\n")))).once()
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://discover.provider.plex.tv/library/metadata/5df46a38237002001dce338d?X-Plex-Token=test-token"),
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("single-item-plex-metadata.json").getLines().mkString("\n")))).once()
    (mockClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://discover.provider.plex.tv/library/metadata/617d3ab142705b2183b1b20b?X-Plex-Token=test-token"),
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("single-item-plex-metadata.json").getLines().mkString("\n")))).once()

    val eitherResult = getSelfWatchlist(config, mockClient).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty[Item])
    result.size shouldBe 2
    result.head shouldBe Item("The Test", List("imdb://tt11347692", "tmdb://95837", "tvdb://372848"), "show")
  }

  it should "successfully fetch friends from Plex" in {
    val mockClient = mock[HttpClient]
    val config = createConfiguration(Some("test-token"))
    val query = GraphQLQuery(
      """query GetAllFriends {
        |        allFriendsV2 {
        |          user {
        |            id
        |            username
        |          }
        |        }
        |      }""".stripMargin)
    (mockClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://community.plex.tv/api"),
      Some("test-token"),
      Some(query.asJson)
    ).returning(IO.pure(parse(Source.fromResource("plex-get-all-friends.json").getLines().mkString("\n")))).once()

    val eitherResult = getFriends(config, mockClient).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty[User])
    result.size shouldBe 2
    result.head shouldBe User("ecdb6as0230e2115", "friend-1")
    result.last shouldBe User("a31281fd8s413643", "friend-2")
  }

  it should "successfully fetch a watchlist from a friend on Plex" in {
    val mockClient = mock[HttpClient]
    val config = createConfiguration(Some("test-token"))
    (mockClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://community.plex.tv/api"),
      Some("test-token"),
      *
    ).returning(IO.pure(parse(Source.fromResource("plex-get-watchlist-from-friend.json").getLines().mkString("\n")))).once()

    val eitherResult = getWatchlistIdsForUser(config, mockClient)(User("ecdb6as0230e2115", "friend-1")).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty[TokenWatchlistItem])
    result.size shouldBe 2
    result.head shouldBe TokenWatchlistItem("The Twilight Saga: Breaking Dawn - Part 2", "5d77688b9ab54400214e789b", "movie", "/library/metadata/5d77688b9ab54400214e789b")
  }

  it should "successfully fetch multiple watchlist pages from a friend on Plex" in {
    val mockClient = mock[HttpClient]
    val config = createConfiguration(Some("test-token"))
    (mockClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://community.plex.tv/api"),
      Some("test-token"),
      *
    ).returning(IO.pure(parse(Source.fromResource("plex-get-watchlist-from-friend-page-1.json").getLines().mkString("\n")))).repeat(13)
    (mockClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://community.plex.tv/api"),
      Some("test-token"),
      *
    ).returning(IO.pure(parse(Source.fromResource("plex-get-watchlist-from-friend.json").getLines().mkString("\n")))).once()

    val eitherResult = getWatchlistIdsForUser(config, mockClient)(User("ecdb6as0230e2115", "friend-1")).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty[TokenWatchlistItem])
    result.size shouldBe 2
    result.head shouldBe TokenWatchlistItem("The Twilight Saga: Breaking Dawn - Part 2", "5d77688b9ab54400214e789b", "movie", "/library/metadata/5d77688b9ab54400214e789b")
  }

  private def createConfiguration(plexToken: Option[String]): Configuration = Configuration(
    refreshInterval = 10.seconds,
    sonarrBaseUrl = Uri.unsafeFromString("https://localhost:8989"),
    sonarrApiKey = "sonarr-api-key",
    sonarrQualityProfileId = 0,
    sonarrRootFolder = "/root/",
    sonarrBypassIgnored = false,
    sonarrSeasonMonitoring = "all",
    radarrBaseUrl = Uri.unsafeFromString("https://localhost:7878"),
    radarrApiKey = "radarr-api-key",
    radarrQualityProfileId = 1,
    radarrRootFolder = "/root/",
    radarrBypassIgnored = false,
    plexWatchlistUrls = List(Uri.unsafeFromString("https://localhost:9090")),
    plexToken = plexToken
  )
}
