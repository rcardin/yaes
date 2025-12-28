package in.rcard.yaes

/** Routes HTTP requests to handlers based on method and path.
  *
  * The Router supports both exact path matching and parameterized paths with type-safe parameter
  * extraction. It maintains two types of routes:
  *   - Exact routes: Fast O(1) Map lookup for literal paths
  *   - Parameterized routes: Pattern-based matching for paths with parameters (e.g., "/users/:id")
  *
  * Exact routes are checked first for performance, then parameterized routes are scanned in
  * definition order.
  *
  * Example:
  * {{{
  * val router = Router(
  *   (Method.GET, "/hello", (req: Request) => Response.ok("Hello!")),
  *   (Method.GET, "/users/:id", (req: Request) => {
  *     val userId = req.pathParam[Int]("id")
  *     Response.ok(s"User $userId")
  *   })
  * )
  *
  * router.handle(Request(Method.GET, "/hello", Map.empty, ""))
  * // Returns: Response(200, ..., "Hello!")
  *
  * router.handle(Request(Method.GET, "/users/123", Map.empty, ""))
  * // Returns: Response(200, ..., "User 123")
  * }}}
  *
  * @param exactRoutes
  *   Map of (method, exact path) to handler functions
  * @param patternRoutes
  *   List of (method, path pattern, handler) for parameterized routes
  */
class Router(
    exactRoutes: Map[(Method, String), Request => Response],
    patternRoutes: List[(Method, PathPattern, Request => Response)]
) {

  /** Handles an incoming HTTP request by routing it to the appropriate handler.
    *
    * The routing algorithm:
    *   1. Try exact match first (O(1) Map lookup)
    *   1. If no exact match, try parameterized routes in order (O(n) scan)
    *   1. If no match found, return 404
    *
    * When a parameterized route matches, the extracted parameters are injected into the Request
    * before calling the handler.
    *
    * @param request
    *   The incoming HTTP request
    * @return
    *   The HTTP response from the matched handler, or 404 if no match
    */
  def handle(request: Request): Response = {
    // Try exact match first (fast path)
    exactRoutes.get((request.method, request.path)) match {
      case Some(handler) => handler(request)
      case None          => handlePatternMatch(request)
    }
  }

  /** Handles pattern-based routing for parameterized paths.
    *
    * Scans parameterized routes in definition order and returns the first match. When a pattern
    * matches, extracts parameters and injects them into the Request before calling the handler.
    *
    * @param request
    *   The incoming HTTP request
    * @return
    *   The HTTP response from the matched handler, or 404 if no match
    */
  private def handlePatternMatch(request: Request): Response = {
    patternRoutes
      .find { case (method, pattern, _) =>
        method == request.method && pattern.matches(request.path).isDefined
      }
      .map { case (_, pattern, handler) =>
        val params = pattern.matches(request.path).get
        handler(request.copy(pathParams = params))
      }
      .getOrElse(Response.notFound("Not Found"))
  }
}

object Router {

  /** Creates a Router from a variable number of route definitions.
    *
    * Routes are automatically partitioned into exact and parameterized based on whether the path
    * contains parameter syntax (':'). Exact routes are stored in a Map for O(1) lookup, while
    * parameterized routes are kept in a List for pattern matching.
    *
    * Each route is defined as a triple of (Method, path, handler).
    *
    * Example:
    * {{{
    * val router = Router(
    *   (Method.GET, "/users", getUsersHandler),              // exact route
    *   (Method.GET, "/users/:id", getUserByIdHandler),       // parameterized route
    *   (Method.POST, "/users", createUserHandler)            // exact route
    * )
    * }}}
    *
    * @param routes
    *   Variable argument list of (Method, path, handler) triples
    * @return
    *   A Router instance configured with the given routes
    */
  def apply(routes: (Method, String, Request => Response)*): Router = {
    // Partition routes into exact and parameterized based on ':' presence
    val (exact, patterns) = routes.partition { case (_, path, _) => !path.contains(":") }

    // Build exact route map for fast lookup
    val exactMap = exact.map { case (method, path, handler) =>
      (method, path) -> handler
    }.toMap

    // Build parameterized route list with parsed patterns
    val patternList = patterns.map { case (method, path, handler) =>
      (method, PathPattern.parse(path), handler)
    }.toList

    new Router(exactMap, patternList)
  }
}
