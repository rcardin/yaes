package in.rcard.yaes

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import scala.concurrent.ExecutionException
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using
import scala.concurrent.Future
import scala.jdk.FutureConverters._
import java.util.concurrent.CompletableFuture
import scala.concurrent.Await
import java.util.concurrent.CompletionException

/** The `IO` effect represents a side-effecting operation that can be run in a controlled
  * environment. This effect is useful to represent operations that can fail with uncotrolled
  * exceptions.
  */
trait IO extends Effect {

  val executor: Executor
}

trait Executor {
  def submit[A](task: => A): Future[A]
}

class JvmExecutor extends Executor {
  val es: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

  override def submit[A](task: => A): Future[A] = {
    CompletableFuture.supplyAsync(() => task, es).asScala
  }
}

object IO {

  /** Lifts a side-effecting operation into the `IO` effect.
    *
    * @param program
    *   The side-effecting operation to lift
    * @tparam A
    *   The result type of the operation
    * @return
    *   The side-effecting operation lifted into the `IO` effect
    */
  def apply[A](program: => A): IO ?=> A = program

  /** Runs the given side-effecting operation in a controlled environment.
    *
    * @param program
    *   The side-effecting operation to run
    * @return
    *   A `Try` with the result of the operation. If the operation fails, the `Try` will contain the
    *   exception that caused the failure.
    */
  inline def runBlocking[A](program: IO ?=> A): Try[A] = {
    Effect.handle(program)(using blockingHandler)
  }

  inline def run[A](program: IO ?=> A): Future[A] = {
    Effect.handle(program)(using handler)
  }

  def blockingHandler[A] = new Effect.Handler[IO, A, Try[A]] {
    override def handle(program: IO ?=> A): Try[A] = {
      val futureResult: Future[A] = handler.handle(program)
      try {
        // FIXME We can't wait forever!
        Success(Await.result(futureResult, scala.concurrent.duration.Duration.Inf))
      } catch {
        case ex: CompletionException => Failure(ex.getCause)
        case otherEx                 => Failure(otherEx)
      }
    }
  }

  def handler[A] = new Effect.Handler[IO, A, Future[A]] {
    override def handle(program: IO ?=> A): Future[A] = {
      val executor = IO.unsafe.executor
      executor.submit(program(using IO.unsafe))
    }
  }

  /** The unsafe implementation of the `IO` effect. This implementation runs the side-effecting
    * operations in a Java virtual thread per task executor.
    */
  private val unsafe: IO = new IO {

    override val executor: Executor = new JvmExecutor()

    val es: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
  }
}
