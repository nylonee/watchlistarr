package radarr

private[radarr] case class RadarrMovie(title: String, imdbId: Option[String], tmdbId: Option[Long], id: Long)
