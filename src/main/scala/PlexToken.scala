import cats.effect.IO
import configuration.Configuration
import io.circe.Json
import io.circe.syntax.EncoderOps
import model._
import cats.implicits._
import org.http4s.{Method, Uri}
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import utils.ArrUtils

object PlexToken {

  private val logger = LoggerFactory.getLogger(getClass)

  def ping(config: Configuration): IO[Unit] = config.plexToken.map { token =>
    val url = Uri
      .unsafeFromString("https://plex.tv/api/v2/ping")
      .withQueryParam("X-Plex-Token", token)
      .withQueryParam("X-Plex-Client-Identifier", "watchlistarr")

    config.client.httpRequest(Method.GET, url).map {
      case Right(_) =>
        logger.info(s"Refreshed the access token expiry")
      case Left(err) =>
        logger.warn(s"Unable to ping plex.tv: $err")
    }
  }.getOrElse(IO.unit)

  def getWatchlist(config: Configuration): IO[Unit] =
    for {
      selfWatchlist <- getSelfWatchlist(config)
      friends <- getFriends(config)
      watchlists <- getWatchlists(config)(friends)
      movies <- WatchlistSync.fetchMovies(config.client)(apiKey = config.radarrApiKey, baseUrl = config.radarrBaseUrl, bypass = config.radarrBypassIgnored)
      shows <- WatchlistSync.fetchSeries(config.client)(apiKey = config.sonarrApiKey, baseUrl = config.sonarrBaseUrl, bypass = config.sonarrBypassIgnored)
      allTitlesOnArr = moviesAndSeriesIntoItems(movies, shows)
      allWatchlistTitles = selfWatchlist ++ watchlists
      _ <- deleteContentNotOnWatchlist(config)(allWatchlistTitles, allTitlesOnArr)
    } yield ()

  private def deleteContentNotOnWatchlist(config: Configuration)(watchlist: List[Item], arrItems: List[Item]): IO[Unit] = {

    logger.info(s"Beginning delete process, starting with ${watchlist.length} watchlist items and ${arrItems.length} -arrs")
    logger.info(s"We look for the ones that have a watchlist entry but no arr entry, they need to be looked up")
    val watchlistItemsToLookup = watchlist.map { w =>
      if (!arrItems.map(_.title).contains(w.title)) {
        logger.warn(s"Found watchlist entry to lookup: ${w.title}")
        Some(w)
      } else {
        None
      }
    }.collect { case Some(x) => x }

    if (watchlistItemsToLookup.isEmpty) {
      logger.info("No watchlist items remaining after comparing to -arr apps, we are ready for deletes")
      IO.unit
    } else {
      val movies = watchlistItemsToLookup.filter(_.category == "movie")
      logger.info(s"Movies: $movies")

      movies
        .map(i =>
          existInRadarr(config)(i).map(r => (r, i))
        )
        .sequence
        .map(_.filter(!_._1))
        .map(r => logger.info(s"reamining: $r"))
    }
  }

  private def existInSonarr(config: Configuration)(item: Item): IO[Boolean] = {
    val baseUrl = config.sonarrBaseUrl.withQueryParam("term", item.title)

    ArrUtils.getToArr(config.client)(baseUrl, config.sonarrApiKey, "series/lookup").map {
      case Right(res) =>
        val result = res.as[List[SonarrSeries]].getOrElse {
          logger.warn("Unable to fetch series from Sonarr")
          List.empty
        }.exists(_.path.nonEmpty)

        logger.info(s"Does ${item.title} exist in sonarr? $result")
        result
      case Left(err) =>
        logger.warn(s"Received error while trying to lookup ${item.title} in Sonarr: $err")
        false
    }
  }

  private def existInRadarr(config: Configuration)(item: Item): IO[Boolean] = {
    val baseUrl = config.radarrBaseUrl.withQueryParam("term", item.title)

    ArrUtils.getToArr(config.client)(baseUrl, config.radarrApiKey, "movie/lookup").map {
      case Right(res) =>
        val result = res.as[List[RadarrMovie]].getOrElse {
          logger.warn("Unable to fetch series from Radarr")
          List.empty
        }.exists(_.path.nonEmpty)

        logger.info(s"Does ${item.title} exist in radarr? $result")
        result
      case Left(err) =>
        logger.warn(s"Received error while trying to lookup ${item.title} in Radarr: $err")
        false
    }
  }

  private def moviesAndSeriesIntoItems(movies: List[RadarrMovie], series: List[SonarrSeries]): List[Item] = {
    val moviesItemized = movies.map(m => Item(m.title, List(m.imdbId, m.tmdbId.map(_.toString)).collect { case Some(r) => r }, "movie"))
    val seriesItemized = series.map(s => Item(s.title, List(s.imdbId, s.tvdbId.map(_.toString)).collect { case Some(r) => r }, "show"))

    moviesItemized ++ seriesItemized
  }

  private def getSelfWatchlist(config: Configuration): IO[List[Item]] =
    config.plexToken.map { token =>
      val url = Uri
        .unsafeFromString("https://metadata.provider.plex.tv/library/sections/watchlist/all")
        .withQueryParam("X-Plex-Token", token)

      config.client.httpRequest(Method.GET, url).map {
        case Right(result) =>
          selfWatchlistToItems(result)
        case Left(err) =>
          logger.warn(s"Unable to query metadata.provider.plex.tv: $err")
          List.empty
      }
    }.getOrElse(IO.pure(List.empty))

  def selfWatchlistToItems(res: Json): List[Item] =
    res.hcursor
      .downField("MediaContainer")
      .downField("Metadata")
      .as[List[MetadataItem]]
      .getOrElse(List.empty)
      .map(_.toItem)

  private def getFriends(config: Configuration): IO[List[User]] = config.plexToken.map { token =>
    val url = Uri.unsafeFromString("https://community.plex.tv/api")

    val query = GraphQLQuery(
      """query GetAllFriends {
        |        allFriendsV2 {
        |          user {
        |            id
        |            username
        |          }
        |        }
        |      }""".stripMargin)

    config.client.httpRequest(Method.POST, url, Some(token), Some(query.asJson)).map {
      case Right(res) =>
        jsonToUserList(res)
      case Left(err) =>
        logger.warn(s"Unable to query plex.tv: $err")
        List.empty
    }
  }.getOrElse(IO.pure(List.empty))

  def jsonToUserList(res: Json): List[User] =
    res.hcursor
      .downField("data")
      .downField("allFriendsV2")
      .as[List[RootUser]]
      .getOrElse(List.empty).map(_.user)

  private def getWatchlists(config: Configuration)(users: List[User]): IO[List[Item]] = {
    users.map { user =>
      val url = Uri.unsafeFromString("https://community.plex.tv/api")
      val query = GraphQLQuery(
        """query GetWatchlistHub ($uuid: ID = "", $first: PaginationInt !, $after: String) {
        user(id: $uuid) {
          watchlist(first: $first, after: $after) {
            nodes {
            ...itemFields
            }
            pageInfo {
              hasNextPage
              endCursor
            }
          }
        }
      }

        fragment itemFields on MetadataItem {
        id
        title
        publicPagesURL
        type
      }""".stripMargin,
        Some(
          s"""{
             |  "first": 100,
             |  "uuid": "${user.id}"
             |}""".stripMargin.asJson)
      )

      config.client.httpRequest(Method.POST, url, config.plexToken, Some(query.asJson)).map {
        case Right(res) =>
          jsonToItemList(res)
        case Left(err) =>
          logger.warn(s"Unable to query plex.tv: $err")
          List.empty
      }
    }.sequence.map(_.flatten)
  }

  private def jsonToItemList(res: Json): List[Item] =
    res.hcursor
      .downField("data")
      .downField("user")
      .downField("watchlist")
      .downField("nodes")
      .as[List[GraphQLItem]]
      .getOrElse(List.empty)
      .map(_.toItem)

}
