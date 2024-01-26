package model

sealed trait ThirdPartyType
object Plex extends ThirdPartyType
object Sonarr extends ThirdPartyType
object Radarr extends ThirdPartyType
