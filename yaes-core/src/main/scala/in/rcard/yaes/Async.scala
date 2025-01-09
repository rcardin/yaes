package in.rcard.yaes

import java.util.concurrent.StructuredTaskScope
import java.util.concurrent.StructuredTaskScope.{ShutdownOnFailure, Subtask}
import scala.concurrent.duration.Duration
import java.util.concurrent.ExecutionException
import scala.concurrent.Promise
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.CancellationException

trait StructuredScope {
  def delay(duration: Duration): Unit
  def fork[A](block: => A): Fiber[A]
}

// FIXME Should we return an Async ?=> A instead of an A?
trait Fiber[A] {
  def value: A
  def join(): Unit
  def cancel(): Unit
}

class JvmFiber[A](private val promise: Future[A], private val forkedThread: Future[Thread])
    extends Fiber[A] {

  override def value: A = promise.get()

  override def join(): Unit =
    try {
      promise.get()
    } catch {
      case cancellationEx: CancellationException => ()
    }

  override def cancel(): Unit = {
    // We'll wait until the thread is forked
    forkedThread.get().interrupt()
  }
}

class JvmStructuredScope(private val scope: StructuredTaskScope[Any]) extends StructuredScope {
  override def delay(duration: Duration): Unit = {
    Thread.sleep(duration.toMillis)
  }

  override def fork[A](block: => A): Fiber[A] = {
    val promise      = CompletableFuture[A]()
    val forkedThread = CompletableFuture[Thread]()
    scope.fork(() => {
      val innerScope = new ShutdownOnFailure()
      try {
        val innerTask: StructuredTaskScope.Subtask[A] = innerScope.fork(() => {
          forkedThread.complete(Thread.currentThread())
          block
        })
        innerScope.join()
        if (innerTask.state() == Subtask.State.FAILED) {
          innerTask.exception() match {
            case ie: InterruptedException => promise.cancel(true)
            case exex: ExecutionException => throw exex.getCause
            case throwable                => throw throwable
          }
        } else {
          promise.complete(innerTask.get())
        }
      } finally {
        innerScope.close()
      }
    })
    new JvmFiber[A](promise, forkedThread)
  }
}

type Async = Effect[StructuredScope]

object Async {
  def apply[A](block: => A): Async ?=> A = block

  def delay(duration: Duration)(using async: Async): Unit = {
    async.sf.delay(duration)
  }

  def fork[A](block: => A)(using async: Async): Fiber[A] =
    async.sf.fork(block)

  def run[A](block: Async ?=> A): A = {
    val loomScope = new ShutdownOnFailure()

    given effect: Effect[StructuredScope] = Effect(JvmStructuredScope(loomScope))

    try {
      val mainTask = loomScope.fork(() => {
        block
      })
      loomScope.join().throwIfFailed(identity)
      mainTask.get()
    } finally {
      loomScope.close()
    }
  }
}
