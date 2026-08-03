package io.yaes.http.server

import io.yaes.http.core.Method
import io.yaes.http.server.routing.*
import io.yaes.http.server.params.EmptyParams
import scala.NamedTuple.AnyNamedTuple
import scala.annotation.targetName

/** Shared DSL for defining routes of a given HTTP method.
  *
  * Two overloads per method collapse the former per-arity explosion:
  *
  *   - a general form receiving the request plus the extracted path and query parameters as named
  *     tuples, accessed by name (`path.id`, `query.limit`);
  *   - an ergonomic form for routes with no path and no query parameters, receiving just the
  *     request.
  *
  * Example:
  * {{{
  * val userId = param[Int]("userId")
  *
  * GET(p"/health") { req =>
  *   Response.ok("OK")
  * }
  *
  * GET((p"/users" / userId) ? queryParam[Boolean]("expand")) { (req, path, query) =>
  *   if (query.expand) Response.ok(s"User ${path.userId} (expanded)")
  *   else Response.ok(s"User ${path.userId}")
  * }
  * }}}
  */
abstract class MethodBuilder(method: Method) {

  /** Define a route with typed path and query parameters.
    *
    * @tparam PathP
    *   The path parameters as a named tuple
    * @tparam QueryP
    *   The query parameters as a named tuple
    * @param pattern
    *   The path pattern with named-tuple parameter encoding
    * @param handler
    *   Receives the request and the extracted path and query named tuples
    * @return
    *   The route bound to this builder's HTTP method
    */
  @targetName("applyWithParams")
  def apply[PathP <: AnyNamedTuple, QueryP <: AnyNamedTuple](
      pattern: PathPattern[PathP, QueryP]
  )(handler: (Request, PathP, QueryP) => Response): Route[PathP, QueryP] =
    Route(method, pattern, handler)

  /** Define a route with no path and no query parameters.
    *
    * @param pattern
    *   The path pattern (literals only)
    * @param handler
    *   Receives just the request
    * @return
    *   The route bound to this builder's HTTP method
    */
  @targetName("applyNoParams")
  def apply(
      pattern: PathPattern[EmptyParams, EmptyParams]
  )(handler: Request => Response): Route[EmptyParams, EmptyParams] =
    Route(method, pattern, (req, _, _) => handler(req))
}

/** DSL for defining GET routes. */
object GET extends MethodBuilder(Method.GET)

/** DSL for defining POST routes. */
object POST extends MethodBuilder(Method.POST)

/** DSL for defining PUT routes. */
object PUT extends MethodBuilder(Method.PUT)

/** DSL for defining DELETE routes. */
object DELETE extends MethodBuilder(Method.DELETE)

/** DSL for defining PATCH routes. */
object PATCH extends MethodBuilder(Method.PATCH)
