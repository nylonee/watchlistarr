package radarr

private case class RadarrPost(title: String, tmdbId: Long, qualityProfileId: Int = 6, rootFolderPath: String, addOptions: AddOptions = AddOptions())
