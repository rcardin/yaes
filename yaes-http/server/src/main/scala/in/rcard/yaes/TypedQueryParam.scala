package in.rcard.yaes

import scala.quoted.*

/** Typed query parameter definition.
  *
  * Represents a query parameter with a specific name and type. Used in the route building DSL.
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
class TypedQueryParam[Name <: String & Singleton, Type](
    val name: Name,
    val parser: QueryParamParser[Type]
)

/** Create a typed query parameter definition.
  *
  * This function preserves the parameter name as a singleton type, enabling compile-time
  * verification of query parameters in route handlers.
  *
  * Example:
  * {{{
  * val page = queryParam[Int]("page")
  * val search = queryParam[String]("q")
  *
  * val route = p"/search" ? search & page
  * }}}
  *
  * @param name
  *   The parameter name
  * @param parser
  *   Implicit parser for the target type
  * @tparam Type
  *   The parameter value type
  * @return
  *   A typed query parameter that can be used in route building
  */
inline def queryParam[Type](inline name: String)(using
    parser: QueryParamParser[Type]
): TypedQueryParam[?, Type] =
  ${queryParamImpl[Type]('name, 'parser)}

private def queryParamImpl[Type](nameExpr: Expr[String], parserExpr: Expr[QueryParamParser[Type]])(using
    t: scala.quoted.Type[Type],
    q: Quotes
): Expr[TypedQueryParam[?, Type]] = {
  import q.reflect.*

  nameExpr.value match {
    case Some(name) =>
      val nameType = ConstantType(StringConstant(name))
      nameType.asType match {
        case '[n] =>
          '{
            new TypedQueryParam[n & String & scala.Singleton, Type](
              $nameExpr.asInstanceOf[n & String & scala.Singleton],
              $parserExpr
            )
          }
      }
    case None =>
      report.errorAndAbort("Query parameter name must be a constant string literal")
  }
}
