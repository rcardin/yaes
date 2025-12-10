package in.rcard.yaes.syntax

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import in.rcard.yaes.Raise

/** Syntax extensions for Cats Validated types to integrate with YAES Raise effect.
  *
  * Import this object to get extension methods for extracting values from Validated,
  * ValidatedNec, and ValidatedNel, raising errors via Raise when invalid.
  *
  * Example:
  * {{{
  * import in.rcard.yaes.syntax.validated._
  * import in.rcard.yaes.Raise
  * import cats.data.Validated
  *
  * val result = Raise.either {
  *   val v: Validated[String, Int] = Validated.valid(42)
  *   v.value  // Returns 42
  * }
  * }}}
  */
object validated extends ValidatedSyntax

/** Trait containing Validated syntax extensions.
  *
  * This trait can be mixed in to provide Validated extension methods.
  */
trait ValidatedSyntax {

  extension [E, A](validated: Validated[E, A])
    /** Extracts the value from a Validated or raises the error.
      *
      * Converts a Cats Validated to a YAES Raise computation:
      * - Valid(value) returns the value
      * - Invalid(error) raises the error via Raise
      *
      * Example:
      * {{{
      * import in.rcard.yaes.syntax.validated._
      * import in.rcard.yaes.Raise
      * import cats.data.Validated
      *
      * val result = Raise.either {
      *   val v: Validated[String, Int] = Validated.invalid("error")
      *   v.value  // Raises "error"
      * }
      * // result will be Left("error")
      *
      * val success = Raise.either {
      *   val v: Validated[String, Int] = Validated.valid(42)
      *   v.value  // Returns 42
      * }
      * // success will be Right(42)
      * }}}
      *
      * @param raise
      *   The Raise context for raising errors
      * @return
      *   The value if Valid, otherwise raises the error
      */
    inline def value(using raise: Raise[E]): A = validated match {
      case Valid(value)   => value
      case Invalid(error) => Raise.raise(error)
    }
}
