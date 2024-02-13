import cats.data.EitherT
import cats.effect.IO
import configuration.Configuration
import http.HttpClient
import model.Item
import org.slf4j.LoggerFactory
import plex.PlexUtils
import radarr.RadarrUtils
import sonarr.SonarrUtils
import cats.implicits._

object PlexTokenSync extends PlexUtils with SonarrUtils with RadarrUtils {

  private val logger = LoggerFactory.getLogger(getClass)

  def run(config: Configuration, client: HttpClient, firstRun: Boolean): IO[Unit] = {
    val result = for {
      selfWatchlist <- if (firstRun)
        getSelfWatchlist(config.plexConfiguration, client)
      else
        EitherT.pure[IO, Throwable](Set.empty[Item])
      _ = if (firstRun) logger.info(s"Found ${selfWatchlist.size} items on user's watchlist using the plex token")
      othersWatchlist <- if (!firstRun || config.plexConfiguration.skipFriendSync)
        EitherT.pure[IO, Throwable](Set.empty[Item])
      else
        getOthersWatchlist(config.plexConfiguration, client)
      watchlistDatas <- EitherT[IO, Throwable, List[Set[Item]]](config.plexConfiguration.plexWatchlistUrls.map(fetchWatchlistFromRss(client)).toList.sequence.map(Right(_)))
      watchlistData = watchlistDatas.flatten.toSet
      _ = if (firstRun) logger.info(s"Found ${othersWatchlist.size} items on other available watchlists using the plex token")
      movies <- fetchMovies(client)(config.radarrConfiguration.radarrApiKey, config.radarrConfiguration.radarrBaseUrl, config.radarrConfiguration.radarrBypassIgnored)
      series <- fetchSeries(client)(config.sonarrConfiguration.sonarrApiKey, config.sonarrConfiguration.sonarrBaseUrl, config.sonarrConfiguration.sonarrBypassIgnored)
      allIds = movies ++ series
      _ <- missingIds(client)(config)(allIds, selfWatchlist ++ othersWatchlist ++ watchlistData)
    } yield ()

    result.leftMap {
      err =>
        logger.warn(s"An error occurred: $err")
        err
    }.value.map(_.getOrElse(()))
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
            logger.warn(s"Found show \"${watchlistedItem.title}\" which does not exist yet in Sonarr, but we do not have the tvdb ID so will skip adding")
            Right(IO.unit)
          }
        case (false, "movie") =>
          if (watchlistedItem.getTmdbId.isDefined) {
            logger.debug(s"Found movie \"${watchlistedItem.title}\" which does not exist yet in Radarr")
            Right(addToRadarr(client)(config.radarrConfiguration)(watchlistedItem))
          } else {
            logger.warn(s"Found movie \"${watchlistedItem.title}\" which does not exist yet in Radarr, but we do not have the tmdb ID so will skip adding")
            Right(IO.unit)
          }

        case (false, c) =>
          logger.warn(s"Found $c \"${watchlistedItem.title}\", but I don't recognize the category")
          Left(new Throwable(s"Unknown category $c"))
      })
    } yield task.flatMap(EitherT.liftF[IO, Throwable, Unit])
  }.toList.sequence.map(_.toSet)

}
