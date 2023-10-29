package model

case class RadarrMovie(title: String, imdbId: Option[String], tmdbId: Option[Long])

case class RadarrMovieExclusion(movieTitle: String, imdbId: Option[String], tmdbId: Option[Long]) {
  def toRadarrMovie: RadarrMovie = RadarrMovie(this.movieTitle, this.imdbId, this.tmdbId)
}
