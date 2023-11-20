package plex

private[plex] case class Users(data: Data)

private[plex] case class Data(allFriendsV2: List[FriendV2])

private[plex] case class FriendV2(user: User)

private[plex] case class User(id: String, username: String)
