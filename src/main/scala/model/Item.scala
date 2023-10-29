package model

case class Item(title: String, guids: List[String], category: String)

case class GraphQLItem(id: String, title: String, publicPagesURL: String, `type`: String) {
  def toItem: Item = Item(this.title, List.empty, this.`type`.toLowerCase)
}

case class MetadataItem(title: String, `type`: String) {
  def toItem: Item = Item(this.title, List.empty, this.`type`)
}
