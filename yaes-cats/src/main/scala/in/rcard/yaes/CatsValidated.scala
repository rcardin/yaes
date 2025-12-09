package in.rcard.yaes

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyChain, NonEmptyList, ValidatedNec, ValidatedNel}

/** Conversion utilities between YAES Raise effect and Cats Validated types.
  *
  * This object provides functions to convert between YAES's Raise effect and Cats Validated,
  * ValidatedNec, and ValidatedNel types, enabling interoperability with Cats-based validation.
  *
  * For extension methods on Validated types, import the syntax:
  * {{{
  * import in.rcard.yaes.syntax.validated._
  * // or
  * import in.rcard.yaes.syntax.all._
  * }}}
  */
object CatsValidated {

  /** Runs a computation using Raise and returns its outcome as a Validated.
    *
    * Converts a Raise computation to Cats Validated:
    * - Valid represents success
    * - Invalid represents logical failure
    *
    * This function re-throws any exceptions thrown within the Raise block.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{Raise, CatsValidated}
    * import cats.data.Validated
    *
    * val result = CatsValidated.validated {
    *   Raise.raise("error")
    * }
    * // result will be Validated.invalid("error")
    *
    * val success = CatsValidated.validated {
    *   42
    * }
    * // success will be Validated.valid(42)
    * }}}
    *
    * @param block
    *   A computation that can raise errors of type `E`
    * @tparam E
    *   The type of the logical error that can be raised by the computation
    * @tparam A
    *   The type of the value returned by the computation
    * @return
    *   A Validated representing the outcome of the computation
    */
  inline def validated[E, A](inline block: Raise[E] ?=> A): Validated[E, A] =
    Raise.fold[E, A, Validated[E, A]](block)(error => Validated.invalid(error))(value => Validated.valid(value))

  /** Runs a computation using Raise and returns its outcome as a ValidatedNec.
    *
    * Converts a Raise computation to Cats ValidatedNec (Validated with NonEmptyChain):
    * - Valid represents success
    * - Invalid with NonEmptyChain represents logical failure
    *
    * Single errors are wrapped in a NonEmptyChain for consistency with error accumulation patterns.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{Raise, CatsValidated}
    * import cats.data.{Validated, NonEmptyChain}
    *
    * val result = CatsValidated.validatedNec {
    *   Raise.raise("error")
    * }
    * // result will be Validated.invalid(NonEmptyChain.one("error"))
    *
    * val success = CatsValidated.validatedNec {
    *   42
    * }
    * // success will be Validated.valid(42)
    * }}}
    *
    * @param block
    *   A computation that can raise errors of type `E`
    * @tparam E
    *   The type of the logical error that can be raised by the computation
    * @tparam A
    *   The type of the value returned by the computation
    * @return
    *   A ValidatedNec representing the outcome of the computation
    */
  inline def validatedNec[E, A](inline block: Raise[E] ?=> A): ValidatedNec[E, A] =
    validated(
      Raise.withError[NonEmptyChain[E], E, A](error => NonEmptyChain.one(error))(block)
    )

  /** Runs a computation using Raise and returns its outcome as a ValidatedNel.
    *
    * Converts a Raise computation to Cats ValidatedNel (Validated with NonEmptyList):
    * - Valid represents success
    * - Invalid with NonEmptyList represents logical failure
    *
    * Single errors are wrapped in a NonEmptyList for consistency with error accumulation patterns.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{Raise, CatsValidated}
    * import cats.data.{Validated, NonEmptyList}
    *
    * val result = CatsValidated.validatedNel {
    *   Raise.raise("error")
    * }
    * // result will be Validated.invalid(NonEmptyList.one("error"))
    *
    * val success = CatsValidated.validatedNel {
    *   42
    * }
    * // success will be Validated.valid(42)
    * }}}
    *
    * @param block
    *   A computation that can raise errors of type `E`
    * @tparam E
    *   The type of the logical error that can be raised by the computation
    * @tparam A
    *   The type of the value returned by the computation
    * @return
    *   A ValidatedNel representing the outcome of the computation
    */
  inline def validatedNel[E, A](inline block: Raise[E] ?=> A): ValidatedNel[E, A] =
    validated(
      Raise.withError[NonEmptyList[E], E, A](error => NonEmptyList.one(error))(block)
    )

  /** Re-export of syntax extensions for backward compatibility.
    * 
    * Prefer importing from `in.rcard.yaes.syntax.validated._` directly.
    */
  export in.rcard.yaes.syntax.validated.*
}
