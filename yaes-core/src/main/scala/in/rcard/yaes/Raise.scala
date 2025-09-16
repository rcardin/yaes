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
import scala.collection.mutable.ArrayBuffer
import scala.util.boundary.Label

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

  infix type raises[A, Error] = Raise[Error] ?=> A

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

  /** Accumulate the errors obtained by executing the `transform` over every element of `iterable`.
    *
    * <h2>Example</h2>
    * {{{
    * val block: List[Int] raises List[String] = Raise.mapAccumulating(List(1, 2, 3, 4, 5)) {
    *   _ + 1
    * }
    * val actual = Raise.fold(
    *   block,
    *   error => fail(s"An error occurred: $error"),
    *   identity
    * )
    * actual shouldBe List(2, 3, 4, 5, 6)
    * }}}
    *
    * @param iterable
    *   The collection of elements to transform
    * @param transform
    *   The transformation to apply to each element that can raise an error of type `Error`
    * @param r
    *   The Raise context
    * @tparam Error
    *   The type of the logical error that can be raised
    * @tparam A
    *   The type of the elements in the `iterable`
    * @tparam B
    *   The type of the transformed elements
    * @return
    *   A list of transformed elements
    */
  inline def mapAccumulating[E, A, B](iterable: Iterable[A])(
      transform: A => (Raise[E] ?=> B)
  )(using RaiseAcc[E]): List[B] = {
    val errors  = collection.mutable.ArrayBuffer.empty[E]
    val results = collection.mutable.ArrayBuffer.empty[B]
    iterable.foreach { a =>
      Raise.fold(
        transform(a)
      )(error => errors += error)(result => results += result)
    }
    if errors.isEmpty then results.toList
    else Raise.raise(errors.toList)
  }

  /** Transform every element of `iterable` using the given `transform`, or accumulate all the
    * occurred errors using `combine`.
    *
    * <h2>Example</h2>
    * {{{
    * case class Errors(val errors: List[String])
    *   def combineErrors(error1: Errors, error2: Errors): Errors =
    *     Errors(error1.errors ++ error2.errors)
    *
    * val block: List[Int] raises Errors =
    *   Raise.mapAccumulating(List(1, 2, 3, 4, 5), combineErrors) { value =>
    *       if (value % 2 == 0) {
    *         Raise.raise(Errors(List(value.toString)))
    *       } else {
    *         value
    *       }
    *     }
    *
    * val actual = Raise.fold(
    *   block,
    *   identity,
    *   identity
    * )
    *
    * actual shouldBe Errors(List("2", "4"))
    * }}}
    *
    * @param iterable
    *   The collection of elements to transform
    * @param combine
    *   The function to combine the errors
    * @param transform
    *   The transformation to apply to each element that can raise an error of type `Error`
    * @param r
    *   The Raise context
    * @tparam Error
    *   The type of the logical error that can be raised
    * @tparam A
    *   The type of the elements in the `iterable`
    * @tparam B
    *   The type of the transformed elements
    * @return
    *   A list of transformed elements
    */
  inline def mapAccumulating[E, A, B](
      iterable: Iterable[A],
      inline combine: (E, E) => E
  )(
      inline transform: A => (Raise[E] ?=> B)
  )(using Raise[E]): List[B] = {
    val errors  = collection.mutable.ArrayBuffer.empty[E]
    val results = collection.mutable.ArrayBuffer.empty[B]
    iterable.foreach { a =>
      Raise.fold(transform(a))(error => errors += error)(result => results += result)
    }
    if errors.isEmpty then results.toList
    else Raise.raise(errors.reduce(combine))
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

  // --- ACCUMULATION ----

  /** A type alias for accumulating errors into a list.
    */
  type RaiseAcc[Error] = Raise[List[Error]]

  /** The scope needed to accumulate errors using the [[accumulate]] function
    * @tparam Error
    *   The type of the errors to accumulate
    */
  class AccumulateScope[Error] {
    private[yaes] val _errors        = ArrayBuffer.empty[Error]
    def errors: List[Error]          = _errors.toList
    def addError(error: Error): Unit = _errors += error
    def hasErrors: Boolean           = _errors.nonEmpty
  }

  /** Conversion from a [[LazyValue]] to its contained value. If the [[LazyValue]] contains errors,
    * it will raise them. There is no need to import this conversion as it is provided by default
    * inside the [[AccumulateScope]] scope.
    */
  private[yaes] case object AccumulationError extends ControlThrowable with NoStackTrace
  given lazyValueConversion[A]: Conversion[LazyValue[A], A] with
    def apply(toConvert: LazyValue[A]): A = {
      toConvert match {
        case LazyValue.Value(value) => value
        case LazyValue.Empty        => throw AccumulationError
      }
    }

  /** Accumulates the errors of the executions in the `block` lambda and raises all of them if any
    * error is found. In detail, the `block` lambda must be a series of statements using the
    * [[accumulating]] function to accumulate possible raised errors.
    *
    * <h2>Example</h2>
    * {{{
    * def validateName(name: String): String raises String = {
    *   ensure(name.nonEmpty)("Name cannot be empty")
    *   name
    * }
    * def validateAge(age: Int): Int raises String = {
    *   ensure(age >= 0)("Age cannot be negative")
    *   age
    * }
    *
    * val person: Person raises List[String] = accumulate {
    *   val name = accumulating { validateName("") }
    *   val age  = accumulating { validateAge(-1) }
    *   Person(name, age)
    * }
    * }}}
    *
    * Errors are accumulated in the order they are raised the first time one of the accumulated
    * values is accessed.
    *
    * @param block
    *   The block of code that can raise multiple errors
    * @tparam Error
    *   The type of the errors to accumulate
    * @tparam A
    *   The type of the value to return if no errors are raised
    * @return
    *   The value of the block if no errors are raised
    */
  inline def accumulate[Error, A](
      block: AccumulateScope[Error] ?=> A
  ): RaiseAcc[Error] ?=> A = {

    import scala.language.implicitConversions

    val accScope: AccumulateScope[Error] = AccumulateScope()
    try {
      val result: A = block(using accScope)
      result
    } catch {
      case AccumulationError =>
        Raise.raise(accScope.errors)
    }
  }

  /** Represents a value that can be either a value or a list of errors raised inside an
    * [[accumulate]] block by the [[accumulating]] function.
    *
    * @see
    *   [[lazyValueConversion]]
    */
  sealed trait LazyValue[+A]
  object LazyValue {
    case class Value[A](value: A) extends LazyValue[A]
    case object Empty             extends LazyValue[Nothing]
  }

  /** Accumulates the errors of the executions in the `block` lambda and returns a [[LazyValue]]
    * that can be either a value or a list of errors. The function is intended to be used inside an
    * [[accumulate]] block.
    *
    * @see
    *   [[accumulate]]
    */
  inline def accumulating[Error, A](
      inline block: Raise[Error] ?=> A
  )(using scope: AccumulateScope[Error]): LazyValue[A] = {
    Raise.recover({
      val a = block
      LazyValue.Value(value = a)
    }) { error =>
      scope.addError(error)
      LazyValue.Empty
    }
  }

  /** A type class to convert a container `MV[_]` of [[LazyValue]]s to a container of values. The
    * error raised during the conversion must be accumulated into a container `ME[_]` of errors.
    * @tparam ME
    *   The type of the container of errors
    * @tparam MV
    *   The type of the container of [[LazyValue]]s
    */
  trait LazyValuesConverter[ME[_], MV[_]] {
    private type RaiseM[Error] = Raise[ME[Error]]
    def convert[Error: RaiseM, A](convertible: MV[LazyValue[A]]): MV[A]
  }

  /** A type class to convert a container `M[_]` of [[LazyValue]]s to a container of values
    * accumulating errors into a [[RaiseAcc]] container.
    * @tparam M
    *   The type of the container of [[LazyValue]]s
    */
  trait RaiseAccLazyValuesConverter[M[_]] extends LazyValuesConverter[List, M] {
    def convert[Error: RaiseAcc, A](convertible: M[LazyValue[A]]): M[A]
  }

  /** A type class instance to convert a container `List[LazyValue[Error, A]]` to a container
    * `List[A]` accumulating errors into a [[RaiseAcc]] container.
    *
    * @see
    *   [[LazyValue]]
    */
  given listRaiseAccLazyValuesConverter: RaiseAccLazyValuesConverter[List] with
    def convert[Error: RaiseAcc, A](convertible: List[LazyValue[A]]): List[A] = {
      convertible.map {
        case LazyValue.Value(value) => value
        case LazyValue.Empty        => throw AccumulationError
      }
    }

  /** Implicit conversion between a container `M[LazyValue[Error, A]]` and a container `M[A]`
    * accumulating errors into a [[RaiseAcc]] container.
    *
    * NOTE: Due to changes in Scala 3.7+ implicit resolution (specifically the restriction of
    * implicit args to using clauses), these conversions don't work reliably during collection
    * construction. The [[accumulate]] function now handles this conversion explicitly at runtime.
    *
    * <h2>Example</h2>
    * {{{
    * val block: List[Int] raises List[String] = accumulate {
    *   List(1, 2, 3, 4, 5).map[Accumulation.Value[String, Int]] { i =>
    *     accumulating {
    *       if (i % 2 == 0) {
    *         Raise.raise(i.toString)
    *       } else {
    *         i
    *       }
    *     }
    *   }
    * }
    *
    * val actual = Raise.fold(
    *   block,
    *   identity,
    *   identity
    * )
    *
    * actual shouldBe List("2", "4")
    * }}}
    *
    * @see
    *   [[LazyValue]]
    */
  given raiseAccLazyValuesConversion[Error: RaiseAcc, A, M[_]: RaiseAccLazyValuesConverter]
      : Conversion[M[LazyValue[A]], M[A]] with
    def apply(convertible: M[LazyValue[A]]): M[A] = {
      println("raiseAccLazyValuesConversion")
      summon[RaiseAccLazyValuesConverter[M]].convert(convertible)
    }

  /** Implement the `raise` method to throw a [[Traced]] exception with the original error to trace.
    */
  private[yaes] class UnsafeTrace[E] extends Unsafe[E] {

    override def raise(error: => E): Nothing = throw Traced(error)
  }

  /** The exception that wraps the original error in case of tracing. It contains a full stack
    * trace.
    * @param original
    *   The original error to trace
    * @tparam E
    *   The type of the error to trace
    */
  case class Traced[E](original: E) extends RuntimeException

  /** Defines how to trace an error. The [[trace]] method represent the behavior to trace the error.
    * Use the type class instance with the [[Raise.traced]] DSL.
    *
    * <h2>Example</h2>
    * {{{
    * given TraceWith[String] = trace => {
    *   trace.printStackTrace()
    * }
    * val lambda: Int raises String = traced {
    *   raise("Oops!")
    * }
    * val actual: String | Int = Raise.run(lambda)
    * actual shouldBe "Oops!"
    * }}}
    *
    * @tparam Error
    *   The type of the error to trace
    */
  trait TraceWith[Error] {
    def trace(traced: Traced[Error]): Unit
  }

  given defaultTracing[Error]: TraceWith[Error] with
    def trace(traced: Traced[Error]): Unit =
      traced.printStackTrace()

  /** Add tracing to a block of code that can raise a logical error. In detail, the logical error is
    * wrapped inside a [[Traced]] exception and processed by the [[TraceWith]] strategy instance.
    * Please, be aware that adding tracing to an error can have a performance impact since a fat
    * exception with a full stack trace is created.
    *
    * <h2>Example</h2>
    * {{{
    * given TraceWith[String] = trace => {
    *   trace.printStackTrace()
    * }
    * val lambda: Int raises String = traced {
    *   raise("Oops!")
    * }
    * val actual: String | Int = Raise.run(lambda)
    * actual shouldBe "Oops!"
    * }}}
    *
    * @param block
    *   The block of code to execute that can raise an error
    * @param tracing
    *   The strategy to process the traced error
    * @tparam Error
    *   The type of the logical error that can be raised by the `block` lambda
    * @tparam A
    *   The type of the result of the execution of `block` lambda
    * @return
    *   The original block wrapped into a traced block
    */
  inline def traced[Error, A](
      inline block: Raise[Error] ?=> A
  )(using inline tracing: TraceWith[Error]): Raise[Error] ?=> A = {
    try {
      given tracedRaise: Raise[Error] = new Yaes(new UnsafeTrace[Error])
      block
    } catch
      case traced: Traced[_] =>
        tracing.trace(traced.asInstanceOf[Traced[Error]])
        Raise.raise(traced.original.asInstanceOf[Error])
  }
}
