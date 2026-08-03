package io.yaes.http.server.params.path

/** Runtime path segment representation.
  *
  * A path pattern is a chain of segments, each either a literal string (which must match exactly)
  * or a typed parameter (which captures and parses a value). The type-level encoding of the
  * captured parameters is tracked by the route builder as a named tuple; the segment chain itself
  * is untyped runtime structure.
  */
sealed trait PathSegment {
  /** Generate the pattern string for debugging/display purposes. */
  def toPattern: String
}

/** Literal path segment.
  *
  * Matches an exact string value in the path. Example: In `/users/posts`, both "users" and "posts"
  * are literals.
  *
  * @param value
  *   The exact string that must match
  * @param next
  *   The next segment in the path
  */
case class Literal(value: String, next: PathSegment) extends PathSegment {
  def toPattern: String = s"/$value${next.toPattern}"
}

/** Parameter path segment.
  *
  * Matches any value and captures it with the given name and type. Example: In `/users/:id`, the
  * `:id` part is a parameter.
  *
  * @param name
  *   The parameter name
  * @param parser
  *   The parser to convert the string value to the target type
  * @param next
  *   The next segment in the path
  */
case class Param(name: String, parser: PathParamParser[Any], next: PathSegment) extends PathSegment {
  def toPattern: String = s"/:$name${next.toPattern}"
}

/** End of path marker. Represents the end of a path pattern. */
case object End extends PathSegment {
  def toPattern: String = ""
}
