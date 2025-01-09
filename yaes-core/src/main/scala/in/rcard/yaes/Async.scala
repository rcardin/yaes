package in.rcard.yaes

import java.util.concurrent.StructuredTaskScope
import java.util.concurrent.StructuredTaskScope.{ShutdownOnFailure, Subtask}
import scala.concurrent.duration.Duration
import java.util.concurrent.ExecutionException
import scala.concurrent.Promise
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

trait StructuredScope {
  def delay(duration: Duration): Unit
  def fork[A](block: => A): Fiber[A]
}

// FIXME Should we return an Async ?=> A instead of an A?
trait Fiber[A] {
  def value: A
  def join(): Unit
}

class JvmFiber[A](private val promise: Future[A]) extends Fiber[A] {
  override def value: A = promise.get()

  override def join(): Unit = promise.get()
}

class JvmStructuredScope(private val scope: StructuredTaskScope[Any]) extends StructuredScope {
  override def delay(duration: Duration): Unit = {
    Thread.sleep(duration.toMillis)
  }

  override def fork[A](block: => A): Fiber[A] = {
    val promise = CompletableFuture[A]()
    val task = scope.fork(() => {
      val innerScope = new ShutdownOnFailure()
      try {
        val innerTask: StructuredTaskScope.Subtask[A] = innerScope.fork(() => block)
        innerScope
          .join()
          .throwIfFailed((trowable: Throwable) => {
            trowable match {
              // FIXME Should we complete the promise with the cause?
              case exex: ExecutionException => exex.getCause
              case _                        => throw trowable
            }
          })
        promise.complete(innerTask.get())
      } finally {
        innerScope.close()
      }
    })
    new JvmFiber[A](promise)
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
