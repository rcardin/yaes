package in.rcard.yaes.cats

import cats.Semigroup
import cats.data.NonEmptyList
import in.rcard.yaes.Raise

/** Error accumulation utilities using Cats Semigroup typeclass and NonEmptyList.
  *
  * This object provides functions to accumulate errors using either:
  * - Cats' Semigroup typeclass for flexible error combining strategies (methods ending with 'S')
  * - NonEmptyList for collecting individual errors
  *
  * For extension methods like `combineErrors` and `combineErrorsS`, import:
  * {{{
  * import in.rcard.yaes.syntax.accumulate.given
  * }}}
  */
object accumulate {

  /** Transform every element of an iterable using the given transform, or accumulate all the
    * occurred errors using the Semigroup typeclass defined on the Error type.
    *
    * The tailing 'S' in the name stands for Semigroup.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{Raise, raises}
    * import in.rcard.yaes.cats.accumulate
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
    *   accumulate.mapAccumulatingS(List(1, 2, 3, 4, 5)) { value =>
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
  inline def mapAccumulatingS[E: Semigroup, A, B](iterable: Iterable[A])(
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
    * import in.rcard.yaes.{Raise, raises}
    * import in.rcard.yaes.cats.accumulate
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
    *   accumulate.mapAccumulatingS(NonEmptyList.of(1, 2, 3, 4, 5)) { value =>
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
  inline def mapAccumulatingS[E: Semigroup, A, B](nonEmptyList: NonEmptyList[A])(
      inline transform: A => (Raise[E] ?=> B)
  )(using raise: Raise[E]): NonEmptyList[B] = {
    val result = Raise.mapAccumulating(nonEmptyList.toList, Semigroup[E].combine)(transform)
    // It's safe to call get here because we started from a non-empty list
    NonEmptyList.fromList(result).get
  }

  /** Transform every element of an iterable using the given transform, or accumulate all the
    * occurred errors in a NonEmptyList.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{Raise, raises}
    * import in.rcard.yaes.cats.accumulate
    * import cats.data.NonEmptyList
    *
    * val block: List[Int] raises NonEmptyList[String] =
    *   accumulate.mapAccumulating(List(1, 2, 3, 4, 5)) { value =>
    *     if (value % 2 == 0) {
    *       Raise.raise(value.toString)
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
    * // actual will be NonEmptyList("2", "4")
    * }}}
    *
    * @param iterable
    *   The collection of elements to transform
    * @param transform
    *   The transformation to apply to each element that can raise an error of type `E`
    * @param raise
    *   The Raise context with NonEmptyList error channel
    * @tparam E
    *   The type of the logical error that can be raised
    * @tparam A
    *   The type of the elements in the iterable
    * @tparam B
    *   The type of the transformed elements
    * @return
    *   A list of transformed elements
    */
  inline def mapAccumulating[E, A, B](iterable: Iterable[A])(
      inline transform: A => (Raise[E] ?=> B)
  )(using raise: Raise[NonEmptyList[E]]): List[B] = {
    val errors  = collection.mutable.ArrayBuffer.empty[E]
    val results = collection.mutable.ArrayBuffer.empty[B]
    iterable.foreach { a =>
      Raise.fold[E, B, Unit](transform(a))(
        error => errors += error
      )(
        result => results += result
      )
    }
    NonEmptyList.fromList(errors.toList).fold(results.toList)(Raise.raise(_))
  }

  /** Transform every element of a NonEmptyList using the given transform, or accumulate all the
    * occurred errors in a NonEmptyList.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{Raise, raises}
    * import in.rcard.yaes.cats.accumulate
    * import cats.data.NonEmptyList
    *
    * val block: NonEmptyList[Int] raises NonEmptyList[String] =
    *   accumulate.mapAccumulating(NonEmptyList.of(1, 2, 3, 4, 5)) { value =>
    *     if (value % 2 == 0) {
    *       Raise.raise(value.toString)
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
    * // actual will be NonEmptyList("2", "4")
    * }}}
    *
    * @param nonEmptyList
    *   The non-empty list of elements to transform
    * @param transform
    *   The transformation to apply to each element that can raise an error of type `E`
    * @param raise
    *   The Raise context with NonEmptyList error channel
    * @tparam E
    *   The type of the logical error that can be raised
    * @tparam A
    *   The type of the elements in the original non-empty list
    * @tparam B
    *   The type of the transformed elements
    * @return
    *   A non-empty list of transformed elements
    */
  inline def mapAccumulating[E, A, B](nonEmptyList: NonEmptyList[A])(
      inline transform: A => (Raise[E] ?=> B)
  )(using raise: Raise[NonEmptyList[E]]): NonEmptyList[B] = {
    val resultAsList = accumulate.mapAccumulating(nonEmptyList.toList)(transform)
    // We know it's safe to call get here because we started from a non-empty list
    NonEmptyList.fromList(resultAsList).get
  }
}
