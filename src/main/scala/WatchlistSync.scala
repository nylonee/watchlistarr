import cats.effect.IO
import cats.implicits._
import configuration.Configuration
import http.HttpClient
import model.Item
import org.slf4j.LoggerFactory
import plex.PlexUtils
import radarr.RadarrUtils
import sonarr.SonarrUtils

object WatchlistSync
  extends SonarrUtils with RadarrUtils with PlexUtils {

  private val logger = LoggerFactory.getLogger(getClass)

  def run(config: Configuration, client: HttpClient): IO[Unit] = {

    logger.debug("Starting watchlist sync")

    for {
      watchlistDatas <- config.plexWatchlistUrls.map(fetchWatchlistFromRss(client)).sequence
      watchlistData = watchlistDatas.flatten.toSet
      movies <- fetchMovies(client)(config.radarrApiKey, config.radarrBaseUrl, config.radarrBypassIgnored)
      series <- fetchSeries(client)(config.sonarrApiKey, config.sonarrBaseUrl, config.sonarrBypassIgnored)
      allIds = movies ++ series
      _ <- missingIds(client)(config)(allIds, watchlistData)
    } yield ()
  }

  private def missingIds(client: HttpClient)(config: Configuration)(existingItems: Set[Item], watchlist: Set[Item]): IO[Set[Unit]] =
    watchlist.map { watchlistedItem =>
      (existingItems.exists(_.matches(watchlistedItem)), watchlistedItem.category) match {
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

}
