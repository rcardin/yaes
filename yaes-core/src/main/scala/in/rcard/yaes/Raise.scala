package in.rcard.yaes

import scala.reflect.ClassTag
import scala.util.boundary
import scala.util.boundary.break
import scala.util.control.ControlThrowable
import scala.util.control.NoStackTrace
import scala.util.control.NonFatal
import scala.util.Try
import scala.util.Success
import scala.util.Failure

/** An effect that represents the ability to raise an error of type `E`.
  *
  * Example usage:
  * {{{
  * trait ArithmeticError
  * case object DivisionByZero extends ArithmeticError
  * type DivisionByZero = DivisionByZero.type
  *
  * def divide(x: Int, y: Int)(using Raise[ArithmeticError]): Int =
  *   if (y == 0) then
  *     Raise.raise(DivisionByZero)
  *   else
  *     x / y
  *
  * // Using fold to handle errors
  * val result = Raise.fold {
  *   divide(10, 0)
  * } (onError = err => "Error: " + err)(onSuccess = res => "Result: " + res)
  * }}}
  *
  * This object contains various combinators and handlers for working with the Raise effect in a
  * safe and composable way.
  */
object Raise {

  type Raise[E] = Yaes[Raise.Unsafe[E]]

  /** Lifts a block of code that may use the Raise effect.
    *
    * @param block
    *   the code to execute
    * @return
    *   the result of the block if successful
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    */
  def apply[E, A](block: => A): Raise[E] ?=> A = block

  /** Raises an error in a context where a Raise effect is available.
    *
    * Example:
    * {{{
    * def ensurePositive(n: Int)(using Raise[String]): Int =
    *   if (n <= 0) then Raise.raise("Number must be positive")
    *   else n
    * }}}
    *
    * @param error
    *   the error to raise
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    */
  def raise[E, A](error: E)(using eff: Raise[E]): Nothing = eff.unsafe.raise(error)

  /** Handles both success and error cases of a computation that may raise an error.
    *
    * Example:
    * {{{
    * // Define our error type
    * sealed trait DivisionError
    * case object DivisionByZero extends DivisionError
    *
    * // Define a function that may raise an error
    * def divide(x: Int, y: Int)(using Raise[DivisionError]): Int =
    *   if (y == 0) then Raise.raise(DivisionByZero)
    *   else x / y
    *
    * // Handle both success and error cases using fold with curried parameters
    * val result = Raise.fold {
    *   divide(10, 0)
    * } {
    *   case DivisionByZero => "Cannot divide by zero"
    * } { result =>
    *   s"Result is $result"
    * }
    * // result will be "Cannot divide by zero"
    * }}}
    *
    * @param block
    *   the computation that may raise an error
    * @param onError
    *   handler for the error case
    * @param onSuccess
    *   handler for the success case
    * @return
    *   the result of either onError or onSuccess
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    * @tparam B
    *   the type of the result of the handler
    */
  def fold[E, A, B](block: Raise[E] ?=> A)(onError: E => B)(onSuccess: A => B): B = {
    val handler = new Yaes.Handler[Raise.Unsafe[E], A, B] {

      override def handle(program: (Raise[E]) ?=> A): B = {
        boundary {
          given eff: Raise[E] = new Yaes(new Raise.Unsafe[E] {
            def raise(error: => E): Nothing =
              break(onError(error))
          })
          onSuccess(block)
        }
      }
    }
    Yaes.handle(block)(using handler)
  }

  /** Runs a computation that may raise an error and returns the result or the error.
    *
    * Example:
    * {{{
    * val result: Int | DivisionError = Raise.run {
    *   divide(10, 0)
    * }
    * // result will be DivisionError
    * }}}
    *
    * @param block
    *   the computation that may raise an error
    * @return
    *   the result of the computation or the error
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    */
  def run[E, A](block: Raise[E] ?=> A): A | E = fold(block)(identity)(identity)

  /** Recovers from an error and returns a default value.
    *
    * Example:
    * {{{
    * val result = Raise.recover {
    *   divide(10, 0)
    * } {
    *   case DivisionByZero => "Cannot divide by zero"
    * }
    * // result will be "Cannot divide by zero"
    * }}}
    *
    * @param block
    *   the computation that may raise an error
    * @param recoverWith
    *   the function to apply to the error
    * @return
    *   the result of the computation or the default value
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    */
  def recover[E, A](block: Raise[E] ?=> A)(recoverWith: E => A): A =
    fold(block)(onError = recoverWith)(onSuccess = identity)

  /** Returns the result of the computation or a default value if an error occurs.
    *
    * Example:
    * {{{
    * val result = Raise.withDefault(0) {
    *   divide(10, 0)
    * }
    * // result will be 0
    * }}}
    *
    * @param default
    *   the default value to return if an error occurs
    * @param block
    *   the computation that may raise an error
    * @return
    *   the result of the computation or the default value
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    */
  def withDefault[E, A](default: => A)(block: Raise[E] ?=> A): A = recover(block)(_ => default)

  /** Returns the result of the computation as an [[Either]].
    *
    * Example:
    * {{{
    * val result: Either[DivisionError, Int] = Raise.either {
    *   divide(10, 0)
    * }
    * // result will be Left(DivisionByZero)
    * }}}
    *
    * @param block
    *   the computation that may raise an error
    * @return
    *   the result of the computation as an Either
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    */
  def either[E, A](block: Raise[E] ?=> A): Either[E, A] =
    fold(block)(onError = Left(_))(onSuccess = Right(_))

  /** Returns the result of the computation as an [[Option]].
    *
    * Example:
    * {{{
    * val result: Option[Int] = Raise.option {
    *   divide(10, 0)
    * }
    * // result will be None
    * }}}
    *
    * @param block
    *   the computation that may raise an error
    * @return
    *   the result of the computation as an [[Option]]
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    */
  def option[E, A](block: Raise[E] ?=> A): Option[A] =
    fold(block)(onError_ => None)(onSuccess = Some(_))

  /** Returns the result of the computation as a nullable value.
    *
    * Example:
    * {{{
    * val result: Int | Null = Raise.nullable {
    *   divide(10, 0)
    * }
    * // result will be null
    * }}}
    *
    * @param block
    *   the computation that may raise an error
    * @return
    *   the result of the computation as a nullable value
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    */
  def nullable[E, A](block: Raise[E] ?=> A): A | Null =
    fold(block)(onError_ => null)(onSuccess = identity)

  /** Ensures that a condition is true and raises an error if it is not.
    *
    * Example:
    * {{{
    * val num = 10
    * val result = Raise.run {
    *   Raise.ensure(num < 0)("Number must be positive")
    * }
    * // result will be "Number must be positive"
    * }}}
    *
    * @param condition
    *   the condition to ensure
    * @param error
    *   the error to raise if the condition is not met
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    */
  def ensure[E](condition: => Boolean)(error: => E)(using r: Raise[E]): Unit =
    if !condition then Raise.raise(error)

  /** Ensures that the `value` is not null; otherwise, [[Raise.raise]]s a logical failure of type
    * `Error`.
    *
    * <h2>Example</h2>
    * {{{
    * val actual: Int = fold(
    *   { ensureNotNull(null) { "error" } },
    *   error => 43,
    *   value => 42
    * )
    * actual should be(43)
    * }}}
    *
    * @param value
    *   The value that must be non-null.
    * @param raise
    *   A lambda that produces an error of type `Error` when the `value` is null.
    * @param r
    *   The Raise context
    * @tparam B
    *   The type of the value
    * @tparam Error
    *   The type of the logical error
    * @return
    *   The value if it is not null
    */
  def ensureNotNull[E, A](value: A | Null)(error: => E)(using r: Raise[E]): A =
    if value == null then Raise.raise(error)
    else value.asInstanceOf[A]

  /** Catches an exception and raises an error of type `E`. For other exceptions, the exception is
    * rethrown.
    *
    * Example:
    * {{{
    * val result = Raise.run {
    *   Raise.catching {
    *     10 / 0
    *   } {
    *     case ArithmeticException => DivisionByZero
    *   }
    * }
    * // result will be DivisionByZero
    * }}}
    *
    * @param block
    *   the computation that may raise an error
    * @param mapException
    *   the function to apply to the exception
    * @return
    *   the result of the computation
    * @tparam E
    *   the type of error that can be raised
    * @tparam A
    *   the type of the result of the block
    */
  def catching[E, A](block: => A)(mapException: Throwable => E)(using r: Raise[E]): A =
    try {
      block
    } catch {
      case NonFatal(nfex) => Raise.raise(mapException(nfex))
      case ex             => throw ex
    }

  /** Catches an exception of type `E` and lifts it to an error. For other exceptions, the exception
    * is rethrown.
    *
    * Example:
    * {{{
    * val result: Int | ArithmeticException = Raise.run {
    *   Raise.catching[ArithmeticException] {
    *     10 / 0
    *   }
    * }
    * // result will be ArithmeticException
    * }}}
    *
    * @param block
    *   the computation that may raise an error
    * @tparam E
    *   the type of exception to catch and lift to an error
    * @tparam A
    *   the type of the result of the block
    */
  def catching[E <: Throwable, A](block: => A)(using r: Raise[E], E: ClassTag[E]): A =
    try {
      block
    } catch {
      case NonFatal(nfex) =>
        if (nfex.getClass == E.runtimeClass) Raise.raise(nfex.asInstanceOf[E])
        else throw nfex
      case ex => throw ex
    }

  /** Execute the [[Raise]] context function resulting in `A` or any _logical error_ of type
    * `OtherError`, and transform any raised `OtherError` into `Error`, which is raised to the outer
    * [[Raise]].
    *
    * <h2>Example</h2>
    * {{{
    * val actual = either {
    *   withError[Int, String, Int](s => s.length) { raise("error") }
    * }
    * actual should be(Left(5))
    * }}}
    *
    * @param transform
    *   The function to transform the `OtherError` into `Error`
    * @param block
    *   The block to execute
    * @param r
    *   The Raise context
    * @tparam ToError
    *   The type of the transformed logical error
    * @tparam FromError
    *   The type of the logical error that can be raised and transformed
    * @tparam A
    *   The type of the result of the `block`
    * @return
    *   The result of the `block`
    */
  def withError[ToError, FromError, A](transform: FromError => ToError)(
      block: Raise[FromError] ?=> A
  )(using Raise[ToError]): A =
    recover(block) { otherError => Raise.raise(transform(otherError)) }

  /** Utility type alias for mapping errors. */
  type MapError[From, To] = Yaes[Raise.UnsafeMapError[From, To]]

  /** A strategy that allows to map an error to another one. As a strategy, it should be used as a
    * `given` instance. Its behavior is comparable to the [[Raise.withError]] method.
    *
    * <h2>Example</h2>
    * {{{
    * val finalLambda: Raise[Int] ?=> String = {
    *   given MapError[String, Int] = MapError { _.length }
    *   Raise.raise("Oops!")
    * }
    * val result: Int | String = Raise.run(finalLambda)
    * result shouldBe 5
    * }}}
    *
    * @tparam From
    *   The original error type
    * @tparam To
    *   The error type to map to
    */
  trait UnsafeMapError[From, To] extends Unsafe[From] {
    def map(error: From): To
  }

  /** Creates a mapping strategy for errors. */
  object MapError {

    /** Creates a mapping strategy for errors.
      *
      * @param mapper
      *   The function to map the original error to the new error
      * @param outer
      *   The new Raise context
      * @tparam From
      *   The original error type
      * @tparam To
      *   The new error type
      * @return
      *   The mapping strategy
      */
    def apply[From, To](mapper: From => To)(using outer: Raise[To]): MapError[From, To] =
      new Yaes(new UnsafeMapError[From, To] {

        override def raise(error: => From): Nothing = outer.unsafe.raise(map(error))

        override def map(error: From): To = mapper(error)
      })
  }
  
  extension [Error, A](either: Either[Error, A]) {

    /** Lifts an [[Either]] into the [[Raise]] context, and returns the value if the Either is a
      * [[Right]], otherwise raises the error contained in the [[Left]].
      *
      * @param either
      *   The Either to extract the value from
      * @param using
      *   The Raise context
      * @tparam Error
      *   The type of the error contained in the Left
      * @tparam A
      *   The type of the value contained in the Right
      * @return
      *   The value contained in the Right
      */
    inline def value(using Raise[Error]): A = either match {
      case Right(value) => value
      case Left(error)  => Raise.raise(error)
    }
  }

  extension [A](option: Option[A]) {

    /** Lifts an [[Option]] into the [[Raise]] context, and returns the value if the [[Option]] is a
      * [[Some]], otherwise raises the error contained in the [[None]].
      *
      * @param option
      *   The Option to extract the value from
      * @param using
      *   The Raise context
      * @tparam A
      *   The type of the value contained in the Some
      * @return
      *   The value contained in the Some
      */
    inline def value(using Raise[None.type]): A = option match {
      case Some(value) => value
      case None        => Raise.raise(None)
    }
  }

  extension [A](tryValue: Try[A]) {

    /** Lifts a [[Try]] into the [[Raise]] context, and returns the value if the Try is a
      * [[Success]], otherwise raises the error contained in the [[Failure]].
      *
      * @param tryValue
      *   The Try to extract the value from
      * @param using
      *   The Raise context
      * @tparam A
      *   The type of the value contained in the Success
      * @return
      *   The value contained in the Success
      */
    inline def value(using Raise[Throwable]): A = tryValue match {
      case Success(value)     => value
      case Failure(throwable) => Raise.raise(throwable)
    }
  }

  /** An effect that represents the ability to raise an error of type `E`. */
  trait Unsafe[-E] {

    /** Raises an error of type `E`.
      *
      * @param error
      *   the error to raise
      */
    def raise(error: => E): Nothing
  }
}
