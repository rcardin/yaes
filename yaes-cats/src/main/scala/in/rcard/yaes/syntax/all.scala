package in.rcard.yaes.syntax

/** Combined syntax extensions for all YAES Cats integrations.
  *
  * Import this object to get all syntax extensions at once:
  * {{{
  * import in.rcard.yaes.syntax.all.given
  * }}}
  */
object all extends AllSyntax

/** Trait combining all syntax extension traits.
  *
  * This trait can be mixed in to provide all syntax extensions.
  */
trait AllSyntax extends CatsEffectSyntax
                    with ValidatedSyntax
                    with AccumulateSyntax
