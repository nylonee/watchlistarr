import cats.data.EitherT
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

    val result = for {
      watchlistDatas <- EitherT[IO, Throwable, List[Set[Item]]](config.plexConfiguration.plexWatchlistUrls.map(fetchWatchlistFromRss(client)).toList.sequence.map(Right(_)))
      watchlistData = watchlistDatas.flatten.toSet
      movies <- fetchMovies(client)(config.radarrConfiguration.radarrApiKey, config.radarrConfiguration.radarrBaseUrl, config.radarrConfiguration.radarrBypassIgnored)
      series <- fetchSeries(client)(config.sonarrConfiguration.sonarrApiKey, config.sonarrConfiguration.sonarrBaseUrl, config.sonarrConfiguration.sonarrBypassIgnored)
      allIds = movies ++ series
      _ <- missingIds(client)(config)(allIds, watchlistData)
    } yield ()

    result.value.map {
      case Left(err) =>
        logger.warn(s"An error occured while attempting to sync: $err")
      case Right(_) =>
        logger.debug("Watchlist sync complete")
    }
  }

  private def missingIds(client: HttpClient)(config: Configuration)(existingItems: Set[Item], watchlist: Set[Item]): EitherT[IO, Throwable, Set[Unit]] = {
    for {
      watchlistedItem <- watchlist
      maybeExistingItem = existingItems.exists(_.matches(watchlistedItem))
      category = watchlistedItem.category
      task = EitherT.fromEither[IO]((maybeExistingItem, category) match {
        case (true, c) =>
          logger.debug(s"$c \"${watchlistedItem.title}\" already exists in Sonarr/Radarr")
          Right(IO.unit)
        case (false, "show") =>
          if (watchlistedItem.getTvdbId.isDefined) {
            logger.debug(s"Found show \"${watchlistedItem.title}\" which does not exist yet in Sonarr")
            Right(addToSonarr(client)(config.sonarrConfiguration)(watchlistedItem))
          } else {
            logger.debug(s"Found show \"${watchlistedItem.title}\" which does not exist yet in Sonarr, but we do not have the tvdb ID so will skip adding")
            Right(IO.unit)
          }
        case (false, "movie") =>
          if (watchlistedItem.getTmdbId.isDefined) {
            logger.debug(s"Found movie \"${watchlistedItem.title}\" which does not exist yet in Radarr")
            Right(addToRadarr(client)(config.radarrConfiguration)(watchlistedItem))
          } else {
            logger.debug(s"Found movie \"${watchlistedItem.title}\" which does not exist yet in Radarr, but we do not have the tmdb ID so will skip adding")
            Right(IO.unit)
          }
        case (false, c) =>
          logger.warn(s"Found $c \"${watchlistedItem.title}\", but I don't recognize the category")
          Left(new Throwable(s"Unknown category $c"))
      })
    } yield task.flatMap(EitherT.liftF[IO, Throwable, Unit])
  }.toList.sequence.map(_.toSet)

}
