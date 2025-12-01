package in.rcard.yaes.interop

/** Interoperability between YAES and Cats Effect.
  *
  * This package provides conversions between YAES effects and Cats Effect types. Import the
  * package object to get access to all conversion extension methods.
  *
  * Example:
  * {{{
  * import in.rcard.yaes.interop.cats._
  *
  * // Now you can use the .value extension method (with optional timeout)
  * }}}
  */
package object cats {
  // Re-export main conversions for convenient access
  export Cats._

  // Type aliases for clarity in documentation and code
  type YaesIO = in.rcard.yaes.IO
  type CatsIO[A] = _root_.cats.effect.IO[A]
}
