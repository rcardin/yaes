package in.rcard.yaes

/** Type-safe path pattern for route matching.
  *
  * Encodes the expected path structure and parameter types at compile time. When a request path
  * matches this pattern, it extracts typed parameter values.
  *
  * Example:
  * {{{
  * // Pattern: /users/:userId/posts/:postId
  * // Type: PathPattern["userId" :: Int :: "postId" :: Long :: NoParams, NoQueryParams]
  *
  * val pattern: PathPattern[...] = ...
  * pattern.extract(request) match {
  *   case Some((pathParams, queryParams)) => // params contains 123: Int and 456L: Long
  *   case None => // path didn't match
  * }
  * }}}
  *
  * @param root
  *   The first segment of the path
  * @param querySpec
  *   The query parameter specification
  * @tparam PathP
  *   The type-level encoding of path parameters in this pattern
  * @tparam QueryP
  *   The type-level encoding of query parameters in this pattern
  */
case class PathPattern[PathP <: PathParams, QueryP <: QueryParams](
    root: PathSegment[PathP],
    querySpec: QueryParamSpec[QueryP]
) {

  /** Extract typed parameter values from a request.
    *
    * Attempts to match the given request against this pattern. If successful, parses and returns the
    * typed parameter values for both path and query params. Parameter parsing uses the
    * [[PathParamParser]] and [[QueryParamParser]] typeclasses and raises errors on parsing failures.
    *
    * @param request
    *   The request to match
    * @return
    *   Some((pathParams, query)) if the pattern matches and all parameters parse successfully,
    *   None if the path structure doesn't match
    */
  def extract(request: Request): Option[(ParamValues[PathP], Query[QueryP])] raises PathParamError | QueryParamError = {
    val pathSegments = request.path.split("/").filter(_.nonEmpty).toList
    matchSegments(root, pathSegments) match {
      case Some(pathParams) =>
        val queryValues = extractQueryParams(querySpec, request.queryString)
        Some((pathParams, Query[QueryP](queryValues)))
      case None => None
    }
  }

  /** Extract query parameter values from query string. */
  private def extractQueryParams(
      spec: QueryParamSpec[?],
      queryString: Map[String, List[String]]
  ): Map[String, Any] raises QueryParamError = spec match {
    case EndOfQuery => Map.empty
    case SingleParam(name, parser, next) =>
      val values = queryString.getOrElse(name, List.empty)
      val parsed = parser.parse(name, values)
      extractQueryParams(next, queryString) + (name -> parsed)
  }

  private def matchSegments[P <: PathParams](
      segment: PathSegment[P],
      pathParts: List[String]
  ): Option[ParamValues[P]] raises PathParamError = {
    segment match {
      case End =>
        // End of pattern - path must also be exhausted
        if (pathParts.isEmpty) Some(NoParamValues.asInstanceOf[ParamValues[P]])
        else None

      case Literal(value, next) =>
        if (pathParts.nonEmpty && pathParts.head == value) {
          // Literal matches, continue with rest
          matchSegments(next, pathParts.tail)
        } else {
          // Literal doesn't match or path exhausted
          None
        }

      case param @ Param(name, parser, next) =>
        if (pathParts.nonEmpty) {
          // Parse the parameter value
          val parsedValue = parser.parse(name, pathParts.head)
          // Continue matching the rest
          matchSegments(next, pathParts.tail) match {
            case Some(tailValues) =>
              Some(
                ParamValueCons(parsedValue, tailValues)
                  .asInstanceOf[ParamValues[P]]
              )
            case None => None
          }
        } else {
          // Path exhausted but parameter expected
          None
        }
    }
  }

  /** Generate a pattern string for display/debugging.
    *
    * Example: `/users/:userId/posts/:postId?page:Int&limit:Int`
    */
  def toPattern: String = root.toPattern + querySpec.toPattern

  override def toString: String = s"PathPattern($toPattern)"
}
