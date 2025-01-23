package in.rcard.yaes

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import scala.concurrent.ExecutionException
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

class SideEffect(val es: ExecutorService)

type IO = Effect[SideEffect]

object IO {
  def apply[A](block: => A): IO ?=> A = block

  def run[A](block: IO ?=> A): Try[A] = {
    // FIXME For now, we can create it here, but we want to be share it across all the IO instances
    Using(Executors.newVirtualThreadPerTaskExecutor()) { executor =>
      given se: Effect[SideEffect] = new Effect(new SideEffect(executor))

      val futureResult = executor.submit(() => block(using se))
      futureResult.get()
    }.fold(
      {
        case exex: ExecutionException => Failure(exex.getCause)
        case throwable                => Failure(throwable)
      },
      result => Success(result)
    )
  }
}
