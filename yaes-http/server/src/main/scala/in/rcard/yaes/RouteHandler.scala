package in.rcard.yaes

/** Type-safe route handler.
  *
  * Handles a request with typed path parameters and query parameters. The type parameters ensure
  * that the handler receives exactly the parameters declared in the route pattern.
  *
  * @tparam PathP
  *   The type-level encoding of path parameters this handler expects
  * @tparam QueryP
  *   The type-level encoding of query parameters this handler expects
  */
sealed trait RouteHandler[PathP <: PathParams, QueryP <: QueryParams] {
  /** Handle a request with the given typed parameters.
    *
    * @param request
    *   The HTTP request
    * @param params
    *   The typed parameter values extracted from the request path
    * @param query
    *   The typed query parameter values extracted from the request query string
    * @return
    *   The HTTP response
    */
  def handle(request: Request, params: ParamValues[PathP], query: Query[QueryP]): Response
}

/** Handler for routes with no path parameters and no query parameters.
  *
  * Example:
  * {{{
  * GET(p"/health") { req =>
  *   Response.ok("OK")
  * }
  * }}}
  */
class NoParamHandler(f: Request => Response) extends RouteHandler[NoParams, NoQueryParams] {
  def handle(request: Request, params: ParamValues[NoParams], query: Query[NoQueryParams]): Response =
    f(request)
}

/** Handler for routes with no path parameters but with query parameters.
  *
  * Provides query parameters to the handler via a context function, enabling type-safe access to
  * parsed query parameter values. The query context is passed using Scala 3's context functions,
  * allowing handlers to access query parameters in a clean, composable way.
  *
  * Example:
  * {{{
  * GET(p"/search" ? queryParam[String]("q") & queryParam[Int]("limit")) { query ?=> req =>
  *   val searchTerm = query.get("q")      // Type-safe: String
  *   val maxResults = query.get("limit")  // Type-safe: Int
  *   Response.ok(s"Searching for $searchTerm, limit $maxResults")
  * }
  * }}}
  *
  * @param f
  *   The handler function receiving query context and request. Uses `Query[QueryP] ?=>` syntax
  *   for context function, making the query implicitly available within the handler body.
  * @tparam QueryP
  *   The type-level encoding of query parameters expected by this handler. Typically a HList-style
  *   encoding like `QueryParam["q", String, QueryParam["limit", Int, NoQueryParams]]`.
  */
class NoParamQueryHandler[QueryP <: QueryParams](f: Query[QueryP] ?=> Request => Response)
    extends RouteHandler[NoParams, QueryP] {
  def handle(request: Request, params: ParamValues[NoParams], query: Query[QueryP]): Response =
    f(using query)(request)
}

/** Handler for routes with one path parameter and no query parameters.
  *
  * Example:
  * {{{
  * val userId = param[Int]("userId")
  * GET(p"/users" / userId) { (req, id: Int) =>
  *   Response.ok(s"User $id")
  * }
  * }}}
  */
class OneParamHandler[N1 <: String & Singleton, T1](f: (Request, T1) => Response)
    extends RouteHandler[::[N1, T1, NoParams], NoQueryParams] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, NoParams]],
      query: Query[NoQueryParams]
  ): Response = {
    params match {
      case ParamValueCons(value1, NoParamValues) => f(request, value1.asInstanceOf[T1])
    }
  }
}

/** Handler for routes with one path parameter and query parameters.
  *
  * Combines path parameter extraction with query parameter context, enabling handlers that
  * receive both typed path values and type-safe query parameter access. This handler bridges
  * the path-based routing with query string parsing.
  *
  * Example:
  * {{{
  * val userId = param[Int]("userId")
  * GET((p"/users" / userId) ? queryParam[Boolean]("expand")) { query ?=> (req, id: Int) =>
  *   val expand = query.get("expand")  // Type-safe: Boolean
  *   if (expand) Response.ok(s"User $id (expanded)")
  *   else Response.ok(s"User $id")
  * }
  * }}}
  *
  * @param f
  *   The handler function receiving query context, request, and the typed path parameter value
  * @tparam N1
  *   The name of the path parameter as a singleton type
  * @tparam T1
  *   The value type of the path parameter
  * @tparam QueryP
  *   The type-level encoding of query parameters
  */
class OneParamQueryHandler[N1 <: String & Singleton, T1, QueryP <: QueryParams](
    f: Query[QueryP] ?=> (Request, T1) => Response
) extends RouteHandler[::[N1, T1, NoParams], QueryP] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, NoParams]],
      query: Query[QueryP]
  ): Response = {
    params match {
      case ParamValueCons(value1, NoParamValues) => f(using query)(request, value1.asInstanceOf[T1])
    }
  }
}

/** Handler for routes with two path parameters and no query parameters.
  *
  * Example:
  * {{{
  * val userId = param[Int]("userId")
  * val postId = param[Long]("postId")
  * GET(p"/users" / userId / "posts" / postId) { (req, uid: Int, pid: Long) =>
  *   Response.ok(s"User $uid, Post $pid")
  * }
  * }}}
  */
class TwoParamHandler[
    N1 <: String & Singleton,
    T1,
    N2 <: String & Singleton,
    T2
](f: (Request, T1, T2) => Response)
    extends RouteHandler[::[N1, T1, ::[N2, T2, NoParams]], NoQueryParams] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, ::[N2, T2, NoParams]]],
      query: Query[NoQueryParams]
  ): Response = {
    params match {
      case ParamValueCons(value1, ParamValueCons(value2, NoParamValues)) =>
        f(request, value1.asInstanceOf[T1], value2.asInstanceOf[T2])
    }
  }
}

/** Handler for routes with two path parameters and query parameters.
  *
  * Combines extraction of two path parameters with query parameter context access, enabling
  * handlers to work with multiple typed path values alongside query string parameters.
  *
  * Example:
  * {{{
  * val userId = param[Int]("userId")
  * val postId = param[Long]("postId")
  * GET((p"/users" / userId / "posts" / postId) ? queryParam[String]("format")) { query ?=> (req, uid: Int, pid: Long) =>
  *   val format = query.get("format")  // Type-safe: String
  *   Response.ok(s"User $uid, Post $pid, format=$format")
  * }
  * }}}
  *
  * @param f
  *   The handler function receiving query context, request, and two typed path parameter values
  * @tparam N1
  *   The name of the first path parameter as a singleton type
  * @tparam T1
  *   The value type of the first path parameter
  * @tparam N2
  *   The name of the second path parameter as a singleton type
  * @tparam T2
  *   The value type of the second path parameter
  * @tparam QueryP
  *   The type-level encoding of query parameters
  */
class TwoParamQueryHandler[
    N1 <: String & Singleton,
    T1,
    N2 <: String & Singleton,
    T2,
    QueryP <: QueryParams
](f: Query[QueryP] ?=> (Request, T1, T2) => Response)
    extends RouteHandler[::[N1, T1, ::[N2, T2, NoParams]], QueryP] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, ::[N2, T2, NoParams]]],
      query: Query[QueryP]
  ): Response = {
    params match {
      case ParamValueCons(value1, ParamValueCons(value2, NoParamValues)) =>
        f(using query)(request, value1.asInstanceOf[T1], value2.asInstanceOf[T2])
    }
  }
}

/** Handler for routes with three path parameters and no query parameters.
  *
  * Example:
  * {{{
  * val org = param[String]("org")
  * val repo = param[String]("repo")
  * val issue = param[Int]("issue")
  * GET(p"/repos" / org / repo / "issues" / issue) { (req, o: String, r: String, i: Int) =>
  *   Response.ok(s"$o/$r#$i")
  * }
  * }}}
  */
class ThreeParamHandler[
    N1 <: String & Singleton,
    T1,
    N2 <: String & Singleton,
    T2,
    N3 <: String & Singleton,
    T3
](f: (Request, T1, T2, T3) => Response)
    extends RouteHandler[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], NoQueryParams] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]]],
      query: Query[NoQueryParams]
  ): Response = {
    params match {
      case ParamValueCons(
            value1,
            ParamValueCons(value2, ParamValueCons(value3, NoParamValues))
          ) =>
        f(request, value1.asInstanceOf[T1], value2.asInstanceOf[T2], value3.asInstanceOf[T3])
    }
  }
}

/** Handler for routes with three path parameters and query parameters.
  *
  * Combines extraction of three path parameters with query parameter context access.
  *
  * Example:
  * {{{
  * val org = param[String]("org")
  * val repo = param[String]("repo")
  * val issue = param[Int]("issue")
  * GET((p"/repos" / org / repo / "issues" / issue) ? queryParam[Boolean]("comments")) { query ?=> (req, o: String, r: String, i: Int) =>
  *   val includeComments = query.get("comments")  // Type-safe: Boolean
  *   Response.ok(s"$o/$r#$i, comments=$includeComments")
  * }
  * }}}
  *
  * @param f
  *   The handler function receiving query context, request, and three typed path parameter values
  * @tparam N1
  *   The name of the first path parameter as a singleton type
  * @tparam T1
  *   The value type of the first path parameter
  * @tparam N2
  *   The name of the second path parameter as a singleton type
  * @tparam T2
  *   The value type of the second path parameter
  * @tparam N3
  *   The name of the third path parameter as a singleton type
  * @tparam T3
  *   The value type of the third path parameter
  * @tparam QueryP
  *   The type-level encoding of query parameters
  */
class ThreeParamQueryHandler[
    N1 <: String & Singleton,
    T1,
    N2 <: String & Singleton,
    T2,
    N3 <: String & Singleton,
    T3,
    QueryP <: QueryParams
](f: Query[QueryP] ?=> (Request, T1, T2, T3) => Response)
    extends RouteHandler[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]]],
      query: Query[QueryP]
  ): Response = {
    params match {
      case ParamValueCons(
            value1,
            ParamValueCons(value2, ParamValueCons(value3, NoParamValues))
          ) =>
        f(using query)(
          request,
          value1.asInstanceOf[T1],
          value2.asInstanceOf[T2],
          value3.asInstanceOf[T3]
        )
    }
  }
}

/** Handler for routes with four path parameters and no query parameters.
  *
  * Example:
  * {{{
  * val a = param[Int]("a")
  * val b = param[Int]("b")
  * val c = param[Int]("c")
  * val d = param[Int]("d")
  * GET(p"/calc" / a / b / c / d) { (req, a: Int, b: Int, c: Int, d: Int) =>
  *   Response.ok(s"Sum: ${a + b + c + d}")
  * }
  * }}}
  */
class FourParamHandler[
    N1 <: String & Singleton,
    T1,
    N2 <: String & Singleton,
    T2,
    N3 <: String & Singleton,
    T3,
    N4 <: String & Singleton,
    T4
](f: (Request, T1, T2, T3, T4) => Response)
    extends RouteHandler[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], NoQueryParams] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]]],
      query: Query[NoQueryParams]
  ): Response = {
    params match {
      case ParamValueCons(
            value1,
            ParamValueCons(
              value2,
              ParamValueCons(value3, ParamValueCons(value4, NoParamValues))
            )
          ) =>
        f(
          request,
          value1.asInstanceOf[T1],
          value2.asInstanceOf[T2],
          value3.asInstanceOf[T3],
          value4.asInstanceOf[T4]
        )
    }
  }
}

/** Handler for routes with four path parameters and query parameters.
  *
  * Combines extraction of four path parameters with query parameter context access. This is
  * the maximum number of path parameters supported by the current DSL.
  *
  * Example:
  * {{{
  * val region = param[String]("region")
  * val org = param[String]("org")
  * val repo = param[String]("repo")
  * val pr = param[Int]("pr")
  * GET((p"/regions" / region / "repos" / org / repo / "prs" / pr) ? queryParam[String]("format")) { query ?=> (req, r: String, o: String, rp: String, p: Int) =>
  *   val format = query.get("format")  // Type-safe: String
  *   Response.ok(s"Region $r, $o/$rp#$p, format=$format")
  * }
  * }}}
  *
  * @param f
  *   The handler function receiving query context, request, and four typed path parameter values
  * @tparam N1
  *   The name of the first path parameter as a singleton type
  * @tparam T1
  *   The value type of the first path parameter
  * @tparam N2
  *   The name of the second path parameter as a singleton type
  * @tparam T2
  *   The value type of the second path parameter
  * @tparam N3
  *   The name of the third path parameter as a singleton type
  * @tparam T3
  *   The value type of the third path parameter
  * @tparam N4
  *   The name of the fourth path parameter as a singleton type
  * @tparam T4
  *   The value type of the fourth path parameter
  * @tparam QueryP
  *   The type-level encoding of query parameters
  */
class FourParamQueryHandler[
    N1 <: String & Singleton,
    T1,
    N2 <: String & Singleton,
    T2,
    N3 <: String & Singleton,
    T3,
    N4 <: String & Singleton,
    T4,
    QueryP <: QueryParams
](f: Query[QueryP] ?=> (Request, T1, T2, T3, T4) => Response)
    extends RouteHandler[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]]],
      query: Query[QueryP]
  ): Response = {
    params match {
      case ParamValueCons(
            value1,
            ParamValueCons(
              value2,
              ParamValueCons(value3, ParamValueCons(value4, NoParamValues))
            )
          ) =>
        f(using query)(
          request,
          value1.asInstanceOf[T1],
          value2.asInstanceOf[T2],
          value3.asInstanceOf[T3],
          value4.asInstanceOf[T4]
        )
    }
  }
}
