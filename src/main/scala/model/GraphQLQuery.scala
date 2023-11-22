package model

import io.circe.Json

case class GraphQLQuery(query: String, variables: Option[Json] = None)
