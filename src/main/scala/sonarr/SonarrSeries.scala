package sonarr

private[sonarr] case class SonarrSeries(
    title: String,
    imdbId: Option[String],
    tvdbId: Option[Long],
    id: Long,
    ended: Option[Boolean]
)

private[sonarr] case class SonarrPagedSeries(
    page: Int,
    pageSize: Int,
    sortKey: String,
    sortDirection: String,
    totalRecords: Int,
    records: List[SonarrSeries]
)
