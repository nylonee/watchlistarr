import cats.effect.IO
import org.http4s.{Header, Method, Request, Uri}
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString
import cats.implicits._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityDecoder._
import io.circe.syntax._
import model._

object WatchlistSync {
  def run(config: Configuration): IO[Unit] = {

    for {
      watchlistDatas <- config.plexWatchlistUrls.map(fetchWatchlist).sequence
      watchlistData = watchlistDatas.fold(Watchlist(Set.empty))(mergeWatchLists)
      movies <- fetchMovies(config.radarrApiKey, config.radarrBaseUrl)
      series <- fetchSeries(config.sonarrApiKey, config.sonarrBaseUrl)
      allIds = merge(movies, series)
      _ <- missingIds(config)(allIds, watchlistData.items)
    } yield ()
  }

  private def mergeWatchLists(l: Watchlist, r: Watchlist): Watchlist = Watchlist(l.items ++ r.items)

  private def fetchWatchlist(url: String): IO[Watchlist] = {
    EmberClientBuilder.default[IO].build.use { client =>
      val req = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(url)
      ).withHeaders(Header.Raw(CIString("Accept"), "application/json"))

      client.expect[Watchlist](req)
    }
  }

  private def fetchMovies(apiKey: String, baseUrl: String): IO[List[RadarrMovie]] =
    ArrUtils.getToArr(baseUrl, apiKey, "movie").map {
      case Right(res) =>
        res.as[List[RadarrMovie]].getOrElse {
          println("Unable to fetch movies from Radarr - decoding failure. Returning empty list instead")
          List.empty
        }
      case Left(err) =>
        println(s"Received error while trying to fetch movies from Radarr: $err")
        throw err
    }

  private def fetchSeries(apiKey: String, baseUrl: String): IO[List[SonarrSeries]] =
    ArrUtils.getToArr(baseUrl, apiKey, "series").map {
      case Right(res) =>
        res.as[List[SonarrSeries]].getOrElse {
          println("Unable to fetch series from Sonarr - decoding failure. Returning empty list instead")
          List.empty
        }
      case Left(err) =>
        println(s"Received error while trying to fetch movies from Radarr: $err")
        throw err
    }

  private def merge(r: List[RadarrMovie], s: List[SonarrSeries]): Set[String] = {
    val allIds = r.map(_.imdbId) ++ r.map(_.tmdbId) ++ s.map(_.imdbId) ++ s.map(_.tvdbId)

    allIds.collect {
      case Some(x) => x.toString
    }.toSet
  }

  private def missingIds(config: Configuration)(allIds: Set[String], watchlist: Set[Item]): IO[Set[Unit]] =
    watchlist.map { watchlistedItem =>
      val watchlistIds = watchlistedItem.guids.map(cleanId).toSet

      (watchlistIds.exists(allIds.contains), watchlistedItem.category) match {
        case (true, c) =>
          println(s"$c \"${watchlistedItem.title}\" already exists in Sonarr/Radarr")
          IO.unit
        case (false, "show") =>
          println(s"Found show \"${watchlistedItem.title}\" which does not exist yet in Sonarr")
          addToSonarr(config)(watchlistedItem)
        case (false, "movie") =>
          println(s"Found movie \"${watchlistedItem.title}\" which does not exist yet in Radarr")
          addToRadarr(config)(watchlistedItem)
        case (false, c) =>
          println(s"Found $c \"${watchlistedItem.title}\", but I don't recognize the category")
          IO.unit
      }
    }.toList.sequence.map(_.toSet)

  private def cleanId: String => String = _.split("://").last

  private case class RadarrPost(title: String, tmdbId: Long, qualityProfileId: Int = 6, rootFolderPath: String, addOptions: AddOptions = AddOptions())

  private case class AddOptions(searchForMovie: Boolean = true)

  private def findTmdbId(strings: List[String]): Option[Long] =
    strings.find(_.startsWith("tmdb://")).flatMap(_.stripPrefix("tmdb://").toLongOption)

  private def addToRadarr(config: Configuration)(item: Item): IO[Unit] = {

    val movie = RadarrPost(item.title, findTmdbId(item.guids).getOrElse(0L), config.radarrQualityProfileId, config.radarrRootFolder)

    ArrUtils.postToArr(config.radarrBaseUrl, config.radarrApiKey, "movie")(movie.asJson).map {
      case Right(_) =>
        println(s"Successfully added movie")
      case Left(err) =>
        println(s"Failed to add movie: $err")
    }
  }

  private case class SonarrPost(title: String, tvdbId: Long, qualityProfileId: Int, rootFolderPath: String, addOptions: SonarrAddOptions = SonarrAddOptions())

  private case class SonarrAddOptions(monitor: String = "all", searchForCutoffUnmetEpisodes: Boolean = true, searchForMissingEpisodes: Boolean = true)

  private def findTvdbId(strings: List[String]): Option[Long] =
    strings.find(_.startsWith("tvdb://")).flatMap(_.stripPrefix("tvdb://").toLongOption)

  private def addToSonarr(config: Configuration)(item: Item): IO[Unit] = {

    val show = SonarrPost(item.title, findTvdbId(item.guids).getOrElse(0L), config.sonarrQualityProfileId, config.sonarrRootFolder)

    ArrUtils.postToArr(config.sonarrBaseUrl, config.sonarrApiKey, "series")(show.asJson).map {
      case Right(_) =>
        println(s"Successfully added show")
      case Left(err) =>
        println(s"Failed to add show: $err")
    }
  }

}
