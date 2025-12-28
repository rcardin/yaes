package in.rcard.yaes

/** Represents a segment in a path pattern.
  *
  * Path segments are either literal strings that must match exactly, or parameters that match any
  * value and capture it.
  */
sealed trait PathSegment

/** A literal path segment that must match exactly.
  *
  * Example: In the pattern "/users/admin", both "users" and "admin" are literals.
  *
  * @param value
  *   The exact string that must match
  */
case class Literal(value: String) extends PathSegment

/** A parameter path segment that matches any value and captures it.
  *
  * Example: In the pattern "/users/:id", ":id" becomes Parameter("id").
  *
  * @param name
  *   The name of the parameter (without the ':' prefix)
  */
case class Parameter(name: String) extends PathSegment

/** Represents a parsed path pattern with support for literal and parameter segments.
  *
  * PathPattern is used for matching incoming request paths and extracting parameter values. It
  * supports colon-style parameter syntax (e.g., "/users/:id/posts/:postId").
  *
  * Example:
  * {{{
  * val pattern = PathPattern.parse("/users/:id/posts/:postId")
  * pattern.matches("/users/123/posts/456")
  * // Returns: Some(Map("id" -> "123", "postId" -> "456"))
  *
  * pattern.matches("/users/123")
  * // Returns: None (wrong number of segments)
  * }}}
  *
  * @param segments
  *   The list of path segments (literals and parameters)
  */
case class PathPattern(segments: List[PathSegment]) {

  /** Matches a request path against this pattern and extracts parameter values.
    *
    * The matching algorithm:
    *   1. Splits the path into segments (ignoring empty segments from leading/trailing slashes)
    *   1. Checks that segment counts match
    *   1. For each segment:
    *      - Literal segments must match exactly
    *      - Parameter segments match any value and capture it
    *
    * @param path
    *   The request path to match (e.g., "/users/123/posts/456")
    * @return
    *   Some(params) if the path matches, with a map of parameter names to values; None if the path
    *   doesn't match
    */
  def matches(path: String): Option[Map[String, String]] = {
    val pathSegments = path.split("/").filter(_.nonEmpty)

    if (pathSegments.length != segments.length) {
      None
    } else {
      val params = scala.collection.mutable.Map[String, String]()
      var matched = true

      segments.zip(pathSegments).foreach {
        case (Literal(expected), actual) if expected != actual =>
          matched = false
        case (Literal(_), _) =>
        // Match, continue
        case (Parameter(name), value) =>
          params(name) = value
      }

      if (matched) Some(params.toMap) else None
    }
  }

  /** Checks if this pattern contains only literal segments (no parameters).
    *
    * Exact patterns are used for priority matching - they should be checked before parameterized
    * patterns.
    *
    * @return
    *   true if all segments are literals, false if any segment is a parameter
    */
  def isExact: Boolean = segments.forall(_.isInstanceOf[Literal])
}

object PathPattern {
  /** Parses a path pattern string into a PathPattern.
    *
    * Segments starting with ':' are treated as parameters, all others as literals. The path is
    * split on '/' and empty segments (from leading/trailing slashes) are filtered out.
    *
    * Pattern validation:
    *   - Parameter names cannot be empty (e.g., `/:` is invalid)
    *   - Parameter names must be unique within the pattern
    *
    * Example:
    * {{{
    * PathPattern.parse("/users/:id")
    * // Returns: PathPattern(List(Literal("users"), Parameter("id")))
    *
    * PathPattern.parse("/users/:userId/posts/:postId")
    * // Returns: PathPattern(List(
    * //   Literal("users"), Parameter("userId"),
    * //   Literal("posts"), Parameter("postId")
    * // ))
    *
    * PathPattern.parse("/users/:") // throws IllegalArgumentException
    * PathPattern.parse("/users/:id/posts/:id") // throws IllegalArgumentException
    * }}}
    *
    * @param pattern
    *   The path pattern string (e.g., "/users/:id/posts/:postId")
    * @return
    *   A parsed PathPattern ready for matching
    * @throws IllegalArgumentException
    *   if the pattern is invalid (empty parameter names or duplicate parameter names)
    */
  def parse(pattern: String): PathPattern = {
    val segments = pattern.split("/").filter(_.nonEmpty).map {
      case s if s.startsWith(":") => Parameter(s.drop(1))
      case s                      => Literal(s)
    }.toList

    // Validate: no empty parameter names
    segments.foreach {
      case Parameter("") =>
        throw new IllegalArgumentException(s"Invalid path pattern '$pattern': parameter name cannot be empty")
      case _ => // OK
    }

    // Validate: no duplicate parameter names
    val paramNames = segments.collect { case Parameter(name) => name }
    val duplicates = paramNames.groupBy(identity).filter(_._2.size > 1).keys
    if (duplicates.nonEmpty) {
      throw new IllegalArgumentException(
        s"Invalid path pattern '$pattern': duplicate parameter names: ${duplicates.mkString(", ")}"
      )
    }

    PathPattern(segments)
  }
}
