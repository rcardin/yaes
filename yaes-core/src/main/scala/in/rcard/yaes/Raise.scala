package in.rcard.yaes

import scala.util.control.{ControlThrowable, NoStackTrace}

trait TypedError[-E] {
  def raise(error: => E): Nothing
}

type Raise[E] = Effect[TypedError[E]]

private[yaes] case class Raised[E](original: E) extends ControlThrowable with NoStackTrace

private[yaes] class DefaultTypedError extends TypedError[Any]:
  def raise(e: => Any): Nothing = throw Raised(e)

object Raise {
  def apply[E, A](block: => A): Raise[E] ?=> A = block

  def raise[E, A](error: E)(using eff: Raise[E]): Nothing = eff.sf.raise(error)

  def fold[E, A, B](block: Raise[E] ?=> A)(onError: E => B)(onSuccess: A => B): B = {
    given eff: Effect[TypedError[E]] = new Effect(new DefaultTypedError)
    try {
      onSuccess(block)
    } catch {
      case Raised(e) => onError(e.asInstanceOf[E])
    }
  }

  def run[E, A](block: Raise[E] ?=> A): A | E = fold(block)(identity)(identity)

  def recover[E, A](block: Raise[E] ?=> A)(recoverWith: E => A): A =
    fold(block)(onError = recoverWith)(onSuccess = identity)

  def withDefault[E, A](default: => A)(block: Raise[E] ?=> A): A = recover(block)(_ => default)
}
