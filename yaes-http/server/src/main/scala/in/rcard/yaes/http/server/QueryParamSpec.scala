package in.rcard.yaes.http.server

/** Runtime specification for query parameters.
  *
  * Similar to PathSegment for path parameters, QueryParamSpec represents the runtime structure of
  * query parameters expected by a route. It forms a linked chain that mirrors the type-level
  * QueryParams encoding.
  *
  * @tparam Params
  *   The type-level query parameter encoding
  */
sealed trait QueryParamSpec[+Params <: QueryParams] {
  /** Generate a debug-friendly pattern representation.
    *
    * Example: "?page:Int&limit:Int"
    */
  def toPattern: String
}

/** A single query parameter specification.
  *
  * Represents one named, typed parameter with a parser, followed by additional parameters.
  *
  * @param name
  *   The parameter name
  * @param parser
  *   The parser to convert string values to the target type
  * @param next
  *   The specification for remaining parameters
  * @tparam Name
  *   The parameter name as a singleton type
  * @tparam Type
  *   The parameter value type
  * @tparam Tail
  *   The remaining parameters
  */
case class SingleParam[Name <: String & Singleton, Type, Tail <: QueryParams](
    name: Name,
    parser: QueryParamParser[Type],
    next: QueryParamSpec[Tail]
) extends QueryParamSpec[QueryParam[Name, Type, Tail]] {
  def toPattern: String = {
    val typeName = extractTypeName(parser)
    val prefix = if (next == EndOfQuery) "?" else "&"
    val rest = next.toPattern
    s"$prefix$name:$typeName$rest"
  }

  private def extractTypeName(parser: QueryParamParser[?]): String = {
    // Simple heuristic to extract type name from parser class name
    // This is for debugging/logging purposes only
    parser.getClass.getName match {
      case s if s.contains("String")  => "String"
      case s if s.contains("Int")     => "Int"
      case s if s.contains("Long")    => "Long"
      case s if s.contains("Boolean") => "Boolean"
      case s if s.contains("Option")  => "Option"
      case s if s.contains("List")    => "List"
      case _                          => "?"
    }
  }
}

/** End-of-query marker.
  *
  * Represents the end of the query parameter chain (no more parameters).
  */
case object EndOfQuery extends QueryParamSpec[NoQueryParams] {
  def toPattern: String = ""
}
