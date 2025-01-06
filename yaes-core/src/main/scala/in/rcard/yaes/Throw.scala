package in.rcard.yaes

import scala.reflect.ClassTag
import scala.util.control.NonFatal

// FIXME It should be a wrapper around NonFatal exceptions
trait Ex[-E <: Throwable]

private[yaes] class DefaultEx[-E <: Throwable] extends Ex[E] {}

type Throw[E <: Throwable, A] = Effect[Ex[E]] ?=> A

object Throw {
  def apply[E <: Throwable, A](block: => A): Throw[E, A] = block

  // FIXME This control should be check at compile time
  def run[E <: Throwable, A](block: Throw[E, A])(using E: ClassTag[E]): A | E = {
    given eff: Effect[Ex[E]] = new Effect(new DefaultEx[E])

    try {
      block(using eff)
    } catch {
      case nonFatal if NonFatal(nonFatal) =>
        if (nonFatal.getClass == E.runtimeClass)
          nonFatal.asInstanceOf[E]
        else
          throw nonFatal
      case fatal => throw fatal
    }
  }
}
