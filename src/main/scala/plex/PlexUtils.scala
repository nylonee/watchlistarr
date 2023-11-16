package plex

import io.circe.generic.auto._
import cats.effect.IO
import http.HttpClient
import model.Item
import org.http4s.{Method, Uri}
import org.slf4j.LoggerFactory

trait PlexUtils {

  private val logger = LoggerFactory.getLogger(getClass)

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
}
