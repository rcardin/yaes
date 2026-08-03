package io.yaes.http.server.params

import scala.NamedTuple.{NamedTuple, AnyNamedTuple, Names, DropNames}

// Named-tuple utilities shared by path and query parameter encodings.
//
// A path or query parameter *is* a name paired with a type, which is exactly a named-tuple
// element. Routes therefore encode their parameters as scala.NamedTuples: a single-parameter
// path is `(id: Int)`, a two-parameter query is `(q: String, limit: Option[Int])`, and a route
// with no parameters uses EmptyParams. Handlers read parameters by name (`path.id`,
// `query.limit`) rather than positionally, so access is order-independent and self-documenting.

/** Empty named tuple, used for routes with no path or no query parameters.
  *
  * Note: `EmptyTuple` on its own is *not* a subtype of [[scala.NamedTuple.AnyNamedTuple]], so it
  * cannot be used where an `AnyNamedTuple` is required. `NamedTuple[EmptyTuple, EmptyTuple]`
  * satisfies the bound and has the same (empty) runtime representation.
  */
type EmptyParams = NamedTuple[EmptyTuple, EmptyTuple]

/** Append named tuple `B` to the end of named tuple `A`.
  *
  * [[scala.Tuple.Concat]] does not see through the `NamedTuple` wrapper, so the names and the value
  * types are concatenated separately and the result is rebuilt via `NamedTuple`.
  *
  * @tparam A
  *   The existing named tuple
  * @tparam B
  *   The named tuple to append
  */
type Append[A <: AnyNamedTuple, B <: AnyNamedTuple] =
  NamedTuple[Tuple.Concat[Names[A], Names[B]], Tuple.Concat[DropNames[A], DropNames[B]]]

/** Append a single named element `(Name: Type)` to the end of named tuple `P`.
  *
  * @tparam P
  *   The existing named tuple
  * @tparam Name
  *   The new parameter name as a singleton string type
  * @tparam Type
  *   The new parameter value type
  */
type AppendOne[P <: AnyNamedTuple, Name <: String & Singleton, Type] =
  Append[P, NamedTuple[Tuple1[Name], Tuple1[Type]]]
