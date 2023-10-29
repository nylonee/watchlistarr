import cats.effect.IO
import org.http4s.{Method, Uri}
import cats.implicits._
import configuration.Configuration
import io.circe.generic.auto._
import io.circe.syntax._
import model._
import org.slf4j.LoggerFactory
import utils.{ArrUtils, HttpClient}

object WatchlistSync {

  private val logger = LoggerFactory.getLogger(getClass)
  def run(config: Configuration): IO[Unit] = {

    logger.debug("Starting watchlist sync")

    for {
      watchlistDatas <- config.plexWatchlistUrls.map(fetchWatchlist(config.client)).sequence
      watchlistData = watchlistDatas.fold(Watchlist(Set.empty))(mergeWatchLists)
      movies <- fetchMovies(config.client)(config.radarrApiKey, config.radarrBaseUrl)
      series <- fetchSeries(config.client)(config.sonarrApiKey, config.sonarrBaseUrl)
      allIds = merge(movies, series)
      _ <- missingIds(config.client)(config)(allIds, watchlistData.items)
    } yield ()
  }

  private def mergeWatchLists(l: Watchlist, r: Watchlist): Watchlist = Watchlist(l.items ++ r.items)

  private def fetchWatchlist(client: HttpClient)(url: Uri): IO[Watchlist] =
    client.httpRequest(Method.GET, url).map {
      case Left(err) =>
        logger.warn(s"Unable to fetch watchlist from Plex: $err")
        Watchlist(Set.empty)
      case Right(json) =>
        logger.debug("Found Json from Plex watchlist, attempting to decode")
        json.as[Watchlist].getOrElse {
          logger.warn("Unable to fetch watchlist from Plex - decoding failure. Returning empty list instead")
          Watchlist(Set.empty)
        }
    }

  private def fetchMovies(client: HttpClient)(apiKey: String, baseUrl: Uri): IO[List[RadarrMovie]] =
    ArrUtils.getToArr(client)(baseUrl, apiKey, "movie").map {
      case Right(res) =>
        res.as[List[RadarrMovie]].getOrElse {
          logger.warn("Unable to fetch movies from Radarr - decoding failure. Returning empty list instead")
          List.empty
        }
      case Left(err) =>
        logger.warn(s"Received error while trying to fetch movies from Radarr: $err")
        throw err
    }

  private def fetchSeries(client: HttpClient)(apiKey: String, baseUrl: Uri): IO[List[SonarrSeries]] =
    ArrUtils.getToArr(client)(baseUrl, apiKey, "series").map {
      case Right(res) =>
        res.as[List[SonarrSeries]].getOrElse {
          logger.warn("Unable to fetch series from Sonarr - decoding failure. Returning empty list instead")
          List.empty
        }
      case Left(err) =>
        logger.warn(s"Received error while trying to fetch movies from Radarr: $err")
        throw err
    }

  private def merge(r: List[RadarrMovie], s: List[SonarrSeries]): Set[String] = {
    val allIds = r.map(_.imdbId) ++ r.map(_.tmdbId) ++ s.map(_.imdbId) ++ s.map(_.tvdbId)

    allIds.collect {
      case Some(x) => x.toString
    }.toSet
  }

  private def missingIds(client: HttpClient)(config: Configuration)(allIds: Set[String], watchlist: Set[Item]): IO[Set[Unit]] =
    watchlist.map { watchlistedItem =>
      val watchlistIds = watchlistedItem.guids.map(cleanId).toSet

      (watchlistIds.exists(allIds.contains), watchlistedItem.category) match {
        case (true, c) =>
          logger.debug(s"$c \"${watchlistedItem.title}\" already exists in Sonarr/Radarr")
          IO.unit
        case (false, "show") =>
          logger.debug(s"Found show \"${watchlistedItem.title}\" which does not exist yet in Sonarr")
          addToSonarr(client)(config)(watchlistedItem)
        case (false, "movie") =>
          logger.debug(s"Found movie \"${watchlistedItem.title}\" which does not exist yet in Radarr")
          addToRadarr(client)(config)(watchlistedItem)
        case (false, c) =>
          logger.warn(s"Found $c \"${watchlistedItem.title}\", but I don't recognize the category")
          IO.unit
      }
    }.toList.sequence.map(_.toSet)

  private def cleanId: String => String = _.split("://").last

  private case class RadarrPost(title: String, tmdbId: Long, qualityProfileId: Int = 6, rootFolderPath: String, addOptions: AddOptions = AddOptions())

  private case class AddOptions(searchForMovie: Boolean = true)

  private def findTmdbId(strings: List[String]): Option[Long] =
    strings.find(_.startsWith("tmdb://")).flatMap(_.stripPrefix("tmdb://").toLongOption)

  private def addToRadarr(client: HttpClient)(config: Configuration)(item: Item): IO[Unit] = {

    val movie = RadarrPost(item.title, findTmdbId(item.guids).getOrElse(0L), config.radarrQualityProfileId, config.radarrRootFolder)

    ArrUtils.postToArr(client)(config.radarrBaseUrl, config.radarrApiKey, "movie")(movie.asJson).map {
      case Right(_) =>
        logger.info(s"Successfully added movie ${item.title} to Radarr")
      case Left(err) =>
        logger.error(s"Failed to add movie ${item.title}: $err")
    }
  }

  private case class SonarrPost(title: String, tvdbId: Long, qualityProfileId: Int, rootFolderPath: String, addOptions: SonarrAddOptions = SonarrAddOptions())

  private case class SonarrAddOptions(monitor: String = "all", searchForCutoffUnmetEpisodes: Boolean = true, searchForMissingEpisodes: Boolean = true)

  private def findTvdbId(strings: List[String]): Option[Long] =
    strings.find(_.startsWith("tvdb://")).flatMap(_.stripPrefix("tvdb://").toLongOption)

  private def addToSonarr(client: HttpClient)(config: Configuration)(item: Item): IO[Unit] = {

    val show = SonarrPost(item.title, findTvdbId(item.guids).getOrElse(0L), config.sonarrQualityProfileId, config.sonarrRootFolder)

    ArrUtils.postToArr(client)(config.sonarrBaseUrl, config.sonarrApiKey, "series")(show.asJson).map {
      case Right(_) =>
        logger.info(s"Successfully added show ${item.title} to Sonarr")
      case Left(err) =>
        logger.info(s"Failed to add show ${item.title}: $err")
    }
  }

}
