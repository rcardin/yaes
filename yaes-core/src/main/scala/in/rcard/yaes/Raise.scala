package in.rcard.yaes

import scala.util.control.{ControlThrowable, NoStackTrace}

trait TypedError[-E] {
  def raise(error: E): Nothing
}

type Raise[E] = Effect[TypedError[E]]

private[yaes] case class Raised[E](original: E)
extends ControlThrowable
  with NoStackTrace

private[yaes] class DefaultTypedError extends TypedError[Any]:
  def raise(e: Any): Nothing = throw Raised(e)

object Raise {
  def apply[E, A](block: => A): Raise[E] ?=> A = block

  def raise[E](error: E)(using eff: Raise[E]): Nothing = eff.sf.raise(error)

  def run[E, A](block: Raise[E] ?=> A): A | E = {
    given eff: Effect[TypedError[E]] = new Effect(new DefaultTypedError)

    try {
      block(using eff)
    } catch {
      case Raised(e) => e.asInstanceOf[E]
    }
  }
}