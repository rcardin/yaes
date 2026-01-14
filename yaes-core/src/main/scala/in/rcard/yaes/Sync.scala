package in.rcard.yaes

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionException
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.jdk.FutureConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

/** The executor service used to run the side-effecting operation. */
trait Executor {

  /** Submits a task to the executor service. The implementation of this method must ensure that the
    * task is executed in a separate thread and that current thread is not blocked.
    *
    * @param task
    *   The task to submit
    * @return
    *   A `Future` with the result of the operation.
    */
  def submit[A](task: => A): Future[A]
}

class JvmExecutor extends Executor {
  val es: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

  override def submit[A](task: => A): Future[A] = {
    CompletableFuture.supplyAsync(() => task, es).asScala
  }
}

type Sync = Yaes[Sync.Unsafe]

/** The `Sync` effect represents a side-effecting operation that can be run in a controlled
  * environment. This effect is useful to represent operations that can fail with uncotrolled
  * exceptions.
  */
object Sync {

  /** Lifts a side-effecting operation into the `Sync` effect.
    *
    * @param program
    *   The side-effecting operation to lift
    * @tparam A
    *   The result type of the operation
    * @return
    *   The side-effecting operation lifted into the `Sync` effect
    */
  def apply[A](program: => A): Sync ?=> A = program

  /** Runs the given side-effecting operation in a controlled environment and blocks the current
    * thread until the operation completes.
    *
    * @param timeout
    *   The timeout for the operation
    * @param program
    *   The side-effecting operation to run
    * @return
    *   A `Try` with the result of the operation. If the operation fails, the `Try` will contain the
    *   exception that caused the failure.
    */
  inline def runBlocking[A](
      timeout: Duration
  )(program: Sync ?=> A)(implicit ec: ExecutionContext): Try[A] = {
    Yaes.handle(program)(using blockingHandler(timeout))
  }

  /** Runs the given side-effecting operation in a controlled environment. The method does not block
    * the current thread.
    *
    * @param program
    *   The side-effecting operation to run
    * @return
    *   A `Future` with the result of the operation.
    */
  inline def run[A](program: Sync ?=> A)(implicit ec: ExecutionContext): Future[A] = {
    Yaes.handle(program)(using handler)
  }

  def blockingHandler[A](timeout: Duration)(implicit ec: ExecutionContext) =
    new Yaes.Handler[Sync.Unsafe, A, Try[A]] {
      override def handle(program: Yaes[Sync.Unsafe] ?=> A): Try[A] = {
        val futureResult: Future[A] = handler.handle(program)
        Try {
          Await.result(futureResult, timeout)
        }
      }
    }

  def handler[A](implicit ec: ExecutionContext) = new Yaes.Handler[Sync.Unsafe, A, Future[A]] {
    override def handle(program: Yaes[Sync.Unsafe] ?=> A): Future[A] = {
      val executor = Sync.unsafe.executor
      executor.submit(program(using new Yaes(Sync.unsafe))).transform {
        case s @ Success(_) => s
        case Failure(ex) =>
          ex match {
            case e: CompletionException => Failure(e.getCause)
            case otherEx                => Failure(otherEx)
          }
      }
    }
  }

  /** The unsafe implementation of the `Sync` effect. This implementation runs the side-effecting
    * operations in a Java virtual thread per task executor.
    */
  private val unsafe = new Unsafe {
    override val executor: Executor = new JvmExecutor()
  }

  /** The unsafe flavor of the `Sync` effect.
    */
  trait Unsafe {
    val executor: Executor
  }
}
