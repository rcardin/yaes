package in.rcard.yaes

/** Type-safe HTTP route.
  *
  * Combines an HTTP method, a typed path pattern, and a handler function. The type parameter
  * [[Params]] ensures compile-time verification that the handler receives exactly the parameters
  * declared in the pattern.
  *
  * @param method
  *   The HTTP method (GET, POST, etc.)
  * @param pattern
  *   The path pattern with type-level parameter encoding
  * @param handler
  *   The request handler with matching parameter signature
  * @tparam Params
  *   The type-level encoding of parameters
  */
case class Route[Params <: PathParams](
    method: Method,
    pattern: PathPattern[Params],
    handler: RouteHandler[Params]
) {

  /** Attempt to match and handle a request.
    *
    * If the method and path match this route, extracts parameters and invokes the handler.
    * [[PathParamError]]s during parameter extraction are automatically converted to 400 Bad Request
    * responses.
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
        pattern.extract(request.path) match {
          case Some(params) =>
            // Path matched, invoke handler
            Some(handler.handle(request, params))
          case None =>
            // Path structure didn't match
            None
        }
      } {
        // Convert PathParamError to 400 Bad Request
        case PathParamError.InvalidType(name, value, targetType) =>
          Some(
            Response.badRequest(
              s"Invalid path parameter '$name': expected $targetType, got '$value'"
            )
          )
        case PathParamError.MissingParam(name) =>
          Some(Response.badRequest(s"Missing required path parameter: $name"))
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
