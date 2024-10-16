package configuration

private[configuration] object Keys {
  val intervalSeconds = "interval.seconds"

  val sonarrBaseUrl          = "sonarr.baseUrl"
  val sonarrApiKey           = "sonarr.apikey"
  val sonarrQualityProfile   = "sonarr.qualityProfile"
  val sonarrRootFolder       = "sonarr.rootFolder"
  val sonarrBypassIgnored    = "sonarr.bypassIgnored"
  val sonarrSeasonMonitoring = "sonarr.seasonMonitoring"
  val sonarrTags             = "sonarr.tags"

  val radarrBaseUrl             = "radarr.baseUrl"
  val radarrApiKey              = "radarr.apikey"
  val radarrQualityProfile      = "radarr.qualityProfile"
  val radarrRootFolder          = "radarr.rootFolder"
  val radarrMinimumAvailability = "radarr.minimumAvailability"
  val radarrBypassIgnored       = "radarr.bypassIgnored"
  val radarrTags                = "radarr.tags"

  val plexWatchlist1 = "plex.watchlist1"
  val plexWatchlist2 = "plex.watchlist2"
  val plexToken      = "plex.token"
  val skipFriendSync = "plex.skipfriendsync"

  val deleteIntervalDays   = "delete.interval.days"
  val deleteMovies         = "delete.movie"
  val deleteEndedShow      = "delete.endedShow"
  val deleteContinuingShow = "delete.continuingShow"
  val deleteFiles          = "delete.deleteFiles"
}
