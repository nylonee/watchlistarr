import PlexToken.{jsonToUserList, selfWatchlistToItems}
import io.circe.Json
import io.circe.syntax.EncoderOps
import model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._

import scala.io.Source

class PlexTokenSpec extends AnyFlatSpec with Matchers {

  "PlexTokenSpec" should "correctly deserialize users" in {
    val jsonStr = Source.fromResource("users.json").getLines().mkString("\n")

    val decodedUsers = jsonToUserList(parse(jsonStr).getOrElse(Json.Null))

    decodedUsers.isEmpty shouldBe false
  }

  it should "correctly deserialize my own watchlist" in {
    val jsonStr = Source.fromResource("watchlist-from-token.json").getLines().mkString("\n")

    val decodedItems = selfWatchlistToItems(parse(jsonStr).getOrElse(Json.Null))

    decodedItems.isEmpty shouldBe false
  }
}
