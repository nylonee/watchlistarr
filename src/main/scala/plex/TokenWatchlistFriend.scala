package plex

private[plex] case class TokenWatchlistFriend(data: WatchlistData)

private[plex] case class WatchlistData(user: WatchlistUserData)

private[plex] case class WatchlistUserData(watchlist: WatchlistNodes)

private[plex] case class WatchlistNodes(nodes: List[WatchlistNode], pageInfo: PageInfo)

private[plex] case class PageInfo(hasNextPage: Boolean, endCursor: String)

private[plex] case class WatchlistNode(id: String, title: String, `type`: String) {
  def toTokenWatchlistItem: TokenWatchlistItem =
    TokenWatchlistItem(title = title, guid = id, key = s"/library/metadata/$id", `type` = `type`.toLowerCase)
}
