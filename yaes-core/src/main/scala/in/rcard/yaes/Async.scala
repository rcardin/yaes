package in.rcard.yaes

import in.rcard.yaes.Async.Async
import in.rcard.yaes.Raise.Raise
import in.rcard.yaes.Yaes.Effect

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.StructuredTaskScope
import java.util.concurrent.StructuredTaskScope.ShutdownOnFailure
import java.util.concurrent.StructuredTaskScope.Subtask
import java.util.function.Consumer
import scala.collection.immutable.Stream.Cons
import scala.concurrent.Promise
import scala.concurrent.duration.Duration

import Async.Cancelled

/** Represents an asynchronous computation that can be controlled.
  *
  * A `Fiber` is a lightweight thread of execution that can be joined, cancelled, and monitored for
  * completion.
  *
  * Example:
  * {{{
  * def example(using async: Async) = {
  *   val fiber = Async.fork {
  *     // Some computation
  *     println("Computing...")
  *     42
  *   }
  *
  *   // Wait for the result
  *   fiber.join()
  *
  *   // Get the value (may throw if cancelled)
  *   val result = fiber.value
  *
  *   // Set up completion callback
  *   fiber.onComplete { value =>
  *     println(s"Completed with: $value")
  *   }
  *
  *   // Cancel the computation
  *   fiber.cancel()
  * }
  * }}}
  *
  * @tparam A
  *   the type of value produced by this fiber
  */
trait Fiber[A] {

  /** Retrieves the value of the computation. It raises a [[Cancelled]] error if the fiber was
    * cancelled.
    *
    * @param async
    *   the async context
    * @return
    *   the computed value
    */
  def value(using async: Async): Raise[Cancelled] ?=> A

  /** Waits for the computation to complete. It does not raise any errors if the fiber was
    * cancelled.
    *
    * @param async
    *   the async context
    */
  def join()(using async: Async): Unit

  /** Cancels the computation. the job is not immediately canceled. The job is canceled when it
    * reaches the first point operation that can be interrupted. Cancellation is cooperative.
    * Cancelling a job follows the relationship between parent and child jobs. If a parent's job is
    * canceled, all the children's jobs are canceled as well.
    *
    * @param async
    *   the async context
    */
  def cancel()(using async: Async): Unit

  /** Registers a callback to be executed when the computation completes.
    *
    * @param result
    *   the callback function
    * @param async
    *   the async context
    */
  def onComplete(result: A => Unit)(using async: Async): Unit

  private[yaes] def unsafeValue(using async: Async): A
}

/** JVM implementation of [[Fiber]] using Java's structured concurrency.
  *
  * This implementation provides fiber functionality using Java's structured concurrency. It manages
  * the lifecycle of an asynchronous computation, including completion, cancellation, and value
  * retrieval.
  *
  * @param promise
  *   the CompletableFuture holding the computation's result
  * @param forkedThread
  *   the Future holding the thread running the computation
  * @tparam A
  *   the type of value produced by this fiber
  */
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

/** JVM implementation of [[Async]] using Java's [[StructuredTaskScope]].
  *
  * This implementation provides structured concurrency support using Java's StructuredTaskScope
  * API. It manages hierarchical relationships between concurrent tasks and ensures proper cleanup.
  *
  * @param scopes
  *   mutable map tracking task scopes by thread ID
  */
class JvmStructuredScope(
    val scopes: scala.collection.mutable.Map[Long, StructuredTaskScope[Any]]
) extends Async.Unsafe {

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

/** Companion object for [[Async]] providing utility methods and constructors.
  *
  * This object contains methods for working with asynchronous computations, including timing out
  * operations, racing between computations, and running computations in parallel.
  *
  * Example:
  * {{{
  * val result = Async.run {
  *   // Timeout after 1 second
  *   Async.timeout(Duration(1, TimeUnit.SECONDS)) {
  *     // Some computation that might take too long
  *     42
  *   }
  * }
  *
  * // Race between two computations
  * val raceResult = Async.run {
  *   Async.race(
  *     { /* first computation */ 1 },
  *     { /* second computation */ 2 }
  *   )
  * }
  *
  * // Run computations in parallel
  * val (result1, result2) = Async.run {
  *   Async.par(
  *     { /* first computation */ 1 },
  *     { /* second computation */ 2 }
  *   )
  * }
  * }}}
  */
object Async {

  type Async = Yaes[Async.Unsafe]

  /** A type representing a cancelled computation.
    *
    * This type is used to signal that a computation was cancelled.
    */
  object Cancelled
  type Cancelled = Cancelled.type

  /** A type representing a timed out computation.
    *
    * This type is used to signal that a computation timed out.
    */
  object TimedOut
  type TimedOut = TimedOut.type

  /** Lifts a computation to the Async context.
    *
    * @param block
    *   the code to execute asynchronously
    * @return
    *   the result of the computation
    */
  def apply[A](block: => A): Async ?=> A = block

  /** Delays the execution for the specified duration.
    *
    * @param duration
    *   the time to delay
    * @param async
    *   the async context
    */
  def delay(duration: Duration)(using async: Async): Unit = {
    async.unsafe.delay(duration)
  }

  /** Creates a new fiber with a specified name.
    *
    * @param name
    *   the name of the fiber
    * @param block
    *   the code to execute asynchronously
    * @param async
    *   the async context
    * @return
    *   a [[Fiber]] representing the forked computation
    */
  def fork[A](name: String)(block: => A)(using async: Async): Fiber[A] =
    async.unsafe.fork(name)(block)

  /** Creates a new fiber with an automatically generated name.
    *
    * @param block
    *   the code to execute asynchronously
    * @param async
    *   the async context
    * @return
    *   a [[Fiber]] representing the forked computation
    */
  def fork[A](block: => A)(using async: Async): Fiber[A] =
    async.unsafe.fork(s"fiber-${scala.util.Random.nextString(10)}")(block)

  /** Executes a block of code with a timeout.
    *
    * If the computation doesn't complete within the specified timeout, it raises a [[TimedOut]]
    * error.
    *
    * Example:
    * {{{
    * val result = Async.timeout(Duration(1, TimeUnit.SECONDS)) {
    *   // Some potentially long computation
    *   42
    * }
    * }}}
    *
    * @param timeout
    *   maximum duration to wait for the computation
    * @param block
    *   the code to execute with timeout
    * @param async
    *   the async context
    * @param raise
    *   the raise context for timeout errors
    * @return
    *   the result of the computation if it completes in time
    * @throws TimedOut
    *   if the computation exceeds the timeout
    */
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

  /** Races two computations against each other, returning the result of the first to complete
    * wether if it was completed successfully or not.
    *
    * The losing computation is automatically cancelled.
    *
    * Example:
    * {{{
    * val result = Async.race(
    *   { /* first computation */ 1 },
    *   { /* second computation */ 2 }
    * )
    * }}}
    *
    * @param block1
    *   the first computation
    * @param block2
    *   the second computation
    * @param async
    *   the async context
    * @return
    *   either the result of block1 or block2, whichever completes first
    */
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

  /** Executes two computations in parallel and returns both results. If one of the computations
    * fails, the other one is cancelled.
    *
    * Unlike [[race]], this waits for both computations to complete.
    *
    * Example:
    * {{{
    * val (result1, result2) = Async.par(
    *   { /* first computation */ 1 },
    *   { /* second computation */ 2 }
    * )
    * }}}
    *
    * @param block1
    *   the first computation
    * @param block2
    *   the second computation
    * @param async
    *   the async context
    * @return
    *   a tuple of both results
    */
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

  /** Races two computations and provides access to both fibers.
    *
    * This is a lower-level version of [[race]] that gives you access to the underlying fibers.
    *
    * @param block1
    *   the first computation
    * @param block2
    *   the second computation
    * @param async
    *   the async context
    * @return
    *   either (result1, fiber2) if block1 wins, or (fiber1, result2) if block2 wins
    */
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

  /** Runs an asynchronous computation.
    *
    * This is the main entry point for executing async computations.
    *
    * Example:
    * {{{
    * val result = Async.run {
    *   // Your async computation here
    *   42
    * }
    * }}}
    *
    * @param block
    *   the async computation to run
    * @return
    *   the result of the computation
    */
  inline def run[A](block: Async ?=> A): A = {
    Yaes.handle(block)(using handler)
  }

  def handler[A]: Yaes.Handler[JvmStructuredScope, A, A] =
    new Yaes.Handler[JvmStructuredScope, A, A] {
      override inline def handle(program: Yaes[JvmStructuredScope] ?=> A): A = {
        val async     = new JvmStructuredScope(scala.collection.mutable.Map())
        val loomScope = new ShutdownOnFailure()
        try {
          val mainTask = loomScope.fork(() => {
            async.scopes.addOne(Thread.currentThread().threadId -> loomScope)
            program(using new Yaes(async))
          })
          loomScope.join().throwIfFailed(identity)
          mainTask.get()
        } finally {
          loomScope.close()
        }
      }
    }

  /** A trait representing asynchronous computations.
    *
    * The `Async` trait provides primitives for working with asynchronous operations, including
    * delaying execution and forking concurrent computations.
    *
    * Example:
    * {{{
    * def asyncOperation(using async: Async): Unit = {
    *   // Delay execution for 1 second
    *   async.delay(Duration(1, TimeUnit.SECONDS))
    *
    *   // Fork a new computation
    *   val fiber = async.fork("computation") {
    *     // Some long-running task
    *     42
    *   }
    *
    *   // Join the fiber to wait for completion and get the result
    *   fiber.value
    * }
    * }}}
    */
  trait Unsafe extends Effect {

    /** Delays the execution for the specified duration.
      *
      * @param duration
      *   the time to delay the execution
      */
    def delay(duration: Duration): Unit

    /** Creates a new fiber executing the given block of code.
      *
      * @param name
      *   the name of the fiber
      * @param block
      *   the code to execute asynchronously
      * @return
      *   a [[Fiber]] representing the forked computation
      */
    def fork[A](name: String)(block: => A): Fiber[A]
  }
}
