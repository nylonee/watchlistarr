package plex

case class RssFeedGenerated(RSSInfo: List[RssInfo])

case class RssInfo(url: String)
