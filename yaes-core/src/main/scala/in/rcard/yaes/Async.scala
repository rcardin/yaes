package in.rcard.yaes

import java.util.concurrent.StructuredTaskScope
import java.util.concurrent.StructuredTaskScope.{ShutdownOnFailure, Subtask}
import scala.concurrent.duration.Duration
import java.util.concurrent.ExecutionException
import scala.concurrent.Promise
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.immutable.Stream.Cons
import java.util.function.Consumer

trait StructuredScope {
  def delay(duration: Duration): Unit
  def fork[A](name: String)(block: => A): Fiber[A]
  def structured[A](program: Async ?=> A): A
}

trait Fiber[A] {
  def value(using async: Async): Raise[Cancelled] ?=> A
  def join()(using async: Async): Unit
  def cancel()(using async: Async): Unit
  def onComplete(result: A => Unit)(using async: Async): Unit

  private[yaes] def unsafeValue(using async: Async): A
}

object Cancelled
type Cancelled = Cancelled.type

class JvmFiber[A](
    private val promise: CompletableFuture[A],
    private val forkedThread: Future[Thread]
) extends Fiber[A] {

  override def unsafeValue(using async: Async): A = promise.get()

  override def onComplete(fn: A => Unit)(using async: Async): Unit = {
    promise.thenAccept(result => fn(result))
  }

  override def value(using async: Async): Raise[Cancelled] ?=> A = try {
    promise.get()
  } catch {
    case cancellationEx: CancellationException => Raise.raise(Cancelled)
  }

  override def join()(using async: Async): Unit =
    try {
      promise.get()
    } catch {
      case cancellationEx: CancellationException => ()
    }

  override def cancel()(using async: Async): Unit = {
    // We'll wait until the thread is forked
    forkedThread.get().interrupt()
  }
}

class JvmStructuredScope(
    private val scopes: scala.collection.mutable.Map[Long, StructuredTaskScope[Any]]
) extends StructuredScope {

  override def structured[A](program: Async ?=> A): A = {
    val loomScope = new ShutdownOnFailure()
    try {
      val mainTask = loomScope.fork(() => {
        scopes.addOne(Thread.currentThread().threadId -> loomScope)
        program(using Effect(this))
      })
      loomScope.join().throwIfFailed(identity)
      mainTask.get()
    } finally {
      loomScope.close()
    }
  }

  override def delay(duration: Duration): Unit = {
    Thread.sleep(duration.toMillis)
  }

  override def fork[A](name: String)(block: => A): Fiber[A] = {
    val promise      = CompletableFuture[A]()
    val forkedThread = CompletableFuture[Thread]()
    scopes(Thread.currentThread().threadId).fork(() => {
      val innerScope = new ShutdownOnFailure()
      try {
        val innerTask: StructuredTaskScope.Subtask[A] = innerScope.fork(() => {
          val currentThread = Thread.currentThread()
          scopes.addOne(currentThread.threadId -> innerScope)
          forkedThread.complete(currentThread)
          block
        })
        innerScope.join()
        if (innerTask.state() != Subtask.State.SUCCESS) {
          innerTask.exception() match {
            case ie: InterruptedException =>
              promise.cancel(true)
            case exex: ExecutionException => throw exex.getCause
            case throwable                => throw throwable
          }
        } else {
          promise.complete(innerTask.get())
        }
      } finally {
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

  def fork[A](block: => A)(using async: Async): Fiber[A] =
    async.sf.fork(s"fiberb-${scala.util.Random.nextString(10)}")(block)

  object TimedOut
  type TimedOut = TimedOut.type

  def timeout[A](
      timeout: Duration
  )(block: => A)(using async: Async, raise: Raise[TimedOut]): A = {
    race(
      {
        block
      }, {
        delay(timeout)
        Raise.raise(TimedOut)
      }
    )
  }

  def race[R1, R2](block1: => R1, block2: => R2)(using async: Async): R1 | R2 = {
    racePair(block1, block2) match {
      case Left((result1, fiber2)) =>
        fiber2.cancel()
        result1
      case Right((fiber1, result2)) =>
        fiber1.cancel()
        result2
    }
  }

  def par[R1, R2](block1: => R1, block2: => R2)(using async: Async): (R1, R2) = {
    racePair(block1, block2) match {
      case Left((result1, fiber2)) =>
        fiber2.join()
        (result1, fiber2.unsafeValue)
      case Right((fiber1, result2)) =>
        fiber1.join()
        (fiber1.unsafeValue, result2)
    }
  }

  def racePair[R1, R2](block1: => R1, block2: => R2)(using
      async: Async
  ): Either[(R1, Fiber[R2]), (Fiber[R1], R2)] = {
    val promise = CompletableFuture[Either[(R1, Fiber[R2]), (Fiber[R1], R2)]]
    val fiber1  = fork(block1)
    val fiber2  = fork(block2)

    fiber1.onComplete { result1 =>
      promise.complete(Left((result1, fiber2)))
    }
    fiber2.onComplete { result2 =>
      promise.complete(Right((fiber1, result2)))
    }

    promise.get()
  }

  def run[A](block: Async ?=> A): A = {
    Async.unsafe.sf.structured(block)
  }

  val unsafe = new Effect[StructuredScope](new JvmStructuredScope(scala.collection.mutable.Map()))
}
