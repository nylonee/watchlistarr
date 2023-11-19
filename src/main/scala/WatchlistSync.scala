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
      watchlistDatas <- EitherT[IO, Throwable, List[Set[Item]]](config.plexWatchlistUrls.map(fetchWatchlistFromRss(client)).sequence.map(Right(_)))
      watchlistData = watchlistDatas.flatten.toSet
      movies <- fetchMovies(client)(config.radarrApiKey, config.radarrBaseUrl, config.radarrBypassIgnored)
      series <- fetchSeries(client)(config.sonarrApiKey, config.sonarrBaseUrl, config.sonarrBypassIgnored)
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
          logger.debug(s"Found show \"${watchlistedItem.title}\" which does not exist yet in Sonarr")
          Right(addToSonarr(client)(config)(watchlistedItem))
        case (false, "movie") =>
          logger.debug(s"Found movie \"${watchlistedItem.title}\" which does not exist yet in Radarr")
          Right(addToRadarr(client)(config)(watchlistedItem))
        case (false, c) =>
          logger.warn(s"Found $c \"${watchlistedItem.title}\", but I don't recognize the category")
          Left(new Throwable(s"Unknown category $c"))
      })
    } yield task.flatMap(EitherT.liftF[IO, Throwable, Unit])
  }.toList.sequence.map(_.toSet)

}
