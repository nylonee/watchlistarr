package radarr

import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json, JsonObject}

case class RadarrPost(
    title: String,
    tmdbId: Long,
    qualityProfileId: Int = 6,
    rootFolderPath: String,
    minimumAvailability: Option[String] = None,
    tags: List[Int] = List.empty[Int],
    addOptions: AddOptions = AddOptions()
)

object RadarrPost {
  implicit val encoder: Encoder[RadarrPost] = Encoder.instance { post =>
    var fields = List[(String, Json)](
      "title"            -> post.title.asJson,
      "tmdbId"           -> post.tmdbId.asJson,
      "qualityProfileId" -> post.qualityProfileId.asJson,
      "rootFolderPath"   -> post.rootFolderPath.asJson,
      "tags"             -> post.tags.asJson
    )
    post.minimumAvailability.foreach { avail =>
      fields = fields :+ ("minimumAvailability" -> avail.asJson)
    }
    fields = fields :+ ("addOptions" -> post.addOptions.asJson)
    Json.fromJsonObject(JsonObject.fromIterable(fields))
  }
}
