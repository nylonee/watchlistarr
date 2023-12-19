package sonarr

private[sonarr] case class SonarrPost(
                                       title: String,
                                       tvdbId: Long,
                                       qualityProfileId: Int,
                                       rootFolderPath: String,
                                       addOptions: SonarrAddOptions,
                                       languageProfileId: Int,
                                       monitored: Boolean = true
                                     )
