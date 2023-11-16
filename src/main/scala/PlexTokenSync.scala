import cats.effect.IO
import configuration.Configuration
import http.HttpClient
import plex.PlexUtils

object PlexTokenSync extends PlexUtils {

  def run(config: Configuration, client: HttpClient): IO[Unit] = ping(client)(config)
}
