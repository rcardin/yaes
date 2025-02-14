package in.rcard.yaes.ex

object ThirdStrike {

  trait Effect

  class Handler[-E <: Effect](effect: E)

  trait Raise[E] extends Effect {
    def raise(e: E): Nothing
  }
  trait Random extends Effect {
    def nextBool(): Boolean
  }

  object Raise {
    def run[E, A](block: Handler[Raise[E]] ?=> A): A = {
      block(using
        new Handler(new Raise[E] {
          override def raise(e: E): Nothing = throw new RuntimeException(e.toString)
        })
      )
    }
  }

  object Random {

    // def nextBool()(using h: Handler[Random]): Boolean = h.effect.nextBool()

    def run[A](block: Handler[Random] ?=> A): A = {
      block(using
        new Handler(new Random {
          override def nextBool(): Boolean = true
        })
      )
    }
  }

  // @main def main(): Unit = {
  //   val program: Handler[Raise[String] & Random] ?=> Int = {
  //     42
  //   }
  //   val result: Handler[Random] ?=> Int = Raise.run(program)
  // }
}
