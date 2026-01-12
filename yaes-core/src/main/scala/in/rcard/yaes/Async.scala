package in.rcard.yaes

import java.util as ju
import scala.concurrent.duration.Duration

import ju.concurrent.CancellationException
import ju.concurrent.CompletableFuture
import ju.concurrent.ExecutionException
import ju.concurrent.Future
import ju.concurrent.StructuredTaskScope
import ju.concurrent.StructuredTaskScope.ShutdownOnFailure
import ju.concurrent.StructuredTaskScope.Subtask
import ju.concurrent.SynchronousQueue
import ju.concurrent.ThreadFactory
import ju.function.Consumer
import ju.concurrent.ConcurrentHashMap
import ju.concurrent.CountDownLatch
import ju.concurrent.Callable
import ju.concurrent.atomic.AtomicBoolean
import ju.concurrent.locks.ReentrantLock

type Async = Yaes[Async.Unsafe]

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
  def value(using async: Async): Raise[Async.Cancelled] ?=> A

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

  override def value(using async: Async): Raise[Async.Cancelled] ?=> A = try {
    unsafeValue
  } catch {
    case cancellationEx: CancellationException => Raise.raise(Async.Cancelled)
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
  */
class JvmAsync extends Async.Unsafe {

  override def delay(duration: Duration): Unit = {
    Thread.sleep(duration.toMillis)
  }

  override def fork[A](name: String)(block: => A): Fiber[A] = {
    val promise      = CompletableFuture[A]()
    val forkedThread = CompletableFuture[Thread]()
    JvmAsync.scope
      .get()
      .fork(() => {
        val innerScope = new ShutdownOnFailure(name, JvmAsync.namedThreadFactory(name))
        forkedThread.complete(Thread.currentThread())
        try {
          val innerTask: StructuredTaskScope.Subtask[A] = innerScope.fork(() => {
            JvmAsync.scope.set(innerScope)
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
        } catch {
          case ie: InterruptedException =>
            promise.cancel(true)
        } finally {
          JvmAsync.scope.remove()
          innerScope.close()
        }
      })
    new JvmFiber[A](promise, forkedThread)
  }
}
object JvmAsync {

  private[yaes] val scope: ThreadLocal[StructuredTaskScope[Any]] = new ThreadLocal()

  private[yaes] def namedThreadFactory(name: String): ThreadFactory = {
    Thread.ofVirtual().name(name).factory()
  }
}

/** A StructuredTaskScope that supports graceful shutdown with timeout enforcement.
  *
  * This scope extends the base `StructuredTaskScope` to provide coordination between external
  * shutdown signals (from the [[Shutdown]] effect) and internal timeout enforcement.
  *
  * **Key Features:**
  *   - Tracks the main task separately from other forked fibers
  *   - Uses `CountDownLatch` for signaling between external thread (Shutdown hook) and internal
  *     timeout enforcer fiber
  *   - `initiateGracefulShutdown()` can be called from external threads safely
  *   - When main task completes after shutdown is initiated, the scope shuts down immediately
  *   - If the timeout expires before main task completion, remaining fibers are cancelled
  *   - Fail-fast behavior: any fiber exception triggers immediate scope shutdown
  *
  * **Thread Safety:**
  *   - `initiateGracefulShutdown()` is thread-safe and idempotent
  *   - Exception handling uses `ReentrantLock` to ensure only the first exception is captured
  *   - Only fibers inside the scope call `shutdown()` - respects structured concurrency
  *
  * @param name
  *   the name for threads created by this scope
  * @param factory
  *   the thread factory for creating virtual threads
  * @param inFlightTasksCompletionTimeout
  *   the maximum time to wait for the main task to complete after shutdown is initiated
  */
private class GracefulShutdownScope(
    name: String,
    factory: ThreadFactory,
    private val inFlightTasksCompletionTimeout: Duration
) extends StructuredTaskScope[Any](name, factory) {

  private val shutdownInitiated = new AtomicBoolean(false)
  private val timeoutExpired    = new AtomicBoolean(false)
  private val shutdownLatch     = new CountDownLatch(1)

  private val exceptionLock             = new ReentrantLock()
  private var firstException: Throwable = null

  @volatile private var mainTask: Subtask[?] = null

  // Fork the fiber that enforces the timeout after shutdown is initiated
  private val timeoutEnforcer = super.fork(() => {
    shutdownLatch.await()
    Thread.sleep(
      inFlightTasksCompletionTimeout.toMillis
    )
    timeoutExpired.set(true)
  })

  /** Signals that graceful shutdown should begin.
    *
    * Called from the Shutdown effect hook when `Shutdown.initiateShutdown()` is invoked. This
    * method is thread-safe and idempotent. It sets `shutdownInitiated` to true and counts down the
    * latch to wake up the timeout enforcer.
    */
  def initiateGracefulShutdown(): Unit = {
    if (shutdownInitiated.compareAndSet(false, true)) {
      shutdownLatch.countDown()
    }
  }

  /** Forks the main task and stores reference for completion tracking.
    *
    * @param task
    *   the task to fork
    * @return
    *   the subtask representing the forked computation
    */
  def forkMainTask[U](task: Callable[? <: U]): Subtask[U] = {
    val subtask = super.fork(task)
    mainTask = subtask
    subtask
  }

  /** Handles task completion with graceful shutdown support.
    *
    * Called by the StructuredTaskScope when a subtask finishes.
    */
  override protected def handleComplete(subtask: Subtask[?]): Unit = {
    exceptionLock.lock()
    try {
      if (subtask.state() == Subtask.State.FAILED && firstException == null) {
        firstException = subtask.exception()
        this.shutdown()
      } else if (timeoutExpired.get()) {
        this.shutdown()
      } else if ((subtask eq mainTask) && shutdownInitiated.get()) {
        // Main task completed after shutdown initiated - all work is done
        this.shutdown()
      } else {
        super.handleComplete(subtask)
      }
    } finally {
      exceptionLock.unlock()
    }
  }

  def throwIfFailed[X <: Throwable](esf: Throwable => X): Unit = {
    ensureOwnerAndJoined()
    val exception: Throwable = firstException;
    if (exception != null) {
      val ex: X = esf(exception);
      throw ex;
    }
  }

  override def join(): GracefulShutdownScope = {
    super.join()
    this
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

  extension [A](flow: Flow[A]) {

    /** Launches the collection of this flow in the current Async context. Returns a Fiber that
      * represents the launched coroutine and can be used to join or cancel collection of the flow.
      *
      * This is a terminal operator on the flow. The flow collection is launched when this function
      * is called and is performed asynchronously. This operator is usually used with extension
      * functions like `onEach`, `onCompletion`, and other operators to process all emitted values
      * and handle exceptions within the flow.
      *
      * Example:
      * {{{
      * val flow = Flow(1, 2, 3)
      *
      * // Launch the flow in the current Async context
      * val fiber = flow
      *   .onEach { value => println(s"Processed value: $value") }
      *   .forkOn()
      *
      * // Do some other work
      *
      * // Wait for the flow collection to complete
      * fiber.join()
      * }}}
      *
      * @param async
      *   the async context to launch the flow in
      * @return
      *   a Fiber that represents the launched computation and can be used for joining or
      *   cancellation
      */
    def forkOn()(using async: Async): Fiber[Unit] = Async.fork {
      flow.collect { _ => () }
    }

    /** Combines the elements of this flow with another flow using the provided function.
      *
      * The method emits elements by applying the provided function `f` to pairs of elements from
      * the current flow and the `other` flow. It only emits elements when both flows provide
      * values.
      *
      * Example:
      * {{{
      * val flow1 = Flow("a", "b", "c", "d")
      * val flow2 = Flow(1, 2, 3)
      * val combined = flow1.zipWith(flow2)((_, _))
      *
      * val result = scala.collection.mutable.ArrayBuffer[(String, Int)]()
      *
      * combined.collect { result += _ }
      *
      * // Result contains the elements ("a", 1), ("b", 2), ("c", 3)
      * }}}
      *
      * @param other
      *   The other flow to combine with this flow.
      * @param f
      *   A function that takes a pair of elements from both flows and produces a new element.
      * @param async
      *   The async context
      * @return
      *   A new flow emitting elements resulting from applying the function `f` to pairs of elements
      *   from both flows.
      */
    def zipWith[B, C](other: Flow[B])(f: (A, B) => C)(using async: Async): Flow[C] = Flow.flow {
      val second: SynchronousQueue[Option[B]] = new SynchronousQueue()
      Async.race(
        {
          other.collect { b =>
            second.put(Some(b))
          }
          second.put(None)
        }, {
          flow.collect { a =>
            second.take() match {
              case Some(b) => Flow.emit(f(a, b))
              case None    => ()
            }
          }
        }
      )
    }
  }

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
    val raceResult: Either[TimedOut, A] = race(
      {
        Right(block)
      }, {
        delay(timeout)
        Left(TimedOut)
      }
    )
    raceResult match {
      case Right(result) => result
      case Left(timeout) => Raise.raise(timeout)
    }
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

  def handler[A]: Yaes.Handler[Async.Unsafe, A, A] =
    new Yaes.Handler[Async.Unsafe, A, A] {
      override inline def handle(program: Yaes[Async.Unsafe] ?=> A): A = {
        val async     = new JvmAsync()
        val loomScope = new ShutdownOnFailure(
          "yaes-async-handler",
          JvmAsync.namedThreadFactory("yaes-async-handler")
        )
        try {
          val mainTask = loomScope.fork(() => {
            try {
              JvmAsync.scope.set(loomScope)
              program(using new Yaes(async))
            } finally {
              JvmAsync.scope.remove()
            }
          })
          loomScope.join().throwIfFailed(identity)
          mainTask.get()
        } finally {
          loomScope.close()
        }
      }
    }

  opaque type Deadline = Duration
  object Deadline {
    def after(duration: Duration): Deadline = duration
  }

  /** Runs an async computation with graceful shutdown support and timeout enforcement.
    *
    * This method wraps an async computation in a [[GracefulShutdownScope]] that coordinates with
    * the [[Shutdown]] effect. When shutdown is initiated, the scope allows in-flight work to
    * complete gracefully within the specified deadline before cancelling remaining fibers.
    *
    * **Behavior:**
    *   - The main task (the `block` parameter) runs normally within the async scope
    *   - When `Shutdown.initiateShutdown()` is called, the scope is notified via the registered
    *     shutdown hook
    *   - After the main task completes (post-shutdown), the scope shuts down immediately
    *   - If the main task doesn't complete within the deadline after shutdown is initiated, the
    *     timeout enforcer triggers and remaining fibers are cancelled via cooperative interruption
    *   - Any forked fibers that fail with an exception cause immediate scope shutdown (fail-fast)
    *
    * **Lifecycle:**
    *   1. Main task and any forked fibers start running
    *   1. Shutdown is initiated (via JVM hook or `Shutdown.initiateShutdown()`)
    *   1. Shutdown hook triggers `scope.initiateGracefulShutdown()`
    *   1. Main task continues running, allowing cleanup code to execute
    *   1. When main task completes, scope shuts down and cancels remaining fibers
    *   1. If deadline expires before main task completes, remaining fibers are cancelled
    *   1. `scope.join()` completes when all fibers finish (or are cancelled)
    *
    * **Integration with Shutdown Effect:** This method returns `Shutdown ?=> A`, meaning it
    * requires a Shutdown context. It automatically registers a hook with `Shutdown.onShutdown` to
    * trigger graceful shutdown when the Shutdown effect transitions to shutting down state.
    *
    * Example:
    * {{{
    * Shutdown.run {
    *   Async.withGracefulShutdown(Deadline.after(30.seconds)) {
    *     val serverFiber = Async.fork("server") {
    *       while (!Shutdown.isShuttingDown()) {
    *         handleRequest()
    *       }
    *       // Graceful cleanup after shutdown initiated
    *     }
    *     serverFiber.join()
    *   }
    * }
    * }}}
    *
    * @param deadline
    *   Maximum time to wait for the main task to complete after shutdown is initiated before
    *   cancelling remaining fibers
    * @param block
    *   The async computation to run
    * @tparam A
    *   The result type of the computation
    * @return
    *   A program requiring Shutdown context that blocks until the computation completes
    */
  def withGracefulShutdown[A](deadline: Deadline)(block: Async ?=> A): Shutdown ?=> A = {

    def handler[A]: Yaes.Handler[Async.Unsafe, A, A] =
      new Yaes.Handler[Async.Unsafe, A, A] {
        override inline def handle(program: Yaes[Async.Unsafe] ?=> A): A = {
          val async     = new JvmAsync()
          val loomScope = new GracefulShutdownScope(
            "yaes-async-with-graceful-shutdown",
            JvmAsync.namedThreadFactory("yaes-async-with-graceful-shutdown"),
            inFlightTasksCompletionTimeout = deadline
          )
          try {

            Shutdown.onShutdown {
              loomScope.initiateGracefulShutdown()
            }

            val mainTask = loomScope.forkMainTask(() => {
              try {
                JvmAsync.scope.set(loomScope)
                program(using new Yaes(async))
              } finally {
                JvmAsync.scope.remove()
              }
            })
            loomScope.join().throwIfFailed(identity)
            mainTask.get()
          } finally {
            loomScope.close()
          }
        }
      }

    Yaes.handle(block)(using handler)
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
  trait Unsafe {

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
