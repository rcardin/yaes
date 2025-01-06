package in.rcard.yaes

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionException
import scala.util.Using

class SideEffect(val es: ExecutorService)

type IO[A] = Effect[SideEffect] ?=> A

object IO {
  def apply[A](block: => A): IO[A] = block

  def run[A](block: IO[A]): A | Throwable = {
    // FIXME For now, we can create it here, but we want to be share it across all the IO instances
    Using(Executors.newVirtualThreadPerTaskExecutor()) { executor =>
      given se: Effect[SideEffect] = new Effect(new SideEffect(executor))

      val futureResult = executor.submit(() =>
        block(using se)
      )
      futureResult.get()
    }.fold(
      {
        case exex: ExecutionException => exex.getCause
        case throwable => throwable
      },
      result => result
    )
  }
}