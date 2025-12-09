package in.rcard.yaes

/** Package containing syntax extensions for YAES Cats integration.
  *
  * Syntax extensions provide fluent APIs through extension methods.
  * Import from specific objects or use `all` for everything.
  *
  * Available syntax:
  *  - `accumulate` - Error accumulation extensions (`combineErrors`, `combineErrorsS`)
  *  - `validated` - Cats Validated extensions (`value`)
  *  - `catsIO` - Cats Effect IO extensions (`value`)
  *  - `all` - All of the above
  *
  * Example:
  * {{{
  * // Import specific syntax
  * import in.rcard.yaes.syntax.accumulate.*
  * import in.rcard.yaes.syntax.validated.*
  * import in.rcard.yaes.syntax.catsIO.*
  *
  * // Or import all syntax
  * import in.rcard.yaes.syntax.all.*
  * }}}
  */
package object syntax {

  /** Object providing all syntax extensions.
    *
    * Import from this object to get all extension methods:
    * - `combineErrors`, `combineErrorsS` for error accumulation
    * - `value` for Cats Validated types
    * - `value` for Cats Effect IO
    *
    * Example:
    * {{{
    * import in.rcard.yaes.syntax.all.*
    * import cats.effect.{IO => CatsIO}
    * import cats.data.Validated
    *
    * val catsIO: CatsIO[Int] = CatsIO.pure(42)
    * val validated: Validated[String, Int] = Validated.valid(42)
    *
    * YaesIO.run {
    *   Raise.either {
    *     catsIO.value      // From catsIO syntax
    *     validated.value   // From validated syntax
    *   }
    * }
    * }}}
    */
  object all extends AccumulateSyntax with ValidatedSyntax with CatsIOSyntax
}
