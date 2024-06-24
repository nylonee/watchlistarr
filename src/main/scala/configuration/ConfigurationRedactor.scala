package configuration

object ConfigurationRedactor {
  def redactToString(config: Configuration): String =
    s"""
      |Configuration:
      |  refreshInterval: ${config.refreshInterval.toSeconds} seconds
      |
      |  SonarrConfiguration:
      |    sonarrBaseUrl: ${config.sonarrConfiguration.sonarrBaseUrl}
      |    sonarrApiKey: REDACTED
      |    sonarrQualityProfileId: ${config.sonarrConfiguration.sonarrQualityProfileId}
      |    sonarrRootFolder: ${config.sonarrConfiguration.sonarrRootFolder}
      |    sonarrBypassIgnored: ${config.sonarrConfiguration.sonarrBypassIgnored}
      |    sonarrLanguageProfileId: ${config.sonarrConfiguration.sonarrLanguageProfileId}
      |    sonarrTagIds: ${config.sonarrConfiguration.sonarrTagIds.mkString(",")}
      |
      |  RadarrConfiguration:
      |    radarrBaseUrl: ${config.radarrConfiguration.radarrBaseUrl}
      |    radarrApiKey: REDACTED
      |    radarrQualityProfileId: ${config.radarrConfiguration.radarrQualityProfileId}
      |    radarrRootFolder: ${config.radarrConfiguration.radarrRootFolder}
      |    radarrMinimumAvailability: ${config.radarrConfiguration.radarrMinimumAvailability}
      |    radarrBypassIgnored: ${config.radarrConfiguration.radarrBypassIgnored}
      |    radarrTagIds: ${config.radarrConfiguration.radarrTagIds.mkString(",")}
      |
      |  PlexConfiguration:
      |    plexWatchlistUrls: ${config.plexConfiguration.plexWatchlistUrls.mkString(", ")}
      |    plexTokens: ${config.plexConfiguration.plexTokens.map(_ => "REDACTED").mkString(", ")}
      |    skipFriendSync: ${config.plexConfiguration.skipFriendSync}
      |    hasPlexPass: ${config.plexConfiguration.hasPlexPass}
      |
      |  DeleteConfiguration:
      |    movieDeleting: ${config.deleteConfiguration.movieDeleting}
      |    endedShowDeleting: ${config.deleteConfiguration.endedShowDeleting}
      |    continuingShowDeleting: ${config.deleteConfiguration.continuingShowDeleting}
      |    deleteInterval: ${config.deleteConfiguration.deleteInterval.toDays} days
      |    deleteFiles: ${config.deleteConfiguration.deleteFiles}
      |
      |""".stripMargin
}
