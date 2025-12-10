package in.rcard.yaes.instances

import cats.*
import in.rcard.yaes.Raise

import scala.annotation.tailrec

/** Provides Cats typeclass instances for YAES Raise effect. */
object raise extends RaiseInstances

/** Provides Cats typeclass instances for YAES Raise effect.
  *
  * This trait provides MonadError instances that allow Raise computations to work seamlessly
  * with Cats libraries and combinators.
  */
trait RaiseInstances {

  /** Given instance providing MonadError for Raise context functions.
    *
    * This instance allows `Raise[E] ?=> A` to be used anywhere a `MonadError` is expected,
    * enabling integration with Cats-based code.
    *
    * Example:
    * {{{
    * import cats.syntax.all.*
    * import in.rcard.yaes.{Raise, raises}
    * import in.rcard.yaes.instances.raise.given
    *
    * def computation1: Int raises String = Raise.raise("error")
    * def computation2: Int raises String = 42
    *
    * // Can use Cats combinators
    * val result: Int raises String = computation1.handleError(_ => computation2)
    * }}}
    */
  given catsRaiseInstancesForMonadError[E]: MonadError[[A] =>> Raise[E] ?=> A, E] =
    new RaiseMonadError[E]

  /** A MonadError implementation for Raise context functions.
    *
    * This class implements the full MonadError interface for `Raise[E] ?=> A`,
    * allowing Raise computations to be composed using Cats abstractions.
    *
    * @tparam E
    *   The type of the error that can be raised
    */
  class RaiseMonadError[E] extends MonadError[[A] =>> Raise[E] ?=> A, E] {

    /** Lifts an error into the Raise context.
      *
      * @param e
      *   The error to raise
      * @return
      *   A computation that raises the given error
      */
    override def raiseError[A](e: E): Raise[E] ?=> A = Raise.raise(e)

    /** Handles an error with a recovery function.
      *
      * This method uses Raise.fold to catch errors and apply the recovery function,
      * which may itself raise errors.
      *
      * @param fa
      *   The computation that may raise an error
      * @param f
      *   The recovery function to apply to the error
      * @return
      *   A computation that either succeeds with the original value or the recovered value
      */
    override def handleErrorWith[A](fa: Raise[E] ?=> A)(
        f: E => Raise[E] ?=> A
    ): Raise[E] ?=> A = {
      Raise.fold[E, A, A](fa)(error => f(error))(value => value)
    }

    /** Lifts a pure value into the Raise context.
      *
      * @param x
      *   The value to lift
      * @return
      *   A computation that returns the given value
      */
    override def pure[A](x: A): Raise[E] ?=> A = x

    /** Applies a function in the Raise context to a value in the Raise context.
      *
      * @param ff
      *   The function in the Raise context
      * @param fa
      *   The value in the Raise context
      * @return
      *   The result of applying the function to the value
      */
    override def ap[A, B](ff: Raise[E] ?=> A => B)(fa: Raise[E] ?=> A): Raise[E] ?=> B =
      ff(fa)

    /** Sequences two computations, where the second depends on the result of the first.
      *
      * @param fa
      *   The first computation
      * @param f
      *   A function that produces the second computation based on the first result
      * @return
      *   The result of the second computation
      */
    override def flatMap[A, B](fa: Raise[E] ?=> A)(
        f: A => Raise[E] ?=> B
    ): Raise[E] ?=> B = f(fa)

    /** Tail-recursive monadic bind.
      *
      * This method implements stack-safe recursion for monadic operations.
      *
      * @param a
      *   The initial value
      * @param f
      *   A function that produces either a new value to recurse on or a final result
      * @return
      *   The final result
      */
    @tailrec
    final override def tailRecM[A, B](
        a: A
    )(f: A => Raise[E] ?=> Either[A, B]): Raise[E] ?=> B =
      f(a) match {
        case Left(a)  => tailRecM(a)(f)
        case Right(b) => b
      }
  }
}
