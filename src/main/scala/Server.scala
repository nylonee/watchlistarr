
import cats.effect._
import cats.implicits.catsSyntaxTuple3Parallel
import configuration.{Configuration, SystemPropertyReader}
import utils.HttpClient

import scala.concurrent.duration.DurationInt

object Server extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val configReader = SystemPropertyReader
    val httpClient = new HttpClient()
    val config = new Configuration(configReader, httpClient)(runtime)

    def periodicRssSync: IO[Unit] =
      WatchlistSync.run(config) >>
        IO.sleep(config.refreshInterval) >>
        periodicRssSync

    // We need to ping plex with the plex token once every 24 hours to renew the token expiry
    def periodicPlexTokenPing: IO[Unit] =
      PlexToken.ping(config) >>
        IO.sleep(24.hours) >>
        periodicPlexTokenPing

    def periodicPlexTokenWatchlistSync: IO[Unit] =
      PlexToken.getWatchlist(config) >>
        IO.sleep(24.seconds) >>
        periodicPlexTokenWatchlistSync

    (periodicRssSync, periodicPlexTokenPing, periodicPlexTokenWatchlistSync).parTupled.as(ExitCode.Success)
  }
}
