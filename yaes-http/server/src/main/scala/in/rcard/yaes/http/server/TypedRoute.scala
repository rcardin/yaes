package in.rcard.yaes.http.server


import in.rcard.yaes.*
/** Type-safe HTTP route.
  *
  * Combines an HTTP method, a typed path pattern, and a handler function. The type parameters
  * ensure compile-time verification that the handler receives exactly the parameters declared
  * in the pattern.
  *
  * @param method
  *   The HTTP method (GET, POST, etc.)
  * @param pattern
  *   The path pattern with type-level parameter encoding
  * @param handler
  *   The request handler with matching parameter signature
  * @tparam PathP
  *   The type-level encoding of path parameters
  * @tparam QueryP
  *   The type-level encoding of query parameters
  */
case class Route[PathP <: PathParams, QueryP <: QueryParams](
    method: Method,
    pattern: PathPattern[PathP, QueryP],
    handler: RouteHandler[PathP, QueryP]
) {

  /** Attempt to match and handle a request.
    *
    * If the method and path match this route, extracts parameters and invokes the handler.
    * [[PathParamError]]s and [[QueryParamError]]s during parameter extraction are automatically
    * converted to 400 Bad Request responses.
    *
    * @param request
    *   The HTTP request to match
    * @return
    *   Some(response) if this route matches, None otherwise
    */
  def matches(request: Request): Option[Response] = {
    if (request.method == method) {
      // Attempt to extract parameters with automatic error handling
      Raise.fold {
        pattern.extract(request) match {
          case Some((pathParams, query)) =>
            // Path and query params matched, invoke handler
            Some(handler.handle(request, pathParams, query))
          case None =>
            // Path structure didn't match
            None
        }
      } {
        // Convert parameter errors to 400 Bad Request
        case PathParamError.InvalidType(name, value, targetType) =>
          Some(
            Response.badRequest(
              s"Invalid path parameter '$name': expected $targetType, got '$value'"
            )
          )
        case PathParamError.MissingParam(name) =>
          Some(Response.badRequest(s"Missing required path parameter: $name"))
        case QueryParamError.InvalidType(name, value, targetType) =>
          Some(
            Response.badRequest(
              s"Invalid query parameter '$name': expected $targetType, got '$value'"
            )
          )
        case QueryParamError.MissingParam(name) =>
          Some(Response.badRequest(s"Missing required query parameter: $name"))
        case QueryParamError.InvalidFormat(message) =>
          Some(Response.badRequest(s"Invalid query string format: $message"))
      } { response => response }
    } else {
      None
    }
  }

  /** Get the path pattern string for debugging/display.
    *
    * Example: "GET /users/:userId/posts/:postId"
    */
  def toPattern: String = s"$method ${pattern.toPattern}"

  override def toString: String = s"Route($toPattern)"
}
