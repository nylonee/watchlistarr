package configuration

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import model.QualityProfile
import org.http4s.{Method, Uri}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import utils.HttpClient
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps

class ConfigurationSpec extends AnyFlatSpec with Matchers with MockFactory {
  "A configuration.Configuration" should "start with all required values provided" in {

    val mockConfigReader = createMockConfigReader()
    val mockHttpClient = createMockHttpClient()

    val config = new Configuration(mockConfigReader, mockHttpClient)
    noException should be thrownBy config
    config.radarrApiKey shouldBe "radarr-api-key"
    config.sonarrApiKey shouldBe "sonarr-api-key"
    config.plexWatchlistUrls shouldBe inAnyOrder(List(Uri.unsafeFromString("https://rss.plex.tv/1")))
  }

  it should "fail if missing sonarr API key" in {

    val mockConfigReader = createMockConfigReader(sonarrApiKey = None)
    val mockHttpClient = createMockHttpClient()

    an[IllegalArgumentException] should be thrownBy new Configuration(mockConfigReader, mockHttpClient)
  }

  it should "fail if missing radarr API key" in {

    val mockConfigReader = createMockConfigReader(radarrApiKey = None)
    val mockHttpClient = createMockHttpClient()

    an[IllegalArgumentException] should be thrownBy new Configuration(mockConfigReader, mockHttpClient)
  }

  it should "fail if missing plex watchlist 1 and 2" in {

    val mockConfigReader = createMockConfigReader(plexWatchlist1 = None)
    val mockHttpClient = createMockHttpClient()

    an[IllegalArgumentException] should be thrownBy new Configuration(mockConfigReader, mockHttpClient)
  }

  it should "pass if missing plex watchlist 1 but there's a plex watchlist 2" in {

    val mockConfigReader = createMockConfigReader(plexWatchlist1 = None, plexWatchlist2 = Some(s"https://rss.plex.tv/2"))
    val mockHttpClient = createMockHttpClient()

    val config = new Configuration(mockConfigReader, mockHttpClient)
    noException should be thrownBy config
    config.plexWatchlistUrls shouldBe inAnyOrder(List(Uri.unsafeFromString("https://rss.plex.tv/2")))
  }

  it should "pass if both plex watchlist 1 and 2 are provided" in {

    val mockConfigReader = createMockConfigReader(plexWatchlist1 = Some(s"https://rss.plex.tv/1"), plexWatchlist2 = Some(s"https://rss.plex.tv/2"))
    val mockHttpClient = createMockHttpClient()

    val config = new Configuration(mockConfigReader, mockHttpClient)
    noException should be thrownBy config
    config.plexWatchlistUrls shouldBe inAnyOrder(List(
      Uri.unsafeFromString("https://rss.plex.tv/1"),
      Uri.unsafeFromString("https://rss.plex.tv/2")
    ))
  }

  private def createMockConfigReader(
                                      sonarrApiKey: Option[String] = Some("sonarr-api-key"),
                                      radarrApiKey: Option[String] = Some("radarr-api-key"),
                                      plexWatchlist1: Option[String] = Some(s"https://rss.plex.tv/1"),
                                      plexWatchlist2: Option[String] = None
                                    ): ConfigurationReader = {
    val unset = None

    val mockConfigReader = mock[ConfigurationReader]
    (mockConfigReader.getConfigOption _).expects(Keys.intervalSeconds).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrBaseUrl).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrApiKey).returning(sonarrApiKey).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrQualityProfile).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrRootFolder).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.sonarrBypassIgnored).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrBaseUrl).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrApiKey).returning(radarrApiKey).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrQualityProfile).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrRootFolder).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.radarrBypassIgnored).returning(unset).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.plexWatchlist1).returning(plexWatchlist1).anyNumberOfTimes()
    (mockConfigReader.getConfigOption _).expects(Keys.plexWatchlist2).returning(plexWatchlist2).anyNumberOfTimes()
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
    mockHttpClient
  }
}
