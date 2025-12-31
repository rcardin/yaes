package in.rcard.yaes

import scala.quoted.*

/** Typed parameter definition.
  *
  * Represents a path parameter with a specific name and type. Used in the route building DSL.
  *
  * @param name
  *   The parameter name (preserved as a singleton type)
  * @param parser
  *   The parser to convert string values to the target type
  * @tparam Name
  *   The parameter name as a singleton string type
  * @tparam Type
  *   The parameter value type
  */
class TypedParam[Name <: String & Singleton, Type](val name: Name, val parser: PathParamParser[Type])

/** Create a typed parameter definition.
  *
  * This function preserves the parameter name as a singleton type, enabling compile-time verification
  * of route handlers.
  *
  * Example:
  * {{{
  * val userId = param[Int]("userId")
  * val postId = param[Long]("postId")
  *
  * val route = p"/users" / userId / "posts" / postId
  * }}}
  *
  * @param name
  *   The parameter name
  * @param parser
  *   Implicit parser for the target type
  * @tparam Type
  *   The parameter value type
  * @return
  *   A typed parameter that can be used in path building
  */
inline def param[Type](inline name: String)(using parser: PathParamParser[Type]): TypedParam[?, Type] =
  ${paramImpl[Type]('name, 'parser)}

private def paramImpl[Type](nameExpr: Expr[String], parserExpr: Expr[PathParamParser[Type]])(using t: scala.quoted.Type[Type], q: Quotes): Expr[TypedParam[?, Type]] = {
  import q.reflect.*

  nameExpr.value match {
    case Some(name) =>
      val nameType = ConstantType(StringConstant(name))
      nameType.asType match {
        case '[n] =>
          // We need to assert that n is a String & scala.Singleton at the type level
          '{
            new TypedParam[n & String & scala.Singleton, Type]($nameExpr.asInstanceOf[n & String & scala.Singleton], $parserExpr)
          }
      }
    case None =>
      report.errorAndAbort("Parameter name must be a constant string literal")
  }
}

/** Path builder for constructing type-safe route patterns.
  *
  * Accumulates path segments and their type information, allowing the final pattern to know exactly
  * what parameters it expects.
  *
  * @param segments
  *   The accumulated path segments in reverse order
  * @param querySpec
  *   The query parameter specification (if any)
  * @tparam PathP
  *   The type-level encoding of path parameters collected so far
  * @tparam QueryP
  *   The type-level encoding of query parameters collected so far
  */
class PathBuilder[PathP <: PathParams, QueryP <: QueryParams](
    private val segments: List[PathSegment[?]],
    private val querySpec: QueryParamSpec[QueryP]
) {

  /** Append a literal path segment.
    *
    * Example:
    * {{{
    * p"/users" / "posts" / "active"
    * }}}
    */
  def /(literal: String): PathBuilder[PathP, QueryP] =
    new PathBuilder[PathP, QueryP](segments :+ Literal(literal, End), querySpec)

  /** Append a typed parameter segment.
    *
    * Example:
    * {{{
    * val userId = param[Int]("userId")
    * p"/users" / userId
    * }}}
    */
  def /[Name <: String & Singleton, Type](
      param: TypedParam[Name, Type]
  ): PathBuilder[Append[PathP, Name, Type], QueryP] =
    new PathBuilder[Append[PathP, Name, Type], QueryP](
      segments :+ Param(param.name, param.parser, End),
      querySpec
    )

  /** Add the first query parameter.
    *
    * Example:
    * {{{
    * p"/search" ? queryParam[String]("q")
    * }}}
    */
  def ?[Name <: String & Singleton, Type](
      param: TypedQueryParam[Name, Type]
  ): PathBuilder[PathP, QueryParam[Name, Type, NoQueryParams]] =
    new PathBuilder[PathP, QueryParam[Name, Type, NoQueryParams]](
      segments,
      SingleParam(param.name, param.parser, EndOfQuery)
    )

  /** Add an additional query parameter.
    *
    * Example:
    * {{{
    * p"/search" ? queryParam[String]("q") & queryParam[Int]("limit")
    * }}}
    */
  def &[Name <: String & Singleton, Type](
      param: TypedQueryParam[Name, Type]
  ): PathBuilder[PathP, QueryAppend[QueryP, Name, Type]] =
    new PathBuilder[PathP, QueryAppend[QueryP, Name, Type]](
      segments,
      appendQueryParam(querySpec, param).asInstanceOf[QueryParamSpec[QueryAppend[QueryP, Name, Type]]]
    )

  /** Helper to append a query parameter to the spec chain. */
  private def appendQueryParam[Name <: String & Singleton, Type](
      spec: QueryParamSpec[?],
      param: TypedQueryParam[Name, Type]
  ): QueryParamSpec[?] = spec match {
    case EndOfQuery => SingleParam(param.name, param.parser, EndOfQuery)
    case SingleParam(n, p, next) =>
      SingleParam(n, p, appendQueryParam(next, param).asInstanceOf[QueryParamSpec[QueryParams]])
  }

  /** Build the final PathPattern.
    *
    * Converts the accumulated segments into a properly linked PathSegment structure.
    */
  private[yaes] def build: PathPattern[PathP, QueryP] = {
    // Build the segment chain from right to left
    val finalSegment = segments.foldRight[PathSegment[PathParams]](End) {
      case (Literal(value, _), next) => Literal(value, next)
      case (Param(name, parser, _), next) =>
        Param(name.asInstanceOf[String & Singleton], parser.asInstanceOf[PathParamParser[Any]], next)
      case (End, next) => next  // Handle root path case
    }
    PathPattern(finalSegment.asInstanceOf[PathSegment[PathP]], querySpec)
  }
}

object PathBuilder {
  /** Implicit conversion to PathPattern for convenience. */
  given [PathP <: PathParams, QueryP <: QueryParams]: Conversion[PathBuilder[PathP, QueryP], PathPattern[PathP, QueryP]] = _.build
}

/** String interpolator for literal path prefixes.
  *
  * Example:
  * {{{
  * p"/users"       // PathBuilder[NoParams, NoQueryParams]
  * p"/api/v1"      // PathBuilder[NoParams, NoQueryParams]
  * }}}
  */
extension (sc: StringContext) {
  def p(args: Any*): PathBuilder[NoParams, NoQueryParams] = {
    require(args.isEmpty, "Path interpolator does not support arguments, use / operator instead")
    val path = sc.parts.mkString

    // Split the path and create literals
    val segments = path.split("/").filter(_.nonEmpty).toList

    if (segments.isEmpty) {
      // Root path "/" - empty segments list, End will be added by foldRight
      new PathBuilder[NoParams, NoQueryParams](List(), EndOfQuery)
    } else {
      // Create literal segments
      val pathSegments = segments.map(seg => Literal(seg, End))
      new PathBuilder[NoParams, NoQueryParams](pathSegments, EndOfQuery)
    }
  }
}
