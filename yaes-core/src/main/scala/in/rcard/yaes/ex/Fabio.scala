package in.rcard.yaes.ex

object Fabio {

  trait Handler[+F] {
    def handle[A, R](eff: Handler[F & R] ?=> A): Handler[R] ?=> A
  }

  type Effect[F, A] = Handler[F] ?=> A

  extension [F, A](eff: Effect[F, A]) {
    inline def map[B](inline f: A => B): Effect[F, B]                = f(eff)
    inline def flatMap[B](inline f: A => Effect[F, B]): Effect[F, B] = f(eff)
  }

  trait IO
  object IO {
    def apply[A](block: => A): Effect[IO, A] = block
    // def run[A](block: Effect[IO] ?=> A): A = {
    //   block(using new IO {})
    // }

    val handler: Handler[IO] = new Handler[IO] {
      override def handle[A, R](block: (Handler[IO & R]) ?=> A): (Handler[R]) ?=> A = {
        block(using this)
      }
    }
  }
  trait Throw
  object Throw {
    def apply[A](block: => A): Effect[Throw, A] = block
    // def run[A](block: Throw ?=> A): A = {
    //   block(using new Throw {})
    // }

    val handler: Handler[Throw] = new Handler[Throw] {

      override def handle[A, R](eff: (Handler[Throw & R]) ?=> A): (Handler[R]) ?=> A = ???

      def handle[A](block: Handler[Throw] ?=> A): A =
        block(using this)
    }
  }

  // Handler[IO] & Handler[Throw] ?=> Int
  val a: Effect[IO & Throw, Int] = IO { Throw(1) }

  val b: Effect[IO, Int] = Throw.handler.handle(a)

  given anyHandler: Handler[Any] = new Handler[Any] {

    override def handle[A, R](eff: (Handler[Any & R]) ?=> A): (Handler[R]) ?=> A = ???

  }
  val c: Int = IO.handler.handle(b)

  // val c: Effect[IO & Throw, Int] = flatMap(a)(_ => b)

  // val result: Throw ?=> Int = IO.run(d)
}
