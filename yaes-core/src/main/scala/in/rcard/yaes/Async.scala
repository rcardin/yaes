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
  def fork[A](name: String)(block: => A): Fiber[A]
}

// FIXME Should we return an Async ?=> A instead of an A?
trait Fiber[A] {
  def value: Throw[FiberCancellationException] ?=> A
  def join(): Unit
  def cancel(): Unit
}

class FiberCancellationException extends Exception

class JvmFiber[A](private val promise: Future[A], private val forkedThread: Future[Thread])
    extends Fiber[A] {

  override def value: Throw[FiberCancellationException] ?=> A = try {
    promise.get()
  } catch {
    case cancellationEx: CancellationException => throw FiberCancellationException()
  }

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

class JvmStructuredScope(
    private val scopes: scala.collection.mutable.Map[Long, StructuredTaskScope[Any]]
) extends StructuredScope {
  override def delay(duration: Duration): Unit = {
    Thread.sleep(duration.toMillis)
  }

  override def fork[A](name: String)(block: => A): Fiber[A] = {
    val promise      = CompletableFuture[A]()
    val forkedThread = CompletableFuture[Thread]()
    println(s"Parent scope: ${scopes.last}")
    scopes(Thread.currentThread().threadId).fork(() => {
      val innerScope = new ShutdownOnFailure()
      try {
        val innerTask: StructuredTaskScope.Subtask[A] = innerScope.fork(() => {
          println(s"Forking: '$name' using '$innerScope'")
          val currentThread = Thread.currentThread()
          scopes.addOne(currentThread.threadId -> innerScope)
          forkedThread.complete(currentThread)
          block
        })
        innerScope.join()
        if (innerTask.state() != Subtask.State.SUCCESS) {
          println("Task failed:" + name)
          innerTask.exception() match {
            case ie: InterruptedException =>
              promise.cancel(true)
              println("Task cancelled:" + name)
            case exex: ExecutionException => throw exex.getCause
            case throwable                => throw throwable
          }
        } else {
          promise.complete(innerTask.get())
        }
      } finally {
        println("Closing:" + name)
        scopes.remove(Thread.currentThread().threadId)
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

  def fork[A](name: String)(block: => A)(using async: Async): Fiber[A] =
    async.sf.fork(name)(block)

  // FIXME Maybe we can think to a better default name for the fiber
  def fork[A](block: => A)(using async: Async): Fiber[A] =
    async.sf.fork("")(block)

  def run[A](block: Async ?=> A): A = {
    val loomScope = new ShutdownOnFailure()

    // given effect: Effect[StructuredScope] = Effect(JvmStructuredScope(loomScope))

    try {
      val mainTask = loomScope.fork(() => {
        block(using
          Effect(
            JvmStructuredScope(
              scala.collection.mutable.Map(Thread.currentThread().threadId -> loomScope)
            )
          )
        )
      })
      loomScope.join().throwIfFailed(identity)
      mainTask.get()
    } finally {
      loomScope.close()
    }
  }
}
