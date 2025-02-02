package in.rcard.yaes

import scala.util.control.{ControlThrowable, NoStackTrace, NonFatal}
import scala.reflect.ClassTag

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

  def either[E, A](block: Raise[E] ?=> A): Either[E, A] =
    fold(block)(onError = Left(_))(onSuccess = Right(_))

  def option[E, A](block: Raise[E] ?=> A): Option[A] =
    fold(block)(onError_ => None)(onSuccess = Some(_))

  def nullable[E, A](block: Raise[E] ?=> A): A | Null =
    fold(block)(onError_ => null)(onSuccess = identity)

  def ensure[E](condition: => Boolean)(error: => E)(using r: Raise[E]): Unit =
    if !condition then Raise.raise(error)

  def catching[E, A](block: => A)(mapException: Throwable => E)(using r: Raise[E]): A =
    try {
      block
    } catch {
      case NonFatal(nfex) => Raise.raise(mapException(nfex))
      case ex             => throw ex
    }

  def catching[E <: Throwable, A](block: => A)(using r: Raise[E], E: ClassTag[E]): A =
    try {
      block
    } catch {
      case NonFatal(nfex) =>
        if (nfex.getClass == E.runtimeClass) Raise.raise(nfex.asInstanceOf[E])
        else throw nfex
      case ex => throw ex
    }
}
