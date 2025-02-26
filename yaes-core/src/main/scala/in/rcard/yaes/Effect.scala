package in.rcard.yaes

/** Represents an effect that handles the execution of a side-effecting operation.
  * @param sf
  *   The side-effecting operation to be executed.
  * @tparam F
  *   The type of the side-effecting operation.
  */
trait Effect

extension [F <: Effect, A](inline eff: F ?=> A) {
  inline def flatMap[B](inline f: A => F ?=> B): F ?=> B = f(eff)
  inline def map[B](inline f: A => B): F ?=> B           = eff.flatMap(a => f(a))
}

object Effect {

  def handle[F <: Effect, A](program: F ?=> A)(using handler: Handler[F]) = new WithEffect[F, A] {
    override def `with`(effect: F): A = {
      handler.handle(program)(using effect)
    }
  }

  trait Handler[F <: Effect] {
    def handle[A](program: F ?=> A)(using eff: F): A
  }

  trait WithEffect[F <: Effect, A] {
    def `with`(effect: F): A
  }
}
