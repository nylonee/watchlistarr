package plex

private[plex] case class TokenWatchlistItem(title: String, guid: String, `type`: String, key: String, Guid: List[Guid] = List.empty)

private[plex] case class Guid(id: String)