package sonarr

import model.Item

private[sonarr] trait SonarrConversions {
  def toItem(series: SonarrSeries): Item = Item(
    series.title,
    List(series.imdbId, series.tvdbId.map("tvdb://" + _), Some(s"sonarr://${series.id}")).collect { case Some(x) => x },
    "show",
    series.ended
  )
}
