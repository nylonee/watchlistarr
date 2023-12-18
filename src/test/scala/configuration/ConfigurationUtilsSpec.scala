package configuration

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import http.HttpClient
import io.circe.Json
import org.http4s.{Method, Uri}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax.EncoderOps

import scala.io.Source

class ConfigurationUtilsSpec extends AnyFlatSpec with Matchers with MockFactory {
  "ConfigurationUtils.create" should "start with all required values provided" in {

    val mockConfigReader = createMockConfigReader()
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.radarrApiKey shouldBe "radarr-api-key"
    config.sonarrApiKey shouldBe "sonarr-api-key"
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
    config.sonarrRootFolder shouldBe "/data2"
  }

  it should "find the root folder provided in sonarr config" in {

    val mockConfigReader = createMockConfigReader(sonarrRootFolder = Some("/data3"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.sonarrRootFolder shouldBe "/data3"
  }

  it should "find the root folder with a trailing slash provided in sonarr config" in {

    val mockConfigReader = createMockConfigReader(sonarrRootFolder = Some("/data3/"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.sonarrRootFolder shouldBe "/data3"
  }

  it should "find the root folder with an escaped slash provided in sonarr config" in {

    val mockConfigReader = createMockConfigReader(sonarrRootFolder = Some("//data3"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.sonarrRootFolder shouldBe "/data3"
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
    config.radarrRootFolder shouldBe "/data2"
  }


  it should "find the root folder provided in radarr config" in {

    val mockConfigReader = createMockConfigReader(radarrRootFolder = Some("/data3"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.radarrRootFolder shouldBe "/data3"
  }

  it should "find the root folder with a trailing slash provided in radarr config" in {

    val mockConfigReader = createMockConfigReader(radarrRootFolder = Some("/data3/"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.radarrRootFolder shouldBe "/data3"
  }

  it should "find the root folder with an escaped slash provided in radarr config" in {

    val mockConfigReader = createMockConfigReader(radarrRootFolder = Some("//data3"))
    val mockHttpClient = createMockHttpClient()

    val config = ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
    noException should be thrownBy config
    config.radarrRootFolder shouldBe "/data3"
  }

  it should "throw an error if the radarr root folder provided can't be found" in {

    val mockConfigReader = createMockConfigReader(radarrRootFolder = Some("/unknown"))
    val mockHttpClient = createMockHttpClient()

    an[IllegalArgumentException] should be thrownBy ConfigurationUtils.create(mockConfigReader, mockHttpClient).unsafeRunSync()
  }

  private def createMockConfigReader(
                                      sonarrApiKey: Option[String] = Some("sonarr-api-key"),
                                      sonarrRootFolder: Option[String] = None,
                                      radarrRootFolder: Option[String] = None,
                                      radarrApiKey: Option[String] = Some("radarr-api-key"),
                                      plexWatchlist1: Option[String] = None,
                                      plexWatchlist2: Option[String] = None,
                                      plexToken: Option[String] = Some("test-token")
                                    ): ConfigurationReader = {
    val unset = None

    val mockConfigReader = mock[ConfigurationReader]
    (mockConfigReader.getConfigOption _).expects(Keys.intervalSeconds).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrBaseUrl).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrApiKey).returning(sonarrApiKey).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrQualityProfile).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrRootFolder).returning(sonarrRootFolder).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrBypassIgnored).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrBaseUrl).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrApiKey).returning(radarrApiKey).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrQualityProfile).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrRootFolder).returning(radarrRootFolder).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrBypassIgnored).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.plexWatchlist1).returning(plexWatchlist1).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.plexWatchlist2).returning(plexWatchlist2).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrSeasonMonitoring).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.plexToken).returning(plexToken).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.skipFriendSync).returning(unset).anyNumberOfTimes()
    mockConfigReader
  }

  private def createMockHttpClient(): HttpClient = {
    val mockHttpClient = mock[HttpClient]

    val defaultQualityProfileResponse = List(QualityProfile("1080p", 5))
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:8989").withPath(Uri.Path.unsafeFromString("/api/v3/qualityprofile")),
      Some("sonarr-api-key"),
      None
    ).returning(IO.pure(Right(defaultQualityProfileResponse.asJson))).anyNumberOfTimes()
    (mockHttpClient.httpRequest _).expects(
      Method.GET,
      Uri.unsafeFromString("http://localhost:7878").withPath(Uri.Path.unsafeFromString("/api/v3/qualityprofile")),
      Some("radarr-api-key"),
      None
    ).returning(IO.pure(Right(defaultQualityProfileResponse.asJson))).anyNumberOfTimes()
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
    mockHttpClient
  }
}
