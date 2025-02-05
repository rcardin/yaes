package in.rcard.yaes.ex

object Exp {

  type Yaes[F, A] = F ?=> A

  extension [F, A](eff: F ?=> A) {
    inline def map[F2, B](inline f: A => F2 ?=> B): (F, F2) ?=> B     = f(eff)
    inline def flatMap[F2, B](inline f: A => F2 ?=> B): (F, F2) ?=> B = f(eff)
  }

  trait YRandom {
    def nextInt(): Int
    def nextBoolean(): Boolean
    def nextDouble(): Double
    def nextLong(): Long
  }

  object YRandom {
    def apply[A](block: => A): Yaes[YRandom, A] = block

    def nextInt(using r: YRandom): Int         = r.nextInt()
    def nextBoolean(using r: YRandom): Boolean = r.nextBoolean()
    def nextDouble(using r: YRandom): Double   = r.nextDouble()
    def nextLong(using r: YRandom): Long       = r.nextLong()

    def run[A](block: YRandom ?=> A): A = {
      block(using
        new YRandom {
          def nextInt(): Int         = scala.util.Random.nextInt()
          def nextLong(): Long       = scala.util.Random.nextLong()
          def nextBoolean(): Boolean = scala.util.Random.nextBoolean()
          def nextDouble(): Double   = scala.util.Random.nextDouble()
        }
      )
    }
  }

  trait YConsole {
    def print(text: String): Unit
    def println(text: String): Unit
  }

  object YConsole {
    def apply[A](block: => A): Yaes[YConsole, A] = block

    def print(text: String)(using c: YConsole): Unit   = c.print(text)
    def println(text: String)(using c: YConsole): Unit = c.println(text)

    def run[A](block: YConsole ?=> A): A = {
      block(using
        new YConsole {
          def print(text: String): Unit   = scala.Console.print(text)
          def println(text: String): Unit = scala.Console.println(text)
        }
      )
    }
  }

}
