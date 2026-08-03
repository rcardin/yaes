package io.yaes.http.server.params.query

/** Runtime specification for query parameters.
  *
  * Similar to `PathSegment` for path parameters, `QueryParamSpec` represents the runtime structure
  * of query parameters expected by a route. It forms a linked chain in declaration order; the
  * type-level encoding of the parameters is tracked separately by the route builder as a named
  * tuple.
  */
sealed trait QueryParamSpec {
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
  */
case class SingleParam(
    name: String,
    parser: QueryParamParser[Any],
    next: QueryParamSpec
) extends QueryParamSpec {
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

/** End-of-query marker. Represents the end of the query parameter chain. */
case object EndOfQuery extends QueryParamSpec {
  def toPattern: String = ""
}
