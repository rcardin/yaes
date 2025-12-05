package in.rcard.yaes

/** Package object for Cats typeclass instances.
  *
  * Import instances from this package to enable Cats integration with YAES Raise effect.
  */
package object instances {

  /** Object providing all Raise typeclass instances.
    *
    * Import given instances from this object to enable MonadError for Raise computations.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.instances.raise.given
    * import cats.syntax.all.*
    *
    * def computation: Int raises String = ???
    * val handled = computation.handleError(_ => 0)
    * }}}
    */
  object raise extends RaiseInstances
}
