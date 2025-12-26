package in.rcard.yaes

/** Routes HTTP requests to handlers based on method and path.
  *
  * The Router maintains a mapping of (HTTP method, path) to handler functions and dispatches
  * incoming requests to the appropriate handler.
  *
  * Example:
  * {{{
  * val router = Router(
  *   (Method.GET, "/hello", (req: Request) => Response.ok("Hello!")),
  *   (Method.POST, "/echo", (req: Request) => Response.ok(req.body))
  * )
  *
  * val response = router.handle(
  *   Request(Method.GET, "/hello", Map.empty, "")
  * )
  * // response.status == 200
  * }}}
  *
  * @param routes
  *   Map of (method, path) tuples to handler functions
  */
class Router(routes: Map[(Method, String), Request => Response]) {

  /** Handles an incoming HTTP request by routing it to the appropriate handler.
    *
    * If no matching route is found, returns a 404 Not Found response.
    *
    * @param request
    *   The incoming HTTP request
    * @return
    *   The HTTP response from the matched handler, or 404 if no match
    */
  def handle(request: Request): Response = {
    routes.get((request.method, request.path)) match {
      case Some(handler) => handler(request)
      case None          => Response.notFound()
    }
  }
}

object Router {

  /** Creates a Router from a variable number of route definitions.
    *
    * Each route is defined as a triple of (Method, path, handler).
    *
    * Example:
    * {{{
    * val router = Router(
    *   (Method.GET, "/users", getUsersHandler),
    *   (Method.POST, "/users", createUserHandler)
    * )
    * }}}
    *
    * @param routes
    *   Variable argument list of (Method, path, handler) triples
    * @return
    *   A Router instance configured with the given routes
    */
  def apply(routes: (Method, String, Request => Response)*): Router = {
    val routeMap = routes.map { case (method, path, handler) =>
      (method, path) -> handler
    }.toMap
    new Router(routeMap)
  }
}
