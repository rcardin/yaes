package io.yaes.http.server.routing

import io.yaes.*
import io.yaes.http.server.Request
import io.yaes.http.server.params.path.{PathSegment, End, Literal, Param, PathParamError}
import io.yaes.http.server.params.query.{QueryParamSpec, EndOfQuery, SingleParam, QueryParamError}
import scala.NamedTuple.AnyNamedTuple

/** Type-safe path pattern for route matching.
  *
  * Encodes the expected path structure and parameter types at compile time. When a request path
  * matches this pattern, it extracts typed parameter values as named tuples.
  *
  * Example:
  * {{{
  * // Pattern: /users/:userId/posts/:postId
  * // Type: PathPattern[(userId: Int, postId: Long), EmptyParams]
  *
  * val pattern: PathPattern[...] = ...
  * pattern.extract(request) match {
  *   case Some((path, query)) => // path.userId: Int, path.postId: Long
  *   case None                => // path didn't match
  * }
  * }}}
  *
  * @param root
  *   The first segment of the path
  * @param querySpec
  *   The query parameter specification
  * @tparam PathP
  *   The named-tuple encoding of path parameters in this pattern
  * @tparam QueryP
  *   The named-tuple encoding of query parameters in this pattern
  */
case class PathPattern[PathP <: AnyNamedTuple, QueryP <: AnyNamedTuple] private[server](
    root: PathSegment,
    querySpec: QueryParamSpec
) {

  /** Extract typed parameter values from a request.
    *
    * Attempts to match the given request against this pattern. If successful, parses and returns the
    * typed parameter values for both path and query params as named tuples. Parameter parsing uses
    * the [[io.yaes.http.server.params.path.PathParamParser]] and
    * [[io.yaes.http.server.params.query.QueryParamParser]] typeclasses and raises errors on parsing
    * failures.
    *
    * @param request
    *   The request to match
    * @return
    *   Some((path, query)) if the pattern matches and all parameters parse successfully, None if the
    *   path structure doesn't match
    */
  def extract(request: Request): Option[(PathP, QueryP)] raises PathParamError | QueryParamError = {
    val pathSegments = request.path.split("/").filter(_.nonEmpty).toList
    matchSegments(root, pathSegments) match {
      case Some(pathValues) =>
        val queryValues = extractQueryParams(querySpec, request.queryString)
        Some((pathValues.asInstanceOf[PathP], queryValues.asInstanceOf[QueryP]))
      case None => None
    }
  }

  /** Extract query parameter values from the query string, in declaration order.
    *
    * The resulting tuple's element order mirrors the spec chain, which the route builder keeps in
    * lockstep with the `QueryP` named-tuple name order, so the tuple can be viewed as `QueryP`.
    */
  private def extractQueryParams(
      spec: QueryParamSpec,
      queryString: Map[String, List[String]]
  ): Tuple raises QueryParamError = spec match {
    case EndOfQuery => EmptyTuple
    case SingleParam(name, parser, next) =>
      val values = queryString.getOrElse(name, List.empty)
      val parsed = parser.parse(name, values)
      parsed *: extractQueryParams(next, queryString)
  }

  /** Match the path segments against the request path, building the captured values in order. */
  private def matchSegments(
      segment: PathSegment,
      pathParts: List[String]
  ): Option[Tuple] raises PathParamError = {
    segment match {
      case End =>
        // End of pattern - path must also be exhausted
        if (pathParts.isEmpty) Some(EmptyTuple)
        else None

      case Literal(value, next) =>
        if (pathParts.nonEmpty && pathParts.head == value) {
          // Literal matches, continue with rest
          matchSegments(next, pathParts.tail)
        } else {
          // Literal doesn't match or path exhausted
          None
        }

      case Param(name, parser, next) =>
        if (pathParts.nonEmpty) {
          // Parse the parameter value
          val parsedValue = parser.parse(name, pathParts.head)
          // Continue matching the rest, prepending this value in position order
          matchSegments(next, pathParts.tail) match {
            case Some(tailValues) => Some(parsedValue *: tailValues)
            case None             => None
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
