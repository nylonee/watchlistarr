package radarr

import model.Item

private[radarr] trait RadarrConversions {
  def toItem(movie: RadarrMovie): Item = Item(
    movie.title,
    List(movie.imdbId, movie.tmdbId.map("tmdb://" + _)).collect { case Some(x) => x },
    "movie"
  )

  def toItem(movie: RadarrMovieExclusion): Item = toItem(RadarrMovie(movie.movieTitle, movie.imdbId, movie.tmdbId))
}
