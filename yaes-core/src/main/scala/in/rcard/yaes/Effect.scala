package in.rcard.yaes

/** Represents an effect that handles the execution of a side-effecting operation.
  * @param sf
  *   The side-effecting operation to be executed.
  * @tparam F
  *   The type of the side-effecting operation.
  */
class Effect[F](val sf: F)

extension [F, A](inline eff: Effect[F] ?=> A) {
  inline def map[B](inline f: A => B): Effect[F] ?=> B                   = eff.flatMap(a => f(a))
  inline def flatMap[B](inline f: A => Effect[F] ?=> B): Effect[F] ?=> B = f(eff)
}

object Effect {
  def handle[F, A](program: Effect[F] ?=> A) = new WithHandler[F, A] {
    override def `with`(handler: Handler[F]): A = {
      program(using new Effect(handler.unsafe))
    }
  }

  trait WithHandler[F, A] {
    def `with`(handler: Handler[F]): A
  }

  trait Handler[F] {
    val unsafe: F
  }
}
