package in.rcard.yaes

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import scala.concurrent.ExecutionException
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

/** The `IO` effect represents a side-effecting operation that can be run in a controlled
  * environment. This effect is useful to represent operations that can fail with uncotrolled
  * exceptions.
  */
trait IO extends Effect {

  /** Runs the given side-effecting operation in a controlled environment.
    *
    * @param program
    *   The side-effecting operation to run
    * @tparam A
    *   The result type of the operation
    * @return
    *   A `Try` with the result of the operation. If the operation fails, the `Try` will contain the
    *   exception that caused the failure.
    */
  def submit[A](program: => A): Try[A] // FIXME Maybe we can change with a custom type
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
  inline def run[A](program: IO ?=> A): Try[A] = {
    Effect.handle(program)(using handler)
  }

  def handler[A] = new Effect.Handler[IO, A, Try[A]] {
    override def handle(program: IO ?=> A): Try[A] = {
      IO.unsafe.submit(program(using IO.unsafe))
    }
  }

  /** 
   * The unsafe implementation of the `IO` effect. This implementation runs the side-effecting
   * operations in a virtual thread per task executor.
   */
  private val unsafe: IO = new IO {

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
  }
}
