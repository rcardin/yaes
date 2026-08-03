package io.yaes.http.server

import io.yaes.*
import io.yaes.http.core.Method
import io.yaes.http.server.routing.*
import io.yaes.http.server.params.path.*
import io.yaes.http.server.params.query.*
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
    * When several routes are registered for the same method and path, the first declared one wins,
    * for both exact and parameterized routes.
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
    *   GET(p"/users" / userId) { (req, path, _) =>
    *     Response.ok(s"User ${path.userId}")
    *   },
    *   GET(p"/users" / userId / "posts" / postId) { (req, path, _) =>
    *     Response.ok(s"User ${path.userId}, Post ${path.postId}")
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
    val exactMap = exact.foldLeft(Map.empty[(Method, String), Request => Response]) { (acc, route) =>
      val key = (route.method, extractExactPath(route.pattern.root))
      // First declaration wins, matching the first-match order used for paramRoutes below.
      if (acc.contains(key)) acc
      else
        acc.updated(key, (req: Request) => {
          val empty = EmptyTuple.asInstanceOf[io.yaes.http.server.params.EmptyParams]
          val handler = route.handler.asInstanceOf[(Request, io.yaes.http.server.params.EmptyParams, io.yaes.http.server.params.EmptyParams) => Response]
          handler(req, empty, empty)
        })
    }

    Routes(exactMap, parameterized.toList)
  }

  /** Check if a path segment represents an exact route (no path parameters). */
  private def isExactRoute(segment: PathSegment): Boolean = segment match {
    case End              => true
    case Literal(_, next) => isExactRoute(next)
    case Param(_, _, _)   => false
  }

  /** Check if a query spec has no query parameters. */
  private def isNoQueryParams(spec: QueryParamSpec): Boolean = spec match {
    case EndOfQuery       => true
    case SingleParam(_, _, _) => false
  }

  /** Extract the exact path string from a literal-only route. */
  private def extractExactPath(segment: PathSegment): String = {
    def loop(segment: PathSegment, acc: String): String = segment match {
      case End => acc
      case Literal(value, next) => loop(next, s"$acc/$value")
      case Param(_, _, _) => throw new IllegalArgumentException("Cannot extract exact path from parameterized route")
    }

    val path = loop(segment, "")
    if (path.isEmpty) "/" else path  // Root path special case
  }
}
