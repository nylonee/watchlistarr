package configuration

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import http.HttpClient
import io.circe.Json
import org.http4s.{Method, Uri}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._

import scala.io.Source

class ConfigurationUtilsSpec extends AnyFlatSpec with Matchers with MockFactory {
  "ConfigurationUtils.create" should "start with all required values provided" in {

    val mockConfigReader = createMockConfigReader()
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.radarrConfiguration.radarrApiKey shouldBe "radarr-api-key"
    config.sonarrConfiguration.sonarrApiKey shouldBe "sonarr-api-key"
    config.sonarrConfiguration.sonarrLanguageProfileId shouldBe 3
  }

  it should "fail if missing sonarr API key" in {

    val mockConfigReader = createMockConfigReader(sonarrApiKey = None)
    val mockHttpClient = createMockHttpClient()

    an[IllegalArgumentException] should be thrownBy ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
  }

  it should "fail if missing radarr API key" in {

    val mockConfigReader = createMockConfigReader(radarrApiKey = None)
    val mockHttpClient = createMockHttpClient()

    an[IllegalArgumentException] should be thrownBy ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
  }

  it should "fetch the first accessible root folder of sonarr if none is provided" in {

    val mockConfigReader = createMockConfigReader()
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.sonarrConfiguration.sonarrRootFolder shouldBe "/data2"
  }

  it should "find the root folder provided in sonarr config" in {

    val mockConfigReader = createMockConfigReader(sonarrRootFolder = Some("/data3"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.sonarrConfiguration.sonarrRootFolder shouldBe "/data3"
  }

  it should "find the root folder with a trailing slash provided in sonarr config" in {

    val mockConfigReader = createMockConfigReader(sonarrRootFolder = Some("/data3/"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.sonarrConfiguration.sonarrRootFolder shouldBe "/data3"
  }

  it should "find the root folder with an escaped slash provided in sonarr config" in {

    val mockConfigReader = createMockConfigReader(sonarrRootFolder = Some("//data3"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.sonarrConfiguration.sonarrRootFolder shouldBe "/data3"
  }

  it should "throw an error if the sonarr root folder provided can't be found" in {

    val mockConfigReader = createMockConfigReader(sonarrRootFolder = Some("/unknown"))
    val mockHttpClient = createMockHttpClient()

    an[IllegalArgumentException] should be thrownBy ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
  }

  it should "fetch the first accessible root folder of radarr if none is provided" in {

    val mockConfigReader = createMockConfigReader()
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.radarrConfiguration.radarrRootFolder shouldBe "/data2"
  }


  it should "find the root folder provided in radarr config" in {

    val mockConfigReader = createMockConfigReader(radarrRootFolder = Some("/data3"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.radarrConfiguration.radarrRootFolder shouldBe "/data3"
  }

  it should "find the root folder with a trailing slash provided in radarr config" in {

    val mockConfigReader = createMockConfigReader(radarrRootFolder = Some("/data3/"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.radarrConfiguration.radarrRootFolder shouldBe "/data3"
  }

  it should "find the root folder with an escaped slash provided in radarr config" in {

    val mockConfigReader = createMockConfigReader(radarrRootFolder = Some("//data3"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.radarrConfiguration.radarrRootFolder shouldBe "/data3"
  }

  it should "throw an error if the radarr root folder provided can't be found" in {

    val mockConfigReader = createMockConfigReader(radarrRootFolder = Some("/unknown"))
    val mockHttpClient = createMockHttpClient()

    an[IllegalArgumentException] should be thrownBy ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
  }

  it should "work even if quality profiles are multiple words" in {

    val mockConfigReader = createMockConfigReader(qualityProfile = Some("HD - 720p/1080p"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.sonarrConfiguration.sonarrQualityProfileId shouldBe 7
    config.radarrConfiguration.radarrQualityProfileId shouldBe 7
  }

  it should "fetch a tag from Sonarr/Radarr" in {

    val mockConfigReader = createMockConfigReader(tags = Some("test-tag"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.sonarrConfiguration.sonarrTagIds shouldBe Set(3)
    config.radarrConfiguration.radarrTagIds shouldBe Set(3)
  }

  private def createMockConfigReader(
                                      sonarrApiKey: Option[String] = Some("sonarr-api-key"),
                                      sonarrRootFolder: Option[String] = None,
                                      radarrRootFolder: Option[String] = None,
                                      radarrApiKey: Option[String] = Some("radarr-api-key"),
                                      plexWatchlist1: Option[String] = None,
                                      plexWatchlist2: Option[String] = None,
                                      plexToken: Option[String] = Some("test-token"),
                                      qualityProfile: Option[String] = None,
                                      tags: Option[String] = None
                                    ): ConfigurationReader = {
    val unset = None

    val mockConfigReader = mock[ConfigurationReader]
    (mockConfigReader.getConfigOption _).expects(Keys.intervalSeconds).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrBaseUrl).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrApiKey).returning(sonarrApiKey).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrQualityProfile).returning(qualityProfile).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrRootFolder).returning(sonarrRootFolder).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrBypassIgnored).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrBaseUrl).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrApiKey).returning(radarrApiKey).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrQualityProfile).returning(qualityProfile).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrRootFolder).returning(radarrRootFolder).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrBypassIgnored).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.plexWatchlist1).returning(plexWatchlist1).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.plexWatchlist2).returning(plexWatchlist2).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrSeasonMonitoring).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.plexToken).returning(plexToken).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.skipFriendSync).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.deleteMovies).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.deleteContinuingShow).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.deleteEndedShow).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.deleteIntervalDays).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrTags).returning(tags).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrTags).returning(tags).anyNumberOfTimes()
    mockConfigReader
  }

  private def createMockHttpClient(): HttpClient = {
    val mockHttpClient = mock[HttpClient]

    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:8989").withPath(Uri.Path.unsafeFromString("/api/v3/qualityprofile")),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("quality-profile.json").getLines().mkString("\n")))).anyNumberOfTimes()
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:8989").withPath(Uri.Path.unsafeFromString("/api/v3/languageprofile")),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("sonarr-language-profile.json").getLines().mkString("\n")))).anyNumberOfTimes()
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:7878").withPath(Uri.Path.unsafeFromString("/api/v3/qualityprofile")),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("quality-profile.json").getLines().mkString("\n")))).anyNumberOfTimes()
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:8989").withPath(Uri.Path.unsafeFromString("/api/v3/rootFolder")),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("rootFolder.json").getLines().mkString("\n")))).anyNumberOfTimes()
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:7878").withPath(Uri.Path.unsafeFromString("/api/v3/rootFolder")),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(parse(Source.fromResource("rootFolder.json").getLines().mkString("\n")))).anyNumberOfTimes()
    (mockHttpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://discover.provider.plex.tv/rss?X-Plex-Token=test-token&X-Plex-Client-Identifier=watchlistarr"),
      None,
      Some(parse("""{"feedType": "watchlist"}""").getOrElse(Json.Null))
    ).returning(IO.pure(parse(Source.fromResource("rss-feed-generated.json").getLines().mkString("\n")))).anyNumberOfTimes()
    (mockHttpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("https://discover.provider.plex.tv/rss?X-Plex-Token=test-token&X-Plex-Client-Identifier=watchlistarr"),
      None,
      Some(parse("""{"feedType": "friendsWatchlist"}""").getOrElse(Json.Null))
    ).returning(IO.pure(parse(Source.fromResource("rss-feed-generated.json").getLines().mkString("\n")))).anyNumberOfTimes()
    (mockHttpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("http://localhost:8989").withPath(Uri.Path.unsafeFromString("/api/v3/tag")),
      Some("sonarr-api-key"),
      *
    ).returning(IO.pure(parse(Source.fromResource("tag-response.json").getLines().mkString("\n")))).anyNumberOfTimes()
    (mockHttpClient.httpRequest _).expects(
      Method.POST,
      Uri.unsafeFromString("http://localhost:7878").withPath(Uri.Path.unsafeFromString("/api/v3/tag")),
      Some("radarr-api-key"),
      *
    ).returning(IO.pure(parse(Source.fromResource("tag-response.json").getLines().mkString("\n")))).anyNumberOfTimes()
    mockHttpClient
  }
}
