package in.rcard.yaes

/** Type-level encoding of query parameters using HList-style types.
  *
  * This hierarchy represents query parameters at the type level, allowing the compiler to verify
  * that route handlers access exactly the parameters declared in the route pattern.
  *
  * Example type encodings:
  * {{{
  * // Route with no query parameters: /users
  * NoQueryParams
  *
  * // Route with one query parameter: /search?q=term where q is String
  * QueryParam["q", String, NoQueryParams]
  *
  * // Route with two query parameters: /search?q=term&limit=10
  * QueryParam["q", String, QueryParam["limit", Int, NoQueryParams]]
  * }}}
  *
  * The encoding uses heterogeneous list (HList) style with:
  *   - `QueryParam` as the cons cell, adding a named typed parameter
  *   - `NoQueryParams` (alias for QueryParamsNil) as the empty list
  */
sealed trait QueryParams

/** Cons cell for query parameters.
  *
  * Represents a single named, typed parameter followed by the rest of the parameter list.
  *
  * @tparam Name
  *   The parameter name as a singleton string type (e.g., "page", "limit")
  * @tparam Type
  *   The parameter value type (e.g., Int, String, Option[Int], List[String])
  * @tparam Tail
  *   The remaining parameters in the list
  */
case class QueryParam[Name <: String & Singleton, Type, Tail <: QueryParams]() extends QueryParams

/** Empty query parameter list marker. */
case object QueryParamsNil extends QueryParams

/** Type alias for empty query parameter list.
  *
  * Use this instead of QueryParamsNil.type for cleaner type signatures.
  */
type NoQueryParams = QueryParamsNil.type

/** Type-level append operation for query parameters.
  *
  * Appends a new parameter to the end of the parameter list, maintaining left-to-right order.
  *
  * @tparam Q
  *   The existing parameter list
  * @tparam Name
  *   The new parameter name
  * @tparam Type
  *   The new parameter type
  */
type QueryAppend[Q <: QueryParams, Name <: String & Singleton, Type] <: QueryParams = Q match {
  case NoQueryParams                  => QueryParam[Name, Type, NoQueryParams]
  case QueryParam[n, t, tail]         => QueryParam[n, t, QueryAppend[tail, Name, Type]]
}
