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
  * @tparam Params
  *   The type-level encoding of parameters collected so far
  */
class PathBuilder[Params <: PathParams](private val segments: List[PathSegment[?]]) {

  /** Append a literal path segment.
    *
    * Example:
    * {{{
    * p"/users" / "posts" / "active"
    * }}}
    */
  def /(literal: String): PathBuilder[Params] =
    new PathBuilder[Params](segments :+ Literal(literal, End))

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
  ): PathBuilder[::[Name, Type, Params]] =
    new PathBuilder[::[Name, Type, Params]](
      segments :+ Param(param.name, param.parser, End)
    )

  /** Build the final PathPattern.
    *
    * Converts the accumulated segments into a properly linked PathSegment structure.
    */
  private[yaes] def build: PathPattern[Params] = {
    // Build the segment chain from right to left
    val finalSegment = segments.foldRight[PathSegment[PathParams]](End) {
      case (Literal(value, _), next) => Literal(value, next)
      case (Param(name, parser, _), next) =>
        Param(name.asInstanceOf[String & Singleton], parser.asInstanceOf[PathParamParser[Any]], next)
    }
    PathPattern(finalSegment.asInstanceOf[PathSegment[Params]])
  }

  /** Implicit conversion to PathPattern for convenience. */
  given [P <: PathParams]: Conversion[PathBuilder[P], PathPattern[P]] = _.build
}

/** String interpolator for literal path prefixes.
  *
  * Example:
  * {{{
  * p"/users"       // PathBuilder[NoParams]
  * p"/api/v1"      // PathBuilder[NoParams]
  * }}}
  */
extension (sc: StringContext) {
  def p(args: Any*): PathBuilder[NoParams] = {
    require(args.isEmpty, "Path interpolator does not support arguments, use / operator instead")
    val path = sc.parts.mkString

    // Split the path and create literals
    val segments = path.split("/").filter(_.nonEmpty).toList

    if (segments.isEmpty) {
      // Root path "/"
      new PathBuilder[NoParams](List(End))
    } else {
      // Create literal segments
      val pathSegments = segments.map(seg => Literal(seg, End))
      new PathBuilder[NoParams](pathSegments)
    }
  }
}
