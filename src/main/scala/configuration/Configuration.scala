package configuration

import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

case class Configuration(
    refreshInterval: FiniteDuration,
    sonarrConfiguration: SonarrConfiguration,
    radarrConfiguration: RadarrConfiguration,
    plexConfiguration: PlexConfiguration,
    deleteConfiguration: DeleteConfiguration
)

case class SonarrConfiguration(
    sonarrBaseUrl: Uri,
    sonarrApiKey: String,
    sonarrQualityProfileId: Int,
    sonarrRootFolder: String,
    sonarrBypassIgnored: Boolean,
    sonarrSeasonMonitoring: String,
    sonarrLanguageProfileId: Int,
    sonarrTagIds: Set[Int]
)

case class RadarrConfiguration(
    radarrBaseUrl: Uri,
    radarrApiKey: String,
    radarrQualityProfileId: Int,
    radarrRootFolder: String,
    radarrMinimumAvailability: String,
    radarrBypassIgnored: Boolean,
    radarrTagIds: Set[Int]
)

case class PlexConfiguration(
    plexWatchlistUrls: Set[Uri],
    plexTokens: Set[String],
    skipFriendSync: Boolean,
    hasPlexPass: Boolean
)

case class DeleteConfiguration(
    movieDeleting: Boolean,
    endedShowDeleting: Boolean,
    continuingShowDeleting: Boolean,
    deleteInterval: FiniteDuration,
    deleteFiles: Boolean
)
