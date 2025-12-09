package in.rcard.yaes.syntax

import cats.Semigroup
import cats.data.NonEmptyList
import in.rcard.yaes.{CatsAccumulate, Raise}

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

/** Syntax extensions for error accumulation with Cats types.
  *
  * Import these extensions to use fluent error accumulation methods on collections.
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
trait AccumulateSyntax {

  /** Extension methods for accumulating errors from collections of Raise computations using
    * Semigroup to combine errors.
    */
  extension [E, A](iterable: Iterable[Raise[E] ?=> A])
    /** Accumulates all the occurred errors using the combine operator of the implicit Semigroup and
      * returns the list of the values or the accumulated errors.
      *
      * Example:
      * {{{
      * import in.rcard.yaes.{Raise, CatsAccumulate}
      * import in.rcard.yaes.syntax.accumulate.*
      * import cats.Semigroup
      *
      * given Semigroup[String] = Semigroup.instance(_ + _)
      *
      * val iterableWithInnerRaise: List[Int raises String] =
      *   List(1, 2, 3, 4, 5).map { value =>
      *     if (value % 2 == 0) {
      *       Raise.raise(value.toString)
      *     } else {
      *       value
      *     }
      *   }
      *
      * val iterableWithOuterRaise: List[Int] raises String =
      *   iterableWithInnerRaise.combineErrorsS
      *
      * val actual = Raise.fold(
      *   iterableWithOuterRaise,
      *   identity,
      *   identity
      * )
      * // actual will be "24"
      * }}}
      *
      * @param semigroup
      *   The semigroup to combine the errors defined on the type `E`
      * @param raise
      *   The Raise context
      * @return
      *   The list of the values or the accumulated error
      */
    inline def combineErrorsS(using semigroup: Semigroup[E], raise: Raise[E]): List[A] =
      CatsAccumulate.mapAccumulatingS(iterable)(identity)

  /** Extension methods for accumulating errors from NonEmptyList of Raise computations using
    * Semigroup to combine errors.
    */
  extension [E, A](nonEmptyList: NonEmptyList[Raise[E] ?=> A])
    /** Accumulates all the occurred errors using the combine operator of the implicit Semigroup and
      * returns the non-empty list of the values or the accumulated errors.
      *
      * Example:
      * {{{
      * import in.rcard.yaes.{Raise, CatsAccumulate}
      * import in.rcard.yaes.syntax.accumulate.*
      * import cats.Semigroup
      * import cats.data.NonEmptyList
      *
      * given Semigroup[String] = Semigroup.instance(_ + _)
      *
      * val iterableWithInnerRaise: NonEmptyList[Int raises String] =
      *   NonEmptyList.of(1, 2, 3, 4, 5).map { value =>
      *     if (value % 2 == 0) {
      *       Raise.raise(value.toString)
      *     } else {
      *       value
      *     }
      *   }
      *
      * val iterableWithOuterRaise: NonEmptyList[Int] raises String =
      *   iterableWithInnerRaise.combineErrorsS
      *
      * val actual = Raise.fold(
      *   iterableWithOuterRaise,
      *   identity,
      *   identity
      * )
      * // actual will be "24"
      * }}}
      *
      * @param semigroup
      *   The semigroup to combine the errors defined on the type `E`
      * @param raise
      *   The Raise context
      * @return
      *   The non-empty list of the values or the accumulated error
      */
    inline def combineErrorsS(using semigroup: Semigroup[E], raise: Raise[E]): NonEmptyList[A] =
      CatsAccumulate.mapAccumulatingS(nonEmptyList)(identity)

  /** Extension methods for accumulating errors from collections of Raise computations using
    * NonEmptyList error channel.
    */
  extension [E, A](iterable: Iterable[Raise[E] ?=> A])
    /** Accumulates all the occurred errors in a NonEmptyList and returns the list of values or the
      * accumulated errors.
      *
      * Example:
      * {{{
      * import in.rcard.yaes.{Raise, CatsAccumulate}
      * import in.rcard.yaes.syntax.accumulate.*
      * import cats.data.NonEmptyList
      *
      * val iterableWithInnerRaise: List[Int raises String] =
      *   List(1, 2, 3, 4, 5).map { value =>
      *     if (value % 2 == 0) {
      *       Raise.raise(value.toString)
      *     } else {
      *       value
      *     }
      *   }
      *
      * val iterableWithOuterRaise: List[Int] raises NonEmptyList[String] =
      *   iterableWithInnerRaise.combineErrors
      *
      * val actual = Raise.fold(
      *   iterableWithOuterRaise,
      *   identity,
      *   identity
      * )
      * // actual will be NonEmptyList("2", "4")
      * }}}
      *
      * @param raise
      *   The Raise context with NonEmptyList error channel
      * @return
      *   The list of the values or the accumulated errors
      */
    inline def combineErrors(using raise: Raise[NonEmptyList[E]]): List[A] =
      CatsAccumulate.mapAccumulating(iterable)(identity)

  /** Extension methods for accumulating errors from NonEmptyList of Raise computations using
    * NonEmptyList error channel.
    */
  extension [E, A](nonEmptyList: NonEmptyList[Raise[E] ?=> A])
    /** Accumulates all the occurred errors in a NonEmptyList and returns the non-empty list of
      * values or the accumulated errors.
      *
      * Example:
      * {{{
      * import in.rcard.yaes.{Raise, CatsAccumulate}
      * import in.rcard.yaes.syntax.accumulate.*
      * import cats.data.NonEmptyList
      *
      * val iterableWithInnerRaise: NonEmptyList[Int raises String] =
      *   NonEmptyList.of(1, 2, 3, 4, 5).map { value =>
      *     if (value % 2 == 0) {
      *       Raise.raise(value.toString)
      *     } else {
      *       value
      *     }
      *   }
      *
      * val iterableWithOuterRaise: NonEmptyList[Int] raises NonEmptyList[String] =
      *   iterableWithInnerRaise.combineErrors
      *
      * val actual = Raise.fold(
      *   iterableWithOuterRaise,
      *   identity,
      *   identity
      * )
      * // actual will be NonEmptyList("2", "4")
      * }}}
      *
      * @param raise
      *   The Raise context with NonEmptyList error channel
      * @return
      *   The non-empty list of the values or the accumulated errors
      */
    inline def combineErrors(using raise: Raise[NonEmptyList[E]]): NonEmptyList[A] =
      CatsAccumulate.mapAccumulating(nonEmptyList)(identity)
}
