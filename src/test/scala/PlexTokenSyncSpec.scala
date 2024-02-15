

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import configuration.{Configuration, DeleteConfiguration, PlexConfiguration, RadarrConfiguration, SonarrConfiguration}
import http.HttpClient
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import model.GraphQLQuery
import org.http4s.{Method, Uri}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt
import scala.io.Source

class PlexTokenSyncSpec extends AnyFlatSpec with Matchers with MockFactory {
  "PlexTokenSync.run" should "do a single sync with all required fields provided" in {

    val mockHttpClient = mock[HttpClient]
    val config = createConfiguration(plexTokens = Set("plex-token"))
    defaultPlexMock(mockHttpClient)
    defaultRadarrMock(mockHttpClient)
    defaultSonarrMock(mockHttpClient)

    val sync: Unit = PlexTokenSync.run(config, mockHttpClient, firstRun = true).unsafeRunSync()

    sync shouldBe ()
  }

  private def createConfiguration(
                                   plexTokens: Set[String]
                                 ): Configuration = Configuration(
    refreshInterval = 10.seconds,
    SonarrConfiguration(
      sonarrBaseUrl = Uri.unsafeFromString("https://localhost:8989"),
      sonarrApiKey = "sonarr-api-key",
      sonarrQualityProfileId = 0,
      sonarrRootFolder = "/root/",
      sonarrBypassIgnored = false,
      sonarrSeasonMonitoring = "all",
      sonarrLanguageProfileId = 1,
      sonarrTagIds = Set(1)
    ),
    RadarrConfiguration(
      radarrBaseUrl = Uri.unsafeFromString("https://localhost:7878"),
      radarrApiKey = "radarr-api-key",
      radarrQualityProfileId = 1,
      radarrRootFolder = "/root/",
      radarrBypassIgnored = false,
      radarrTagIds = Set(2)
    ),
    PlexConfiguration(
      plexWatchlistUrls = Set(),
      plexTokens = plexTokens,
      skipFriendSync = false,
      hasPlexPass = true
    ),
    DeleteConfiguration(
      movieDeleting = false,
      endedShowDeleting = false,
      continuingShowDeleting = false,
      deleteInterval = 7.days
    )
  )

  private def defaultPlexMock(httpClient: HttpClient): HttpClient = {
    (httpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://metadata.provider.plex.tv/library/sections/watchlist/all?X-Plex-Token=plex-token&X-Plex-Container-Start=0&X-Plex-Container-Size=300"),
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("self-watchlist-from-token.json").getLines().mkString("\n")))).once()
    (httpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://discover.provider.plex.tv/library/metadata/5df46a38237002001dce338d?X-Plex-Token=plex-token"),
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("single-item-plex-metadata.json").getLines().mkString("\n")))).once()
    (httpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://discover.provider.plex.tv/library/metadata/617d3ab142705b2183b1b20b?X-Plex-Token=plex-token"),
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("single-item-plex-metadata2.json").getLines().mkString("\n")))).once()
    val query = GraphQLQuery(
      """query GetAllFriends {
        |        allFriendsV2 {
        |          user {
        |            id
        |            username
        |          }
        |        }
        |      }""".stripMargin)
    (httpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://community.plex.tv/api"),
      Some("plex-token"),
      Some(query.asJson)
    ).returning(IO.pure(parse(Source.fromResource("plex-get-all-friends.json").getLines().mkString("\n")))).once()
    (httpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://community.plex.tv/api"),
      Some("plex-token"),
      *
    ).returning(IO.pure(parse(Source.fromResource("plex-get-watchlist-from-friend.json").getLines().mkString("\n")))).twice()
    (httpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://discover.provider.plex.tv/library/metadata/5d77688b9ab54400214e789b?X-Plex-Token=plex-token"),
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("single-item-plex-metadata2.json").getLines().mkString("\n")))).once()
    (httpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://discover.provider.plex.tv/library/metadata/5d77688b594b2b001e68f2f0?X-Plex-Token=plex-token"),
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("single-item-plex-metadata2.json").getLines().mkString("\n")))).anyNumberOfTimes()
    (httpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://discover.provider.plex.tv/library/metadata/5d77688b9ab54400214e789b?X-Plex-Token=plex-token"),
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("single-item-plex-metadata2.json").getLines().mkString("\n")))).once()
    httpClient
  }

  private def defaultRadarrMock(httpClient: HttpClient): HttpClient = {
    val movieToAdd = """{
                       |  "title" : "Nowhere",
                       |  "tmdbId" : 1151534,
                       |  "qualityProfileId" : 1,
                       |  "rootFolderPath" : "/root/",
                       |  "addOptions" : {
                       |    "searchForMovie" : true
                       |  },
                       |  "tags" : [
                       |    2
                       |  ]
                       |}""".stripMargin
    val movieToAdd2 =
      """{
        |  "title" : "The Twilight Saga: Breaking Dawn - Part 2",
        |  "tmdbId" : 1151534,
        |  "qualityProfileId" : 1,
        |  "rootFolderPath" : "/root/",
        |  "addOptions" : {
        |    "searchForMovie" : true
        |  },
        |  "tags" : [
        |    2
        |  ]
        |}""".stripMargin
    val movieToAdd3 =
      """{
        |  "title" : "The Twilight Saga: Breaking Dawn - Part 1",
        |  "tmdbId" : 1151534,
        |  "qualityProfileId" : 1,
        |  "rootFolderPath" : "/root/",
        |  "addOptions" : {
        |    "searchForMovie" : true
        |  },
        |  "tags" : [
        |    2
        |  ]
        |}""".stripMargin
    (httpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:7878/api/v3/movie"),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("radarr.json").getLines().mkString("\n")))).once()
    (httpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:7878/api/v3/exclusions"),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("exclusions.json").getLines().mkString("\n")))).once()
    (httpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://localhost:7878/api/v3/movie"),
      Some("radarr-api-key"),
      parse(movieToAdd).toOption
    ).returning(IO.pure(parse("{}"))).once()
    (httpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://localhost:7878/api/v3/movie"),
      Some("radarr-api-key"),
      parse(movieToAdd2).toOption
    ).returning(IO.pure(parse("{}"))).once()
    (httpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://localhost:7878/api/v3/movie"),
      Some("radarr-api-key"),
      parse(movieToAdd3).toOption
    ).returning(IO.pure(parse("{}"))).once()
    httpClient
  }

  private def defaultSonarrMock(httpClient: HttpClient): HttpClient = {
    (httpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:8989/api/v3/series"),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("sonarr.json").getLines().mkString("\n")))).once()
    (httpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:8989/api/v3/importlistexclusion"),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("importlistexclusion.json").getLines().mkString("\n")))).once()
    httpClient
  }

}
