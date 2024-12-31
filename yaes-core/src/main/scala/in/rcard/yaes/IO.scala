package in.rcard.yaes

import java.util.concurrent.{ExecutorService, Executors}
import scala.util.Using

class SideEffect(val es: ExecutorService)

type IO[A] = Effect[SideEffect] ?=> A

object IO {
  def apply[A](block: => A): IO[A] = block

  def run[A](block: IO[A]): A | Throwable = {
    Using(Executors.newVirtualThreadPerTaskExecutor()) { executor =>
      given se: Effect[SideEffect] = new Effect(new SideEffect(executor))

      block(using se)
    }.fold(
      throwable => throwable,
      result => result
    )
  }
}