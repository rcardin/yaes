package in.rcard.yaes

/** Type-safe path segment representation.
  *
  * Path segments can be either literal strings (must match exactly) or typed parameters (capture
  * values). The type parameter [[Params]] encodes what parameters remain to be parsed.
  *
  * @tparam Params
  *   The parameters this segment and its successors will extract
  */
sealed trait PathSegment[+Params <: PathParams] {
  /** Generate the pattern string for debugging/display purposes. */
  def toPattern: String
}

/** Literal path segment.
  *
  * Matches an exact string value in the path.
  *
  * Example: In `/users/posts`, both "users" and "posts" are literals.
  *
  * @param value
  *   The exact string that must match
  * @param next
  *   The next segment in the path
  * @tparam Params
  *   The parameters extracted by this segment and its successors
  */
case class Literal[Params <: PathParams](value: String, next: PathSegment[Params])
    extends PathSegment[Params] {
  def toPattern: String = s"/$value${next.toPattern}"
}

/** Parameter path segment.
  *
  * Matches any value and captures it with the given name and type.
  *
  * Example: In `/users/:id`, the `:id` part is a parameter.
  *
  * @param name
  *   The parameter name (as a singleton type)
  * @param parser
  *   The parser to convert the string value to the target type
  * @param next
  *   The next segment in the path
  * @tparam Name
  *   The parameter name (singleton string type)
  * @tparam Type
  *   The parameter value type
  * @tparam Tail
  *   The remaining parameters to be extracted
  */
case class Param[Name <: String & Singleton, Type, Tail <: PathParams](
    name: Name,
    parser: PathParamParser[Type],
    next: PathSegment[Tail]
) extends PathSegment[::[Name, Type, Tail]] {
  def toPattern: String = s"/:$name${next.toPattern}"
}

/** End of path marker.
  *
  * Represents the end of a path pattern with no remaining parameters.
  */
case object End extends PathSegment[NoParams] {
  def toPattern: String = ""
}
