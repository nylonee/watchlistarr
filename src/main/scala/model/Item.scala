package model

case class Item(title: String, guids: List[String], category: String) {
  def getTvdbId: Option[Long] =
    guids.find(_.startsWith("tvdb://")).flatMap(_.stripPrefix("tvdb://").toLongOption)

  def getTmdbId: Option[Long] =
    guids.find(_.startsWith("tmdb://")).flatMap(_.stripPrefix("tmdb://").toLongOption)

  def matches(that: Any): Boolean = that match {
    case Item(_, theirGuids, c) if c == this.category =>
      theirGuids.foldLeft(false) {
        case (acc, guid) => acc || guids.contains(guid)
      }
    case _ => false
  }
}
