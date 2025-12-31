package in.rcard.yaes

/** Context type for accessing query parameters in a type-safe way.
  *
  * Query instances are provided as context parameters in route handlers, allowing type-safe access
  * to parsed query parameter values. The type parameter Params encodes which parameters are
  * available and their types, enabling compile-time verification.
  *
  * Example:
  * {{{
  * GET(p"/search" ? ("q" -> string) & ("limit" -> int)) { query ?=> req =>
  *   // query: Query["q" :: String :: "limit" :: Int :: NoQueryParams]
  *   val searchTerm = query.get("q")      // String (compile-time verified)
  *   val maxResults = query.get("limit")  // Int (compile-time verified)
  *   Response.ok(s"Searching for $searchTerm, limit $maxResults")
  * }
  * }}}
  *
  * @param values
  *   Internal map of parsed parameter values
  * @tparam Params
  *   The type-level encoding of available query parameters
  */
class Query[Params <: QueryParams](private val values: Map[String, Any]) {

  /** Get a query parameter value in a type-safe way.
    *
    * This method uses compile-time evidence to verify that:
    *   1. The parameter name exists in the query parameter spec
    *   1. The requested type matches the declared type
    *
    * The compiler automatically infers the return type based on the parameter declaration.
    *
    * Example:
    * {{{
    * GET(p"/search" ? ("q" -> string) & ("limit" -> int)) { query ?=> req =>
    *   val q = query.get("q")          // Type inferred as String
    *   val limit = query.get("limit")  // Type inferred as Int
    *   // val foo = query.get("foo")   // Compile error: "foo" not in params
    * }
    * }}}
    *
    * @param name
    *   The parameter name
    * @param ev
    *   Compile-time evidence that Params contains Name :: Type
    * @tparam Name
    *   The parameter name as a singleton type
    * @tparam Type
    *   The parameter value type
    * @return
    *   The typed parameter value
    */
  def get[Name <: String & Singleton, Type](name: Name)(using
      ev: Contains[Params, Name, Type]
  ): Type = values(name).asInstanceOf[Type]
}

object Query {
  /** Create a Query instance from parsed values.
    *
    * This is used internally by the routing layer after parsing query parameters.
    *
    * @param values
    *   Map of parameter names to their parsed values
    * @tparam Params
    *   The type-level query parameter encoding
    */
  def apply[Params <: QueryParams](values: Map[String, Any]): Query[Params] =
    new Query[Params](values)

  /** Get a query parameter value using the implicit Query context.
    *
    * This is a convenience function that retrieves a query parameter value without requiring
    * explicit access to the Query instance. The Query instance is provided as a context parameter.
    *
    * Example:
    * {{{
    * GET(p"/search" ? ("q" -> string) & ("limit" -> int)) { query ?=> req =>
    *   // Using query.get directly:
    *   val searchTerm1 = query.get("q")
    *
    *   // Using Query.queryParam (more concise):
    *   val searchTerm2 = Query.queryParam("q")
    *
    *   // Both are equivalent, searchTerm1 == searchTerm2
    * }
    * }}}
    *
    * @param name
    *   The parameter name
    * @param query
    *   The Query instance provided via context parameter
    * @param ev
    *   Compile-time evidence that Params contains Name :: Type
    * @tparam Name
    *   The parameter name as a singleton type
    * @tparam Type
    *   The parameter value type
    * @tparam Params
    *   The query parameter list type
    * @return
    *   The typed parameter value
    */
  def queryParam[Name <: String & Singleton, Type, Params <: QueryParams](name: Name)(using
      query: Query[Params]
  )(using
      ev: Contains[Params, Name, Type]
  ): Type = query.get(name)(using ev)
}

/** Type-level evidence that a parameter list contains a specific named, typed parameter.
  *
  * This trait provides compile-time proof that a parameter Name :: Type exists somewhere in the
  * Params HList. The compiler searches through the list to find a matching entry.
  *
  * @tparam Params
  *   The query parameter list to search
  * @tparam Name
  *   The parameter name to find
  * @tparam Type
  *   The expected type for the parameter
  */
trait Contains[Params <: QueryParams, Name <: String & Singleton, Type]

object Contains {
  /** Base case: QueryParam[Name, Type, Tail] is at the head of the list. */
  given head[Name <: String & Singleton, Type, Tail <: QueryParams]
      : Contains[QueryParam[Name, Type, Tail], Name, Type] =
    new Contains[QueryParam[Name, Type, Tail], Name, Type] {}

  /** Recursive case: QueryParam[Name, Type, ...] is somewhere in the tail of the list. */
  given tail[Name <: String & Singleton, Type, OtherName <: String & Singleton, OtherType, Tail <: QueryParams](using
      ev: Contains[Tail, Name, Type]
  ): Contains[QueryParam[OtherName, OtherType, Tail], Name, Type] =
    new Contains[QueryParam[OtherName, OtherType, Tail], Name, Type] {}
}
