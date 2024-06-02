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

object PlexTokenDeleteSync extends PlexUtils with SonarrUtils with RadarrUtils {

  private val logger = LoggerFactory.getLogger(getClass)

  def run(config: Configuration, client: HttpClient): IO[Unit] = {
    val result = for {
      selfWatchlist <- getSelfWatchlist(config.plexConfiguration, client)
      othersWatchlist <-
        if (config.plexConfiguration.skipFriendSync) EitherT.pure[IO, Throwable](Set.empty[Item])
        else getOthersWatchlist(config.plexConfiguration, client)
      watchlistDatas <- EitherT[IO, Throwable, List[Set[Item]]](
        config.plexConfiguration.plexWatchlistUrls.map(fetchWatchlistFromRss(client)).toList.sequence.map(Right(_))
      )
      watchlistData = watchlistDatas.flatten.toSet
      moviesWithoutExclusions <- fetchMovies(client)(
        config.radarrConfiguration.radarrApiKey,
        config.radarrConfiguration.radarrBaseUrl,
        bypass = true
      )
      seriesWithoutExclusions <- fetchSeries(client)(
        config.sonarrConfiguration.sonarrApiKey,
        config.sonarrConfiguration.sonarrBaseUrl,
        bypass = true
      )
      allIdsWithoutExclusions = moviesWithoutExclusions ++ seriesWithoutExclusions
      _ <- missingIdsOnPlex(client)(config)(allIdsWithoutExclusions, selfWatchlist ++ othersWatchlist ++ watchlistData)
    } yield ()

    result
      .leftMap { err =>
        logger.warn(s"An error occurred: $err")
        err
      }
      .value
      .map(_.getOrElse(()))
  }

  private def missingIdsOnPlex(
      client: HttpClient
  )(config: Configuration)(existingItems: Set[Item], watchlist: Set[Item]): EitherT[IO, Throwable, Set[Unit]] = {
    for {
      item <- existingItems
      maybeExistingItem = watchlist.exists(_.matches(item))
      task = (maybeExistingItem, item.category) match {
        case (true, c) =>
          logger.debug(s"$c \"${item.title}\" already exists in Plex")
          EitherT[IO, Throwable, Unit](IO.pure(Right(())))
        case (false, "show") =>
          deleteSeries(client, config)(item)
        case (false, "movie") =>
          deleteMovie(client, config)(item)
        case (false, c) =>
          logger.warn(s"Found $c \"${item.title}\", but I don't recognize the category")
          EitherT[IO, Throwable, Unit](IO.pure(Left(new Throwable(s"Unknown category $c"))))
      }
    } yield task
  }.toList.sequence.map(_.toSet)

  private def deleteMovie(client: HttpClient, config: Configuration)(movie: Item): EitherT[IO, Throwable, Unit] =
    if (config.deleteConfiguration.movieDeleting) {
      deleteFromRadarr(client, config.radarrConfiguration, config.deleteConfiguration.deleteFiles)(movie)
    } else {
      logger.info(s"Found movie \"${movie.title}\" which is not watchlisted on Plex")
      EitherT.pure[IO, Throwable](())
    }

  private def deleteSeries(client: HttpClient, config: Configuration)(show: Item): EitherT[IO, Throwable, Unit] =
    if (show.ended.contains(true) && config.deleteConfiguration.endedShowDeleting) {
      deleteFromSonarr(client, config.sonarrConfiguration, config.deleteConfiguration.deleteFiles)(show)
    } else if (show.ended.contains(false) && config.deleteConfiguration.continuingShowDeleting) {
      deleteFromSonarr(client, config.sonarrConfiguration, config.deleteConfiguration.deleteFiles)(show)
    } else {
      logger.info(s"Found show \"${show.title}\" which is not watchlisted on Plex")
      EitherT[IO, Throwable, Unit](IO.pure(Right(())))
    }

}
