package in.rcard.yaes

/** Router definition containing a collection of routes.
  *
  * Handles incoming requests by matching them against registered routes. Routes are partitioned for
  * efficient matching: exact routes (no parameters) are stored in a map for O(1) lookup,
  * parameterized routes are checked sequentially.
  *
  * @param exactRoutes
  *   Routes with no parameters, indexed by (Method, path) for fast lookup
  * @param paramRoutes
  *   Routes with parameters, checked in order
  */
case class Routes(
    exactRoutes: Map[(Method, String), Request => Response],
    paramRoutes: List[Route[?, ?]]
) {

  /** Handle an incoming request.
    *
    * Attempts to match the request against registered routes. Exact routes are checked first for
    * performance, then parameterized routes are tried in order. Returns 404 if no route matches.
    *
    * @param request
    *   The incoming HTTP request
    * @return
    *   The HTTP response
    */
  def handle(request: Request): Response = {
    // Try exact match first (fast path)
    exactRoutes.get((request.method, request.path)) match {
      case Some(handler) =>
        handler(request)
      case None =>
        // Try parameterized routes in order
        paramRoutes.view
          .map(_.matches(request))
          .collectFirst { case Some(response) => response }
          .getOrElse(Response.notFound(s"No route found for ${request.method} ${request.path}"))
    }
  }
}

object Routes {

  /** Create a router from a collection of routes.
    *
    * Partitions routes into exact (no parameters) and parameterized for efficient matching.
    *
    * Example:
    * {{{
    * val userId = param[Int]("userId")
    * val postId = param[Long]("postId")
    *
    * val routes = Routes(
    *   GET(p"/health") { req =>
    *     Response.ok("OK")
    *   },
    *   GET(p"/users" / userId) { (req, id: Int) =>
    *     Response.ok(s"User $id")
    *   },
    *   GET(p"/users" / userId / "posts" / postId) { (req, uid: Int, pid: Long) =>
    *     Response.ok(s"User $uid, Post $pid")
    *   }
    * )
    * }}}
    *
    * @param routes
    *   Variable argument list of Route instances
    * @return
    *   A Routes instance ready to handle requests
    */
  def apply(routes: Route[?, ?]*): Routes = {
    // Partition routes into exact and parameterized
    // Exact routes have no path params AND no query params
    val (exact, parameterized) = routes.partition { route =>
      isExactRoute(route.pattern.root) && isNoQueryParams(route.pattern.querySpec)
    }

    // Build exact routes map
    val exactMap = exact.map { route =>
      val path = extractExactPath(route.pattern.root)
      (route.method, path) -> ((req: Request) => {
        // For exact routes, we know PathParams is NoParams and QueryParams is NoQueryParams
        val handler = route.handler.asInstanceOf[RouteHandler[NoParams, NoQueryParams]]
        handler.handle(req, NoParamValues, Query[NoQueryParams](Map.empty))
      })
    }.toMap

    Routes(exactMap, parameterized.toList)
  }

  /** Check if a path segment represents an exact route (no path parameters). */
  private def isExactRoute(segment: PathSegment[?]): Boolean = segment match {
    case End              => true
    case Literal(_, next) => isExactRoute(next)
    case Param(_, _, _)   => false
  }

  /** Check if a query spec has no query parameters. */
  private def isNoQueryParams(spec: QueryParamSpec[?]): Boolean = spec match {
    case EndOfQuery       => true
    case SingleParam(_, _, _) => false
  }

  /** Extract the exact path string from a literal-only route. */
  private def extractExactPath(segment: PathSegment[?]): String = {
    def loop(segment: PathSegment[?], acc: String): String = segment match {
      case End => acc
      case Literal(value, next) => loop(next, s"$acc/$value")
      case Param(_, _, _) => throw new IllegalArgumentException("Cannot extract exact path from parameterized route")
    }

    val path = loop(segment, "")
    if (path.isEmpty) "/" else path  // Root path special case
  }
}
