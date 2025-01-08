package in.rcard.yaes

import java.util.concurrent.StructuredTaskScope
import java.util.concurrent.StructuredTaskScope.{ShutdownOnFailure, Subtask}
import scala.concurrent.duration.Duration

trait StructuredScope {
  def delay(duration: Duration): Unit
  def fork[A](block: => A): Fiber[A]
}

// FIXME Should we return an Async ?=> A instead of an A?
trait Fiber[A] {
  def value: A
  def join(): Unit
}

class JvmFiber[A](private val subtask: Subtask[A]) extends Fiber[A] {
  override def value: A = subtask.get()

  override def join(): Unit = subtask.get()
}

class JvmStructuredScope(private val scope: StructuredTaskScope[Any]) extends StructuredScope {
  override def delay(duration: Duration): Unit = {
    Thread.sleep(duration.toMillis)
  }

  override def fork[A](block: => A): Fiber[A] = {
    val task = scope.fork(() => {
      val innerScope = new ShutdownOnFailure()
      try {
        val innerTask: StructuredTaskScope.Subtask[A] = innerScope.fork(() => block)
        innerScope.join().throwIfFailed()
        innerTask.get()
      } finally {
        innerScope.close()
      }
    })
    new JvmFiber[A](task)
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
