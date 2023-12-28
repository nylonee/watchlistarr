package radarr

private[radarr] case class RadarrMovieExclusion(movieTitle: String, imdbId: Option[String], tmdbId: Option[Long], id: Long)
