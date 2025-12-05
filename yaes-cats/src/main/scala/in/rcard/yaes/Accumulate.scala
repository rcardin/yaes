package in.rcard.yaes

import cats.Semigroup
import cats.data.NonEmptyList

/** Error accumulation utilities using Cats Semigroup typeclass.
  *
  * This object provides functions to accumulate errors using Cats' Semigroup typeclass,
  * enabling flexible error combining strategies.
  */
object CatsAccumulate {

  /** Transform every element of an iterable using the given transform, or accumulate all the
    * occurred errors using the Semigroup typeclass defined on the Error type.
    *
    * The tailing 'S' in the name stands for Semigroup.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{Raise, CatsAccumulate}
    * import cats.Semigroup
    *
    * case class MyError(errors: List[String])
    *
    * given Semigroup[MyError] with {
    *   def combine(error1: MyError, error2: MyError): MyError =
    *     MyError(error1.errors ++ error2.errors)
    * }
    *
    * val block: List[Int] raises MyError =
    *   CatsAccumulate.mapOrAccumulateS(List(1, 2, 3, 4, 5)) { value =>
    *     if (value % 2 == 0) {
    *       Raise.raise(MyError(List(value.toString)))
    *     } else {
    *       value
    *     }
    *   }
    *
    * val actual = Raise.fold(
    *   block,
    *   identity,
    *   identity
    * )
    * // actual will be MyError(List("2", "4"))
    * }}}
    *
    * @param iterable
    *   The collection of elements to transform
    * @param transform
    *   The transformation to apply to each element that can raise an error of type `E`
    * @param semigroup
    *   The Semigroup instance for combining errors
    * @param raise
    *   The Raise context
    * @tparam E
    *   The type of the logical error that can be raised. It must have a Semigroup instance
    *   available
    * @tparam A
    *   The type of the elements in the iterable
    * @tparam B
    *   The type of the transformed elements
    * @return
    *   A list of transformed elements
    */
  inline def mapOrAccumulateS[E: Semigroup, A, B](iterable: Iterable[A])(
      inline transform: A => (Raise[E] ?=> B)
  )(using raise: Raise[E]): List[B] =
    Raise.mapAccumulating(iterable, Semigroup[E].combine)(transform)

  /** Transform every element of a NonEmptyList using the given transform, or accumulate all the
    * occurred errors using the Semigroup typeclass defined on the Error type.
    *
    * The tailing 'S' in the name stands for Semigroup.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{Raise, CatsAccumulate}
    * import cats.Semigroup
    * import cats.data.NonEmptyList
    *
    * case class MyError(errors: List[String])
    *
    * given Semigroup[MyError] with {
    *   def combine(error1: MyError, error2: MyError): MyError =
    *     MyError(error1.errors ++ error2.errors)
    * }
    *
    * val block: NonEmptyList[Int] raises MyError =
    *   CatsAccumulate.mapOrAccumulateS(NonEmptyList.of(1, 2, 3, 4, 5)) { value =>
    *     if (value % 2 == 0) {
    *       Raise.raise(MyError(List(value.toString)))
    *     } else {
    *       value
    *     }
    *   }
    *
    * val actual = Raise.fold(
    *   block,
    *   identity,
    *   identity
    * )
    * // actual will be MyError(List("2", "4"))
    * }}}
    *
    * @param nonEmptyList
    *   The non-empty list of elements to transform
    * @param transform
    *   The transformation to apply to each element that can raise an error of type `E`
    * @param semigroup
    *   The Semigroup instance for combining errors
    * @param raise
    *   The Raise context
    * @tparam E
    *   The type of the logical error that can be raised. It must have a Semigroup instance
    *   available
    * @tparam A
    *   The type of the elements in the original non-empty list
    * @tparam B
    *   The type of the transformed elements
    * @return
    *   A non-empty list of transformed elements
    */
  inline def mapOrAccumulateS[E: Semigroup, A, B](nonEmptyList: NonEmptyList[A])(
      inline transform: A => (Raise[E] ?=> B)
  )(using raise: Raise[E]): NonEmptyList[B] = {
    val result = Raise.mapAccumulating(nonEmptyList.toList, Semigroup[E].combine)(transform)
    // It's safe to call get here because we started from a non-empty list
    NonEmptyList.fromList(result).get
  }

  /** Extension methods for accumulating errors from collections of Raise computations.
    *
    * These extension methods provide a fluent API for working with collections of computations
    * that may raise errors, using Semigroup to combine errors.
    */
  extension [E, A](iterable: Iterable[Raise[E] ?=> A])
    /** Accumulates all the occurred errors using the combine operator of the implicit Semigroup
      * and returns the list of the values or the accumulated errors.
      *
      * Example:
      * {{{
      * import in.rcard.yaes.{Raise, CatsAccumulate}
      * import in.rcard.yaes.CatsAccumulate.combineErrorsS
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
      CatsAccumulate.mapOrAccumulateS(iterable)(identity)

  extension [E, A](nonEmptyList: NonEmptyList[Raise[E] ?=> A])
    /** Accumulates all the occurred errors using the combine operator of the implicit Semigroup
      * and returns the non-empty list of the values or the accumulated errors.
      *
      * Example:
      * {{{
      * import in.rcard.yaes.{Raise, CatsAccumulate}
      * import in.rcard.yaes.CatsAccumulate.combineErrorsS
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
      CatsAccumulate.mapOrAccumulateS(nonEmptyList)(identity)
}
