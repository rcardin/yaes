package in.rcard.yaes

import scala.util.control.{ControlThrowable, NoStackTrace}

trait TypedError[-E] {
  def raise[A](error: => E): A
}

type Raise[E] = Effect[TypedError[E]]

private[yaes] case class Raised[E](original: E)
extends ControlThrowable
  with NoStackTrace

private[yaes] class DefaultTypedError extends TypedError[Any]:
  def raise[A](e: => Any): Nothing = throw Raised(e)

object Raise {
  def apply[E, A](block: => A): Raise[E] ?=> A = block

  def raise[E, A](error: E)(using eff: Raise[E]): A = eff.sf.raise(error)

  def run[E, A](block: Raise[E] ?=> A): A | E = {
    given eff: Effect[TypedError[E]] = new Effect(new DefaultTypedError)

    try {
      block(using eff)
    } catch {
      case Raised(e) => e.asInstanceOf[E]
    }
  }

  def withDefault[E, A](default: => A)(block: Raise[E] ?=> A): A = {
    block(using new Effect(new TypedError[E] {
      override def raise[A1](e: => E): A1 = default.asInstanceOf[A1]
    }))
  }
}