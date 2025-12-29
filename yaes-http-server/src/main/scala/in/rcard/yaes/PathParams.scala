package in.rcard.yaes

/** Type-level encoding of path parameters using HList-style types.
  *
  * This hierarchy represents path parameters at the type level, allowing the compiler to verify that
  * route handlers receive exactly the parameters declared in the route pattern.
  *
  * Example type encodings:
  * {{{
  * // Route with no parameters: /users
  * NoParams
  *
  * // Route with one parameter: /users/:id where id is Int
  * "id" :: Int :: NoParams
  *
  * // Route with two parameters: /users/:userId/posts/:postId
  * "userId" :: Int :: "postId" :: Long :: NoParams
  * }}}
  *
  * The encoding uses heterogeneous list (HList) style with:
  *   - `::` as the cons cell, adding a named typed parameter
  *   - `NoParams` (alias for PathParamsNil) as the empty list
  */
sealed trait PathParams

/** Cons cell for path parameters.
  *
  * Represents a single named, typed parameter followed by the rest of the parameter list.
  *
  * @tparam Name
  *   The parameter name as a singleton string type (e.g., "id", "userId")
  * @tparam Type
  *   The parameter value type (e.g., Int, Long, String)
  * @tparam Tail
  *   The remaining parameters in the list
  */
case class ::[Name <: String & Singleton, Type, Tail <: PathParams]() extends PathParams

/** Empty parameter list marker. */
case object PathParamsNil extends PathParams

/** Type alias for empty parameter list.
  *
  * Use this instead of PathParamsNil.type for cleaner type signatures.
  */
type NoParams = PathParamsNil.type
