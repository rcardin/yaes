package io.yaes.http.server

import io.yaes.http.server.routing.PathPattern
import io.yaes.http.server.params.path.*
import io.yaes.http.server.params.query.*
import io.yaes.http.server.params.{EmptyParams, AppendOne}
import scala.NamedTuple.{NamedTuple, AnyNamedTuple}
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
  * This function preserves the parameter name as a singleton type, enabling compile-time
  * verification of route handlers. It is `transparent inline` so the concrete singleton name from
  * the macro body surfaces to the call site and flows into the route's named-tuple type.
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
transparent inline def param[Type](inline name: String)(using parser: PathParamParser[Type]): TypedParam[?, Type] =
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
  * Accumulates path segments and their type information, tracking exactly which parameters the
  * final pattern expects as named tuples.
  *
  * @param segments
  *   The accumulated path segments, in order
  * @param querySpec
  *   The query parameter specification (if any)
  * @tparam PathP
  *   The named-tuple encoding of path parameters collected so far
  * @tparam QueryP
  *   The named-tuple encoding of query parameters collected so far
  */
class PathBuilder[PathP <: AnyNamedTuple, QueryP <: AnyNamedTuple](
    private val segments: List[PathSegment],
    private val querySpec: QueryParamSpec
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
  ): PathBuilder[AppendOne[PathP, Name, Type], QueryP] =
    new PathBuilder[AppendOne[PathP, Name, Type], QueryP](
      segments :+ Param(param.name, param.parser.asInstanceOf[PathParamParser[Any]], End),
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
  ): PathBuilder[PathP, NamedTuple[Tuple1[Name], Tuple1[Type]]] =
    new PathBuilder[PathP, NamedTuple[Tuple1[Name], Tuple1[Type]]](
      segments,
      SingleParam(param.name, param.parser.asInstanceOf[QueryParamParser[Any]], EndOfQuery)
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
  ): PathBuilder[PathP, AppendOne[QueryP, Name, Type]] =
    new PathBuilder[PathP, AppendOne[QueryP, Name, Type]](
      segments,
      appendQueryParam(querySpec, param)
    )

  /** Helper to append a query parameter to the end of the spec chain. */
  private def appendQueryParam[Name <: String & Singleton, Type](
      spec: QueryParamSpec,
      param: TypedQueryParam[Name, Type]
  ): QueryParamSpec = spec match {
    case EndOfQuery => SingleParam(param.name, param.parser.asInstanceOf[QueryParamParser[Any]], EndOfQuery)
    case SingleParam(n, p, next) =>
      SingleParam(n, p, appendQueryParam(next, param))
  }

  /** Build the final PathPattern.
    *
    * Converts the accumulated segments into a properly linked PathSegment structure.
    */
  private[yaes] def build: PathPattern[PathP, QueryP] = {
    // Build the segment chain from right to left
    val finalSegment = segments.foldRight[PathSegment](End) {
      case (Literal(value, _), next) => Literal(value, next)
      case (Param(name, parser, _), next) => Param(name, parser, next)
      case (End, next) => next // Handle root path case
    }
    PathPattern(finalSegment, querySpec)
  }
}

object PathBuilder {
  /** Implicit conversion to PathPattern for convenience. */
  given [PathP <: AnyNamedTuple, QueryP <: AnyNamedTuple]: Conversion[PathBuilder[PathP, QueryP], PathPattern[PathP, QueryP]] = _.build
}

/** String interpolator for literal path prefixes.
  *
  * Example:
  * {{{
  * p"/users"       // PathBuilder[EmptyParams, EmptyParams]
  * p"/api/v1"      // PathBuilder[EmptyParams, EmptyParams]
  * }}}
  */
extension (sc: StringContext) {
  /** Interpolated arguments are rejected at compile time: build parameters and additional literal
    * segments with `/` instead.
    */
  inline def p(inline args: Any*): PathBuilder[EmptyParams, EmptyParams] =
    ${pImpl('sc, 'args)}
}

private def pImpl(scExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using q: Quotes): Expr[PathBuilder[EmptyParams, EmptyParams]] = {
  import q.reflect.*

  argsExpr match {
    case Varargs(Seq()) => '{literalPath($scExpr)}
    case _ =>
      report.errorAndAbort(
        "The path interpolator does not accept interpolated values. " +
          "Build path parameters with `/` and `param`, e.g. `p\"/users\" / param[Int](\"id\")`, " +
          "and literal segments with `/`, e.g. `p\"/api\" / \"v1\"`."
      )
  }
}

private[server] def literalPath(sc: StringContext): PathBuilder[EmptyParams, EmptyParams] = {
  val path = sc.parts.mkString

  // Split the path and create literals
  val segments = path.split("/").filter(_.nonEmpty).toList

  if (segments.isEmpty) {
    // Root path "/" - empty segments list, End will be added by foldRight
    new PathBuilder[EmptyParams, EmptyParams](List(), EndOfQuery)
  } else {
    // Create literal segments
    val pathSegments = segments.map(seg => Literal(seg, End))
    new PathBuilder[EmptyParams, EmptyParams](pathSegments, EndOfQuery)
  }
}
