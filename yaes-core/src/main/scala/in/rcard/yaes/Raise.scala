package in.rcard.yaes

import scala.reflect.ClassTag
import scala.util.control.ControlThrowable
import scala.util.control.NoStackTrace
import scala.util.control.NonFatal
import scala.util.boundary.break
import scala.util.boundary

trait Raise[-E] extends Effect {
  def raise(error: => E): Nothing
}

object Raise {
  def apply[E, A](block: => A): Raise[E] ?=> A = block

  def raise[E, A](error: E)(using eff: Raise[E]): Nothing = eff.raise(error)

  def fold[E, A, B](block: Raise[E] ?=> A)(onError: E => B)(onSuccess: A => B): B = {
    val handler = new Effect.Handler[Raise[E], A, B] {

      override def handle(program: (Raise[E]) ?=> A): B = {
        boundary {
          given eff: Raise[E] = new Raise[E] {
            def raise(error: => E): Nothing =
              break(onError(error))
          }
          onSuccess(block)
        }
      }
    }
    Effect.handle(block)(using handler)
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
