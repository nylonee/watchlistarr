import io.circe.generic.auto._
import io.circe.parser.decode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.io.Source

class WatchlistSyncSpec extends AnyFlatSpec with Matchers {

  "A WatchlistSync" should "correctly deserialize movies" in {
    val jsonStr = Source.fromResource("radarr.json").getLines().mkString("\n")

    val decodedMovies = decode[List[RadarrMovie]](jsonStr)

    decodedMovies match {
      case Right(movies) =>
        movies should not be empty
        movies.head should be (RadarrMovie("Incredible But True", Some("tt13145534"), Some(735697)))
      case Left(error) =>
        fail(s"Failed to decode JSON: $error")
    }
  }

  it should "correctly deserialize series" in {
    val jsonStr = Source.fromResource("sonarr.json").getLines().mkString("\n")

    val decodedSeries = decode[List[SonarrSeries]](jsonStr)

    decodedSeries match {
      case Right(series) =>
        series should not be empty
        series.head should be (SonarrSeries("Three-Body", Some("tt20242042"), Some(421555)))
      case Left(error) =>
        fail(s"Failed to decode JSON: $error")
    }
  }

  it should "correctly deserialize watchlist" in {
    val jsonStr = Source.fromResource("watchlist.json").getLines().mkString("\n")

    val decodedWatchlist = decode[Watchlist](jsonStr)

    decodedWatchlist match {
      case Right(Watchlist(items)) =>
        items should not be empty
        items.head should be (Item("Enola Holmes 2 (2022)", List("imdb://tt14641788", "tmdb://829280", "tvdb://166087"), "movie"))
      case Left(error) =>
        fail(s"Failed to decode JSON: $error")
    }
  }
}
