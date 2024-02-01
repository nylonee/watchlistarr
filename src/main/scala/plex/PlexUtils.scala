package plex

import cats.data.EitherT
import cats.effect.IO
import cats.implicits.toTraverseOps
import configuration.PlexConfiguration
import http.HttpClient
import model.{GraphQLQuery, Item}
import org.http4s.{Method, Uri}
import org.slf4j.LoggerFactory
import io.circe.generic.extras
import io.circe.generic.extras.auto._
import io.circe.syntax.EncoderOps
import org.http4s.client.UnexpectedStatus

trait PlexUtils {

  private val logger = LoggerFactory.getLogger(getClass)

  implicit val customConfig: extras.Configuration =
    extras.Configuration.default
      .withDefaults

  protected def fetchWatchlistFromRss(client: HttpClient)(url: Uri): IO[Set[Item]] =
    client.httpRequest(Method.GET, url).map {
      case Left(err) =>
        logger.warn(s"Unable to fetch watchlist from Plex: $err")
        Set.empty
      case Right(json) =>
        logger.debug("Found Json from Plex watchlist, attempting to decode")
        json.as[Watchlist].map(_.items).getOrElse {
          logger.warn("Unable to fetch watchlist from Plex - decoding failure. Returning empty list instead")
          Set.empty
        }
    }

  protected def ping(client: HttpClient)(config: PlexConfiguration): IO[Unit] = {
    config.plexTokens.map { token =>
      val url = Uri
        .unsafeFromString("https://plex.tv/api/v2/ping")
        .withQueryParam("X-Plex-Token", token)
        .withQueryParam("X-Plex-Client-Identifier", "watchlistarr")

      client.httpRequest(Method.GET, url).map {
        case Right(_) =>
          logger.info(s"Refreshed the access token expiry")
        case Left(err) =>
          logger.warn(s"Unable to ping plex.tv: $err")
      }
    }
  }.toList.sequence.map(_ => ())

  protected def getSelfWatchlist(config: PlexConfiguration, client: HttpClient): EitherT[IO, Throwable, Set[Item]] = config.plexTokens.map { token =>
    val url = Uri
      .unsafeFromString("https://metadata.provider.plex.tv/library/sections/watchlist/all")
      .withQueryParam("X-Plex-Token", token)
      .withQueryParam("X-Plex-Container-Start", 0) // todo: pagination
      .withQueryParam("X-Plex-Container-Size", 300)

    for {
      response <- EitherT(client.httpRequest(Method.GET, url))
      result <- EitherT(response.as[TokenWatchlist].map(toItems(config, client)).sequence).leftMap(err => new Throwable(err))
    } yield result
  }.toList.sequence.map(_.toSet.flatten)

  protected def getOthersWatchlist(config: PlexConfiguration, client: HttpClient): EitherT[IO, Throwable, Set[Item]] =
    for {
      friends <- getFriends(config, client)
      watchlistItems <- friends.map { case (friend, token) => getWatchlistIdsForUser(config, client, token)(friend) }.toList.sequence.map(_.flatten)
      items <- watchlistItems.map(i => toItems(config, client, i)).sequence.map(_.toSet)
    } yield items

  protected def getFriends(config: PlexConfiguration, client: HttpClient): EitherT[IO, Throwable, Set[(User, String)]] = config.plexTokens.map { token =>
    val url = Uri
      .unsafeFromString("https://community.plex.tv/api")

    val query = GraphQLQuery(
      """query GetAllFriends {
        |        allFriendsV2 {
        |          user {
        |            id
        |            username
        |          }
        |        }
        |      }""".stripMargin)

    EitherT(client.httpRequest(Method.POST, url, Some(token), Some(query.asJson)).map {
      case Left(err) =>
        logger.warn(s"Unable to fetch friends from Plex: $err")
        Left(err)
      case Right(json) =>
        json.as[Users] match {
          case Right(v) => Right(v.data.allFriendsV2.map(_.user).toSet).map(_.map(u => (u, token)))
          case Left(v) => Left(new Throwable(v))
        }
    })
  }.toList.sequence.map(_.toSet.flatten)

  protected def getWatchlistIdsForUser(config: PlexConfiguration, client: HttpClient, token: String)(user: User, page: Option[String] = None): EitherT[IO, Throwable, Set[TokenWatchlistItem]] = {
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
                        type
                      }""".stripMargin,
      if (page.isEmpty) {
        Some(
          s"""{
             |  "first": 100,
             |  "uuid": "${user.id}"
             |}""".stripMargin.asJson)
      } else {
        Some(
          s"""{
             |  "first": 100,
             |  "after": "${page.getOrElse("")}",
             |  "uuid": "${user.id}"
             |}""".stripMargin.asJson)
      })

    for {
      responseJson <- EitherT(client.httpRequest(Method.POST, url, Some(token), Some(query.asJson)))
      watchlist <- EitherT.fromEither[IO](responseJson.as[TokenWatchlistFriend]).leftMap(new Throwable(_))
      extraContent <- if (watchlist.data.user.watchlist.pageInfo.hasNextPage && watchlist.data.user.watchlist.pageInfo.endCursor.nonEmpty)
        getWatchlistIdsForUser(config, client, token)(user, watchlist.data.user.watchlist.pageInfo.endCursor)
      else
        EitherT.pure[IO, Throwable](Set.empty[TokenWatchlistItem])
    } yield watchlist.data.user.watchlist.nodes.map(_.toTokenWatchlistItem).toSet ++ extraContent
  }

  // We don't have all the information available in TokenWatchlist
  // so we need to make additional calls to Plex to get more information
  private def toItems(config: PlexConfiguration, client: HttpClient)(plex: TokenWatchlist): IO[Set[Item]] =
    plex.MediaContainer.Metadata.map(i => toItems(config, client, i).leftMap {
      err =>
        logger.warn(s"Found item ${i.title} on the watchlist, but we cannot find this in Plex's database.")
        err
    }
    ).foldLeft(IO.pure(Set.empty[Item])) { case (acc, eitherT) =>
      for {
        eitherItem <- eitherT.value
        itemsToAdd = eitherItem.map(Set(_)).getOrElse(Set.empty)
        accumulatedItems <- acc
      } yield accumulatedItems ++ itemsToAdd
    }

  private def toItems(config: PlexConfiguration, client: HttpClient, i: TokenWatchlistItem): EitherT[IO, Throwable, Item] = {

    val key = cleanKey(i.key)
    val url = Uri
      .unsafeFromString(s"https://discover.provider.plex.tv$key")
      .withQueryParam("X-Plex-Token", config.plexTokens.headOption.getOrElse("unknown"))

    val guids: EitherT[IO, Throwable, List[String]] = for {
      response <- EitherT(client.httpRequest(Method.GET, url))
      result <- EitherT(IO.pure(response.as[TokenWatchlist])).leftMap(err => new Throwable(err))
      guids = result.MediaContainer.Metadata.flatMap(_.Guid.map(_.id))
    } yield guids

    guids.map(ids => Item(i.title, ids, i.`type`, ended = None))
  }

  private def cleanKey(path: String): String =
    if (path.endsWith("/children")) path.dropRight(9) else path
}
