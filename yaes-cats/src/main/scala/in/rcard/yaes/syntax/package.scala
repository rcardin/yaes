package in.rcard.yaes

/** Package containing syntax extensions for YAES Cats integration.
  *
  * Syntax extensions provide fluent APIs through extension methods.
  * Import from specific objects or use `all` for everything.
  *
  * Example:
  * {{{
  * // Import specific syntax
  * import in.rcard.yaes.syntax.accumulate.*
  *
  * // Or import all syntax
  * import in.rcard.yaes.syntax.all.*
  * }}}
  */
package object syntax {

  /** Object providing accumulation syntax extensions.
    *
    * Import from this object to get `combineErrors` and `combineErrorsS` extension methods.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.syntax.accumulate.*
    * import cats.Semigroup
    *
    * given Semigroup[String] = Semigroup.instance(_ + _)
    *
    * val results: List[Int] raises String = 
    *   List(computation1, computation2).combineErrorsS
    * }}}
    */
  object accumulate extends AccumulateSyntax

  /** Object providing all syntax extensions.
    *
    * Import from this object to get all extension methods.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.syntax.all.*
    * }}}
    */
  object all extends AccumulateSyntax
}
