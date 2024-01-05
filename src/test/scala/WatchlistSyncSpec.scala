

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import configuration.{Configuration, DeleteConfiguration, PlexConfiguration, RadarrConfiguration, SonarrConfiguration}
import http.HttpClient
import org.http4s.{Method, Uri}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._

import scala.concurrent.duration.DurationInt
import scala.io.Source

class WatchlistSyncSpec extends AnyFlatSpec with Matchers with MockFactory {
  private val plexWatchlistUrl = Uri.unsafeFromString("https://rss.plex.tv/one")

  "WatchlistSync.run" should "do a single sync with all required fields provided and nothing to update" in {

    val mockHttpClient = mock[HttpClient]
    val config = createConfiguration()
    defaultPlexMock(mockHttpClient)
    defaultRadarrMock(mockHttpClient)
    defaultSonarrMock(mockHttpClient)

    val sync: Unit = WatchlistSync.run(config, mockHttpClient).unsafeRunSync()

    sync shouldBe()
  }

  it should "make a sonarr request when a series needs to be added" in {

    val mockHttpClient = mock[HttpClient]
    val config = createConfiguration()
    val seriesToAdd =
      """{
        |  "title" : "Three-Body (2023)",
        |  "tvdbId" : 421555,
        |  "qualityProfileId" : 0,
        |  "rootFolderPath" : "/root/",
        |  "addOptions" : {
        |    "monitor" : "all",
        |    "searchForCutoffUnmetEpisodes" : true,
        |    "searchForMissingEpisodes" : true
        |  },
        |  "languageProfileId" : 1,
        |  "monitored" : true
        |}""".stripMargin
    defaultPlexMock(mockHttpClient)
    defaultRadarrMock(mockHttpClient)
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:8989/api/v3/series"),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("sonarr-missingshow.json").getLines().mkString("\n")))).once()
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:8989/api/v3/importlistexclusion"),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("importlistexclusion.json").getLines().mkString("\n")))).once()
    (mockHttpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://localhost:8989/api/v3/series"),
      Some("sonarr-api-key"),
      parse(seriesToAdd).toOption
    ).returning(IO.pure(parse("{}"))).once()

    val sync: Unit = WatchlistSync.run(config, mockHttpClient).unsafeRunSync()

    sync shouldBe()
  }

  it should "ignore sonarr exclusions when sonarrBypassIgnored = true" in {

    val mockHttpClient = mock[HttpClient]
    val config = createConfiguration(sonarrBypassIgnored = true)
    defaultPlexMock(mockHttpClient)
    defaultRadarrMock(mockHttpClient)
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:8989/api/v3/series"),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("sonarr.json").getLines().mkString("\n")))).once()

    val sync: Unit = WatchlistSync.run(config, mockHttpClient).unsafeRunSync()

    sync shouldBe()
  }

  it should "make a radarr request when a movie needs to be added" in {

    val mockHttpClient = mock[HttpClient]
    val config = createConfiguration()
    val movieToAdd =
      """{
        |  "title" : "Oppenheimer (2023)",
        |  "tmdbId" : 872585,
        |  "qualityProfileId" : 1,
        |  "rootFolderPath" : "/root/",
        |  "addOptions" : {
        |    "searchForMovie" : true
        |  }
        |}""".stripMargin
    defaultPlexMock(mockHttpClient)
    defaultSonarrMock(mockHttpClient)
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:7878/api/v3/movie"),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("radarr-missingmovie.json").getLines().mkString("\n")))).once()
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:7878/api/v3/exclusions"),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("exclusions.json").getLines().mkString("\n")))).once()
    (mockHttpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://localhost:7878/api/v3/movie"),
      Some("radarr-api-key"),
      parse(movieToAdd).toOption
    ).returning(IO.pure(parse("{}"))).once()

    val sync: Unit = WatchlistSync.run(config, mockHttpClient).unsafeRunSync()

    sync shouldBe()
  }

  it should "ignore radarr exclusions when radarrBypassIgnored = true" in {

    val mockHttpClient = mock[HttpClient]
    val config = createConfiguration(radarrBypassIgnored = true)
    defaultPlexMock(mockHttpClient)
    defaultSonarrMock(mockHttpClient)
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:7878/api/v3/movie"),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("radarr.json").getLines().mkString("\n")))).once()

    val sync: Unit = WatchlistSync.run(config, mockHttpClient).unsafeRunSync()

    sync shouldBe()
  }

  it should "not update sonarr if the initial sonarr call fails" in {

    val mockHttpClient = mock[HttpClient]
    val config = createConfiguration()
    defaultPlexMock(mockHttpClient)
    defaultRadarrMock(mockHttpClient)
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:8989/api/v3/series"),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(Left(new UnknownError))).once()

    val sync: Unit = WatchlistSync.run(config, mockHttpClient).unsafeRunSync()

    sync shouldBe ()
  }

  it should "not update radarr if the initial radarr call fails" in {

    val mockHttpClient = mock[HttpClient]
    val config = createConfiguration()
    defaultPlexMock(mockHttpClient)
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("https://localhost:7878/api/v3/movie"),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(Left(new UnknownError))).once()

    val sync: Unit = WatchlistSync.run(config, mockHttpClient).unsafeRunSync()

    sync shouldBe ()
  }

  private def createConfiguration(
                                   sonarrBypassIgnored: Boolean = false,
                                   radarrBypassIgnored: Boolean = false
                                 ): Configuration = Configuration(
    refreshInterval = 10.seconds,
    SonarrConfiguration(
      sonarrBaseUrl = Uri.unsafeFromString("https://localhost:8989"),
      sonarrApiKey = "sonarr-api-key",
      sonarrQualityProfileId = 0,
      sonarrRootFolder = "/root/",
      sonarrBypassIgnored = sonarrBypassIgnored,
      sonarrSeasonMonitoring = "all",
      sonarrLanguageProfileId = 1
    ),
    RadarrConfiguration(
      radarrBaseUrl = Uri.unsafeFromString("https://localhost:7878"),
      radarrApiKey = "radarr-api-key",
      radarrQualityProfileId = 1,
      radarrRootFolder = "/root/",
      radarrBypassIgnored = radarrBypassIgnored
    ),
    PlexConfiguration(
      plexWatchlistUrls = Set(plexWatchlistUrl),
      plexTokens = Set("test-token"),
      skipFriendSync = false
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
      plexWatchlistUrl,
      None,
      None
    ).returning(IO.pure(parse(Source.fromResource("watchlist.json").getLines().mkString("\n")))).once()
    httpClient
  }

  private def defaultRadarrMock(httpClient: HttpClient): HttpClient = {
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
