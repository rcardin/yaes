package in.rcard.yaes

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import scala.concurrent.ExecutionException
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

trait SideEffect {
  def submit[A](task: => A): Try[A] // FIXME Maybe we can change with a custom type
}

type IO = Effect[SideEffect]

object IO {
  def apply[A](block: => A): IO ?=> A = block

  def run[A](block: IO ?=> A): Try[A] = {
    IO.unsafe.sf.submit(block(using IO.unsafe))
  }

  val unsafe: IO = new Effect(new SideEffect {

    val es: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    override def submit[A](task: => A): Try[A] = {
      val futureResult = es.submit(() => task)
      try {
        Success(futureResult.get())
      } catch {
        case exex: ExecutionException => Failure(exex.getCause)
        case throwable                => Failure(throwable)
      }
    }
  })
}
