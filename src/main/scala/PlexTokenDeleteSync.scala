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

  def run(config: Configuration, client: HttpClient): IO[Unit] =  {
    val result = for {
      selfWatchlist <- getSelfWatchlist(config, client)
      othersWatchlist <- getOthersWatchlist(config, client)
      moviesWithoutExclusions <- fetchMovies(client)(config.radarrApiKey, config.radarrBaseUrl, bypass = true)
      seriesWithoutExclusions <- fetchSeries(client)(config.sonarrApiKey, config.sonarrBaseUrl, bypass = true)
      allIdsWithoutExclusions = moviesWithoutExclusions ++ seriesWithoutExclusions
      _ <- missingIdsOnPlex(client)(config)(allIdsWithoutExclusions, selfWatchlist ++ othersWatchlist)
    } yield ()

    result.leftMap {
      err =>
        logger.warn(s"An error occurred: $err")
        err
    }.value.map(_.getOrElse(()))
  }

  private def missingIdsOnPlex(client: HttpClient)(config: Configuration)(existingItems: Set[Item], watchlist: Set[Item]): EitherT[IO, Throwable, Set[Unit]] = {
    for {
      item <- existingItems
      maybeExistingItem = watchlist.exists(_.matches(item))
      category = item.category
      task = EitherT.fromEither[IO]((maybeExistingItem, category) match {
        case (true, c) =>
          logger.debug(s"$c \"${item.title}\" already exists in Plex")
          Right(IO.unit)
        case (false, "show") =>
          logger.info(s"Found show \"${item.title}\" which is not watchlisted on Plex")
          Right(IO.unit)
        case (false, "movie") =>
          logger.info(s"Found movie \"${item.title}\" which is not watchlisted on Plex")
          Right(IO.unit)
        case (false, c) =>
          logger.warn(s"Found $c \"${item.title}\", but I don't recognize the category")
          Left(new Throwable(s"Unknown category $c"))
      })
    } yield task.flatMap(EitherT.liftF[IO, Throwable, Unit])
  }.toList.sequence.map(_.toSet)

}
