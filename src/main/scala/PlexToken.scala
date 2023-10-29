import cats.effect.IO
import configuration.Configuration
import io.circe.Json
import io.circe.syntax.EncoderOps
import model._
import cats.implicits._
import org.http4s.{Method, Uri}
import org.slf4j.LoggerFactory
import io.circe.generic.auto._

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
      movies <- WatchlistSync.fetchMovies(config.client)(apiKey = config.radarrApiKey, baseUrl = config.radarrBaseUrl, bypass = true)
      shows <- WatchlistSync.fetchSeries(config.client)(apiKey = config.sonarrApiKey, baseUrl = config.sonarrBaseUrl, bypass = true)
      allTitlesOnArr = movies.map(_.title) ++ shows.map(_.title)
      allWatchlistTitles = (selfWatchlist ++ watchlists).map(_.title)
      _ <- deleteContentNotOnWatchlist(allWatchlistTitles, allTitlesOnArr)
    } yield ()

  private def deleteContentNotOnWatchlist(watchlist: List[String], arrItems: List[String]): IO[Unit] = IO {
    arrItems.foreach { maybeDeleteThis =>
      if (!watchlist.contains(maybeDeleteThis)) {
        logger.warn(s"DRY RUN: Deleting $maybeDeleteThis")
      }
    }

    watchlist.foreach { wasThisIncluded =>
      if (!arrItems.contains(wasThisIncluded)) {
        logger.warn(s"Straggler: ${wasThisIncluded}")
      }
    }
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
