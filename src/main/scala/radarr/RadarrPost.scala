package radarr

private case class RadarrPost(
    title: String,
    tmdbId: Long,
    qualityProfileId: Int = 6,
    rootFolderPath: String,
    minimumAvailability: String,
    addOptions: AddOptions = AddOptions(),
    tags: List[Int] = List.empty[Int]
)
