package in.rcard.yaes

import scala.annotation.targetName

/** DSL for defining GET routes.
  *
  * Provides overloaded apply methods for routes with 0-4 parameters, ensuring compile-time type
  * safety.
  */
object GET {

  /** Define a GET route with no path parameters.
    *
    * Example:
    * {{{
    * GET(p"/health") { req =>
    *   Response.ok("OK")
    * }
    * }}}
    */
  @targetName("getNoParams")
  def apply[QueryP <: QueryParams](
      pattern: PathPattern[NoParams, QueryP]
  )(handler: Query[QueryP] ?=> Request => Response): Route[NoParams, QueryP] =
    Route(Method.GET, pattern, NoParamQueryHandler(handler))

  /** Define a GET route with one path parameter.
    *
    * Example:
    * {{{
    * val userId = param[Int]("userId")
    * GET(p"/users" / userId) { (req, id: Int) =>
    *   Response.ok(s"User $id")
    * }
    * }}}
    */
  @targetName("getOneParam")
  def apply[N1 <: String & Singleton, T1, QueryP <: QueryParams](
      pattern: PathPattern[::[N1, T1, NoParams], QueryP]
  )(handler: Query[QueryP] ?=> (Request, T1) => Response): Route[::[N1, T1, NoParams], QueryP] =
    Route(Method.GET, pattern, OneParamQueryHandler(handler))

  /** Define a GET route with two path parameters. */
  @targetName("getTwoParams")
  def apply[N1 <: String & Singleton, T1, N2 <: String & Singleton, T2, QueryP <: QueryParams](
      pattern: PathPattern[::[N1, T1, ::[N2, T2, NoParams]], QueryP]
  )(handler: Query[QueryP] ?=> (Request, T1, T2) => Response): Route[::[N1, T1, ::[N2, T2, NoParams]], QueryP] =
    Route(Method.GET, pattern, TwoParamQueryHandler(handler))

  /** Define a GET route with three path parameters. */
  @targetName("getThreeParams")
  def apply[
      N1 <: String & Singleton,
      T1,
      N2 <: String & Singleton,
      T2,
      N3 <: String & Singleton,
      T3,
      QueryP <: QueryParams
  ](pattern: PathPattern[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP])(
      handler: Query[QueryP] ?=> (Request, T1, T2, T3) => Response
  ): Route[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP] =
    Route(Method.GET, pattern, ThreeParamQueryHandler(handler))

  /** Define a GET route with four path parameters. */
  @targetName("getFourParams")
  def apply[
      N1 <: String & Singleton,
      T1,
      N2 <: String & Singleton,
      T2,
      N3 <: String & Singleton,
      T3,
      N4 <: String & Singleton,
      T4,
      QueryP <: QueryParams
  ](pattern: PathPattern[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP])(
      handler: Query[QueryP] ?=> (Request, T1, T2, T3, T4) => Response
  ): Route[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP] =
    Route(Method.GET, pattern, FourParamQueryHandler(handler))
}

/** DSL for defining POST routes. */
object POST {
  @targetName("postNoParams")
  def apply[QueryP <: QueryParams](
      pattern: PathPattern[NoParams, QueryP]
  )(handler: Query[QueryP] ?=> Request => Response): Route[NoParams, QueryP] =
    Route(Method.POST, pattern, NoParamQueryHandler(handler))

  @targetName("postOneParam")
  def apply[N1 <: String & Singleton, T1, QueryP <: QueryParams](
      pattern: PathPattern[::[N1, T1, NoParams], QueryP]
  )(handler: Query[QueryP] ?=> (Request, T1) => Response): Route[::[N1, T1, NoParams], QueryP] =
    Route(Method.POST, pattern, OneParamQueryHandler(handler))

  @targetName("postTwoParams")
  def apply[N1 <: String & Singleton, T1, N2 <: String & Singleton, T2, QueryP <: QueryParams](
      pattern: PathPattern[::[N1, T1, ::[N2, T2, NoParams]], QueryP]
  )(handler: Query[QueryP] ?=> (Request, T1, T2) => Response): Route[::[N1, T1, ::[N2, T2, NoParams]], QueryP] =
    Route(Method.POST, pattern, TwoParamQueryHandler(handler))

  @targetName("postThreeParams")
  def apply[
      N1 <: String & Singleton,
      T1,
      N2 <: String & Singleton,
      T2,
      N3 <: String & Singleton,
      T3,
      QueryP <: QueryParams
  ](pattern: PathPattern[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP])(
      handler: Query[QueryP] ?=> (Request, T1, T2, T3) => Response
  ): Route[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP] =
    Route(Method.POST, pattern, ThreeParamQueryHandler(handler))

  @targetName("postFourParams")
  def apply[
      N1 <: String & Singleton,
      T1,
      N2 <: String & Singleton,
      T2,
      N3 <: String & Singleton,
      T3,
      N4 <: String & Singleton,
      T4,
      QueryP <: QueryParams
  ](pattern: PathPattern[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP])(
      handler: Query[QueryP] ?=> (Request, T1, T2, T3, T4) => Response
  ): Route[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP] =
    Route(Method.POST, pattern, FourParamQueryHandler(handler))
}

/** DSL for defining PUT routes. */
object PUT {
  @targetName("putNoParams")
  def apply[QueryP <: QueryParams](
      pattern: PathPattern[NoParams, QueryP]
  )(handler: Query[QueryP] ?=> Request => Response): Route[NoParams, QueryP] =
    Route(Method.PUT, pattern, NoParamQueryHandler(handler))

  @targetName("putOneParam")
  def apply[N1 <: String & Singleton, T1, QueryP <: QueryParams](
      pattern: PathPattern[::[N1, T1, NoParams], QueryP]
  )(handler: Query[QueryP] ?=> (Request, T1) => Response): Route[::[N1, T1, NoParams], QueryP] =
    Route(Method.PUT, pattern, OneParamQueryHandler(handler))

  @targetName("putTwoParams")
  def apply[N1 <: String & Singleton, T1, N2 <: String & Singleton, T2, QueryP <: QueryParams](
      pattern: PathPattern[::[N1, T1, ::[N2, T2, NoParams]], QueryP]
  )(handler: Query[QueryP] ?=> (Request, T1, T2) => Response): Route[::[N1, T1, ::[N2, T2, NoParams]], QueryP] =
    Route(Method.PUT, pattern, TwoParamQueryHandler(handler))

  @targetName("putThreeParams")
  def apply[
      N1 <: String & Singleton,
      T1,
      N2 <: String & Singleton,
      T2,
      N3 <: String & Singleton,
      T3,
      QueryP <: QueryParams
  ](pattern: PathPattern[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP])(
      handler: Query[QueryP] ?=> (Request, T1, T2, T3) => Response
  ): Route[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP] =
    Route(Method.PUT, pattern, ThreeParamQueryHandler(handler))

  @targetName("putFourParams")
  def apply[
      N1 <: String & Singleton,
      T1,
      N2 <: String & Singleton,
      T2,
      N3 <: String & Singleton,
      T3,
      N4 <: String & Singleton,
      T4,
      QueryP <: QueryParams
  ](pattern: PathPattern[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP])(
      handler: Query[QueryP] ?=> (Request, T1, T2, T3, T4) => Response
  ): Route[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP] =
    Route(Method.PUT, pattern, FourParamQueryHandler(handler))
}

/** DSL for defining DELETE routes. */
object DELETE {
  @targetName("deleteNoParams")
  def apply[QueryP <: QueryParams](
      pattern: PathPattern[NoParams, QueryP]
  )(handler: Query[QueryP] ?=> Request => Response): Route[NoParams, QueryP] =
    Route(Method.DELETE, pattern, NoParamQueryHandler(handler))

  @targetName("deleteOneParam")
  def apply[N1 <: String & Singleton, T1, QueryP <: QueryParams](
      pattern: PathPattern[::[N1, T1, NoParams], QueryP]
  )(handler: Query[QueryP] ?=> (Request, T1) => Response): Route[::[N1, T1, NoParams], QueryP] =
    Route(Method.DELETE, pattern, OneParamQueryHandler(handler))

  @targetName("deleteTwoParams")
  def apply[N1 <: String & Singleton, T1, N2 <: String & Singleton, T2, QueryP <: QueryParams](
      pattern: PathPattern[::[N1, T1, ::[N2, T2, NoParams]], QueryP]
  )(handler: Query[QueryP] ?=> (Request, T1, T2) => Response): Route[::[N1, T1, ::[N2, T2, NoParams]], QueryP] =
    Route(Method.DELETE, pattern, TwoParamQueryHandler(handler))

  @targetName("deleteThreeParams")
  def apply[
      N1 <: String & Singleton,
      T1,
      N2 <: String & Singleton,
      T2,
      N3 <: String & Singleton,
      T3,
      QueryP <: QueryParams
  ](pattern: PathPattern[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP])(
      handler: Query[QueryP] ?=> (Request, T1, T2, T3) => Response
  ): Route[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP] =
    Route(Method.DELETE, pattern, ThreeParamQueryHandler(handler))

  @targetName("deleteFourParams")
  def apply[
      N1 <: String & Singleton,
      T1,
      N2 <: String & Singleton,
      T2,
      N3 <: String & Singleton,
      T3,
      N4 <: String & Singleton,
      T4,
      QueryP <: QueryParams
  ](pattern: PathPattern[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP])(
      handler: Query[QueryP] ?=> (Request, T1, T2, T3, T4) => Response
  ): Route[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP] =
    Route(Method.DELETE, pattern, FourParamQueryHandler(handler))
}

/** DSL for defining PATCH routes. */
object PATCH {
  @targetName("patchNoParams")
  def apply[QueryP <: QueryParams](
      pattern: PathPattern[NoParams, QueryP]
  )(handler: Query[QueryP] ?=> Request => Response): Route[NoParams, QueryP] =
    Route(Method.PATCH, pattern, NoParamQueryHandler(handler))

  @targetName("patchOneParam")
  def apply[N1 <: String & Singleton, T1, QueryP <: QueryParams](
      pattern: PathPattern[::[N1, T1, NoParams], QueryP]
  )(handler: Query[QueryP] ?=> (Request, T1) => Response): Route[::[N1, T1, NoParams], QueryP] =
    Route(Method.PATCH, pattern, OneParamQueryHandler(handler))

  @targetName("patchTwoParams")
  def apply[N1 <: String & Singleton, T1, N2 <: String & Singleton, T2, QueryP <: QueryParams](
      pattern: PathPattern[::[N1, T1, ::[N2, T2, NoParams]], QueryP]
  )(handler: Query[QueryP] ?=> (Request, T1, T2) => Response): Route[::[N1, T1, ::[N2, T2, NoParams]], QueryP] =
    Route(Method.PATCH, pattern, TwoParamQueryHandler(handler))

  @targetName("patchThreeParams")
  def apply[
      N1 <: String & Singleton,
      T1,
      N2 <: String & Singleton,
      T2,
      N3 <: String & Singleton,
      T3,
      QueryP <: QueryParams
  ](pattern: PathPattern[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP])(
      handler: Query[QueryP] ?=> (Request, T1, T2, T3) => Response
  ): Route[::[N1, T1, ::[N2, T2, ::[N3, T3, NoParams]]], QueryP] =
    Route(Method.PATCH, pattern, ThreeParamQueryHandler(handler))

  @targetName("patchFourParams")
  def apply[
      N1 <: String & Singleton,
      T1,
      N2 <: String & Singleton,
      T2,
      N3 <: String & Singleton,
      T3,
      N4 <: String & Singleton,
      T4,
      QueryP <: QueryParams
  ](pattern: PathPattern[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP])(
      handler: Query[QueryP] ?=> (Request, T1, T2, T3, T4) => Response
  ): Route[::[N1, T1, ::[N2, T2, ::[N3, T3, ::[N4, T4, NoParams]]]], QueryP] =
    Route(Method.PATCH, pattern, FourParamQueryHandler(handler))
}
