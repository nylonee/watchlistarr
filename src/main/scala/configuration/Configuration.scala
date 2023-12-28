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
                                sonarrLanguageProfileId: Int
                              )

case class RadarrConfiguration(
                                radarrBaseUrl: Uri,
                                radarrApiKey: String,
                                radarrQualityProfileId: Int,
                                radarrRootFolder: String,
                                radarrBypassIgnored: Boolean
                              )

case class PlexConfiguration(
                              plexWatchlistUrls: Set[Uri],
                              plexTokens: Set[String],
                              skipFriendSync: Boolean
                            )

case class DeleteConfiguration(
                                movieDeleting: Boolean,
                                endedShowDeleting: Boolean,
                                continuingShowDeleting: Boolean
                              )
