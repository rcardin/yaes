package in.rcard.yaes

/** Type-safe path pattern for route matching.
  *
  * Encodes the expected path structure and parameter types at compile time. When a request path
  * matches this pattern, it extracts typed parameter values.
  *
  * Example:
  * {{{
  * // Pattern: /users/:userId/posts/:postId
  * // Type: PathPattern["userId" :: Int :: "postId" :: Long :: NoParams]
  *
  * val pattern: PathPattern[...] = ...
  * pattern.extract("/users/123/posts/456") match {
  *   case Some(params) => // params contains 123: Int and 456L: Long
  *   case None => // path didn't match
  * }
  * }}}
  *
  * @param root
  *   The first segment of the path
  * @tparam Params
  *   The type-level encoding of all parameters in this pattern
  */
case class PathPattern[Params <: PathParams](root: PathSegment[Params]) {

  /** Extract typed parameter values from a request path.
    *
    * Attempts to match the given path against this pattern. If successful, parses and returns the
    * typed parameter values. Parameter parsing uses the [[PathParamParser]] typeclass and raises
    * [[PathParamError]] on parsing failures.
    *
    * @param path
    *   The request path to match (e.g., "/users/123/posts/456")
    * @return
    *   Some(params) if the path matches and all parameters parse successfully, None if the path
    *   structure doesn't match
    */
  def extract(path: String): Option[ParamValues[Params]] raises PathParamError = {
    val segments = path.split("/").filter(_.nonEmpty).toList
    matchSegments(root, segments)
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
    * Example: `/users/:userId/posts/:postId`
    */
  def toPattern: String = root.toPattern

  override def toString: String = s"PathPattern($toPattern)"
}
