package sonarr

private[sonarr] case class SonarrAddOptions(monitor: String, searchForCutoffUnmetEpisodes: Boolean = true, searchForMissingEpisodes: Boolean = true)
