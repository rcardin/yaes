package in.rcard.yaes

/** Package containing syntax extensions for YAES Cats integration.
  *
  * Syntax extensions provide fluent APIs through extension methods.
  * Import from specific objects or use `all` for everything.
  *
  * Available syntax:
  *  - `accumulate` - Error accumulation extensions (`combineErrors`, `combineErrorsS`)
  *  - `validated` - Cats Validated extensions (`value`)
  *  - `catseffect` - Cats Effect IO extensions (`value`)
  *  - `all` - All of the above
  *
  * Example:
  * {{{
  * // Import specific syntax
  * import in.rcard.yaes.syntax.accumulate.given
  * import in.rcard.yaes.syntax.validated.given
  * import in.rcard.yaes.syntax.catseffect.given
  *
  * // Or import all syntax
  * import in.rcard.yaes.syntax.all.given
  * }}}
  */
package object syntax

