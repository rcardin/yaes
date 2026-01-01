package in.rcard.yaes.http.server

/** Runtime container for path parameter values.
  *
  * This mirrors the type-level [[PathParams]] structure at runtime, holding the actual parsed
  * parameter values extracted from a request path.
  *
  * Example:
  * {{{
  * // Route: /users/:userId/posts/:postId
  * // Request: /users/123/posts/456
  * // Result: ParamValueCons(123, ParamValueCons(456L, NoParamValues))
  * }}}
  *
  * @tparam Params
  *   The type-level parameter structure this value container corresponds to
  */
sealed trait ParamValues[Params <: PathParams]

/** Empty parameter values.
  *
  * Corresponds to [[NoParams]] at the type level.
  */
case object NoParamValues extends ParamValues[NoParams]

/** Cons cell for parameter values.
  *
  * Represents a single parameter value followed by the rest of the values.
  *
  * @param head
  *   The value of the first parameter
  * @param tail
  *   The remaining parameter values
  * @tparam Name
  *   The parameter name (singleton type)
  * @tparam Type
  *   The parameter value type
  * @tparam Tail
  *   The remaining parameters
  */
case class ParamValueCons[Name <: String & Singleton, Type, Tail <: PathParams](
    head: Type,
    tail: ParamValues[Tail]
) extends ParamValues[::[Name, Type, Tail]]
