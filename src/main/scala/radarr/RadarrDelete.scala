package radarr

private case class RadarrDelete(title: String, tmdbId: Long, qualityProfileId: Int = 6, rootFolderPath: String, addOptions: AddOptions = AddOptions())
