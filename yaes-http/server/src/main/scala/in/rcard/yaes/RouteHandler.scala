package in.rcard.yaes

/** Type-safe route handler.
  *
  * Handles a request with typed path parameters. The type parameter [[Params]] ensures that the
  * handler receives exactly the parameters declared in the route pattern.
  *
  * @tparam Params
  *   The type-level encoding of parameters this handler expects
  */
sealed trait RouteHandler[Params <: PathParams] {
  /** Handle a request with the given typed parameters.
    *
    * @param request
    *   The HTTP request
    * @param params
    *   The typed parameter values extracted from the request path
    * @return
    *   The HTTP response
    */
  def handle(request: Request, params: ParamValues[Params]): Response
}

/** Handler for routes with no parameters.
  *
  * Example:
  * {{{
  * GET(p"/health") { req =>
  *   Response.ok("OK")
  * }
  * }}}
  */
class NoParamHandler(f: Request => Response) extends RouteHandler[NoParams] {
  def handle(request: Request, params: ParamValues[NoParams]): Response = f(request)
}

/** Handler for routes with one parameter.
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
    extends RouteHandler[::[N1, T1, NoParams]] {
  def handle(request: Request, params: ParamValues[::[N1, T1, NoParams]]): Response = {
    params match {
      case ParamValueCons(value1, NoParamValues) => f(request, value1.asInstanceOf[T1])
    }
  }
}

/** Handler for routes with two parameters.
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
    extends RouteHandler[::[N1, T1, ::[N2, T2, NoParams]]] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, ::[N2, T2, NoParams]]]
  ): Response = {
    params match {
      case ParamValueCons(value1, ParamValueCons(value2, NoParamValues)) =>
        f(request, value1.asInstanceOf[T1], value2.asInstanceOf[T2])
    }
  }
}

/** Handler for routes with three parameters.
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
    extends RouteHandler[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]]] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]]]
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

/** Handler for routes with four parameters.
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
    extends RouteHandler[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]]] {
  def handle(
      request: Request,
      params: ParamValues[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]]]
  ): Response = {
    params match {
      case ParamValueCons(
            value1,
            ParamValueCons(
              value2,
              ParamValueCons(value3, ParamValueCons(value4, NoParamValues))
            )
          ) =>
        f(request, value1.asInstanceOf[T1], value2.asInstanceOf[T2], value3.asInstanceOf[T3], value4.asInstanceOf[T4])
    }
  }
}
