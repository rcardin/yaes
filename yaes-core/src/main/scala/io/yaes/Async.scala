package io.yaes

import java.util as ju
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

import ju.concurrent.CancellationException
import ju.concurrent.CompletableFuture
import ju.concurrent.ExecutionException
import ju.concurrent.Future
import ju.concurrent.StructuredTaskScope
import ju.concurrent.CountDownLatch
import ju.concurrent.StructuredTaskScope.Joiner
import ju.concurrent.StructuredTaskScope.FailedException

type Async = Async.Unsafe

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

  /** Registers a callback to be executed when the computation completes successfully.
    *
    * @param result
    *   the callback function
    * @param async
    *   the async context
    */
  def onComplete(result: A => Unit)(using async: Async): Unit

  /** Registers a callback to be executed when the computation fails with an exception.
    *
    * @param handler
    *   the callback function receiving the exception
    * @param async
    *   the async context
    */
  def onFailure(handler: Throwable => Unit)(using async: Async): Unit

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

  override def onFailure(handler: Throwable => Unit)(using async: Async): Unit = {
    promise.whenComplete { (_, ex) =>
      if (ex != null) {
        handler(ex)
      }
    }
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
      case ee: ExecutionException                => throw ee.getCause
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
        Thread.currentThread().setName(name)
        val innerScope = StructuredTaskScope
          .open[A, Void](Joiner.awaitAllSuccessfulOrThrow())
        forkedThread.complete(Thread.currentThread())
        JvmAsync.scope.set(innerScope.asInstanceOf[StructuredTaskScope[Any, Any]])
        try {
          val result = block
          innerScope.join()
          promise.complete(result)
        } catch {
          case _: InterruptedException =>
            promise.cancel(true)
            JvmAsync.ensureJoined(innerScope)
          case fe: FailedException =>
            promise.completeExceptionally(fe.getCause)
            throw fe.getCause
          case t: Throwable =>
            val wasCancelled = Thread.currentThread().isInterrupted()
            JvmAsync.ensureJoined(innerScope)
            promise.completeExceptionally(t)
            if (!wasCancelled) throw t
        } finally {
          JvmAsync.scope.remove()
          innerScope.close()
        }
      })
    new JvmFiber[A](promise, forkedThread)
  }
}
object JvmAsync {

  private[yaes] val scope: ThreadLocal[StructuredTaskScope[Any, Any]] = new ThreadLocal()

  /** Ensures that `join()` has been called on the given scope before `close()`.
    *
    * In JDK 25, `close()` requires `join()` to have been called first. When the block throws before
    * reaching `join()`, we must still call it. Setting the interrupt flag ensures `join()` returns
    * immediately, and `close()` will then cancel all remaining fibers.
    */
  private[yaes] def ensureJoined(scope: StructuredTaskScope[?, ?]): Unit = {
    Thread.currentThread().interrupt()
    try { scope.join() }
    catch { case _: Throwable => () }
  }

  /** Like [[JvmAsync.fork]], but a `NonFatal` failure of `block` — whether thrown directly or
    * surfaced by joining a nested scope that a fiber forked inside `block` opened (e.g. via a
    * further `Async.fork`/`Async.par` call) — is captured on the returned fiber's promise and never
    * rethrown into the parent scope.
    *
    * `Async.fork` always rethrows on failure, which is the right default: a failing fiber should
    * fail its parent scope. [[Async.raceSuccess]] needs the opposite: a losing branch's failure
    * must never poison the scope that `raceSuccess` itself runs in, because `raceSuccess` only
    * fails when *both* branches fail — a single branch failing is an expected, recoverable outcome,
    * not a supervision event.
    *
    * Cancellation (`InterruptedException`) and fatal errors (`VirtualMachineError`, `LinkageError`,
    * ...) are handled exactly like [[JvmAsync.fork]]: cancellation never rethrows (it already
    * didn't) and fatal errors always do, so they still propagate and poison the parent scope as
    * usual.
    *
    * @param name
    *   the name of the fiber
    * @param block
    *   the code to execute asynchronously
    * @tparam A
    *   the type of value produced by the block
    * @return
    *   a [[Fiber]] whose promise carries the outcome (success, failure, or cancellation) without
    *   ever poisoning the parent scope on a `NonFatal` failure
    */
  private[yaes] def attemptFork[A](name: String)(block: => A): Fiber[A] = {
    val promise      = CompletableFuture[A]()
    val forkedThread = CompletableFuture[Thread]()
    JvmAsync.scope
      .get()
      .fork(() => {
        Thread.currentThread().setName(name)
        val innerScope = StructuredTaskScope
          .open[A, Void](Joiner.awaitAllSuccessfulOrThrow())
        forkedThread.complete(Thread.currentThread())
        JvmAsync.scope.set(innerScope.asInstanceOf[StructuredTaskScope[Any, Any]])
        try {
          val result = block
          innerScope.join()
          promise.complete(result)
        } catch {
          case _: InterruptedException =>
            promise.cancel(true)
            JvmAsync.ensureJoined(innerScope)
          case fe: FailedException =>
            // innerScope.join() already ran (that is how we got here), so there is nothing
            // left to join before close() — unlike the other branches below.
            val cause = fe.getCause
            promise.completeExceptionally(cause)
            if (!NonFatal(cause)) throw cause
          case NonFatal(t) =>
            JvmAsync.ensureJoined(innerScope)
            promise.completeExceptionally(t)
          case t: Throwable =>
            val wasCancelled = Thread.currentThread().isInterrupted()
            JvmAsync.ensureJoined(innerScope)
            promise.completeExceptionally(t)
            if (!wasCancelled) throw t
        } finally {
          JvmAsync.scope.remove()
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

  /** A type representing a shutdown timeout.
    *
    * This type is used to signal that a shutdown operation timed out.
    */
  object ShutdownTimedOut
  type ShutdownTimedOut = ShutdownTimedOut.type

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
    async.delay(duration)
  }

  /** Creates a new fiber with a specified name.
    *
    * This method is deliberately not an overload of [[fork]]: a block whose type conforms to
    * `String` (including `Nothing`, e.g. a block ending in `throw` or `Raise.raise`) would
    * otherwise bind to the `name` parameter and be evaluated eagerly on the caller thread, with the
    * remaining parameter list silently eta-expanded to a discarded function value.
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
  def forkNamed[A](name: String)(block: => A)(using async: Async): Fiber[A] =
    async.fork(name)(block)

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
    async.fork(s"fiber-${scala.util.Random.nextString(10)}")(block)

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

  /** Races two computations against each other, returning the result of the first one to *succeed*,
    * ignoring failures unless both branches fail.
    *
    * Unlike [[race]] — which returns whichever branch completes first, success or failure, so a
    * fast failure beats a slow success — `raceSuccess` keeps waiting on the surviving branch when
    * one of them fails. Only if *both* branches fail does `raceSuccess` fail, surfacing the LAST
    * failure observed (i.e. the failure of whichever branch finished second).
    *
    * As soon as one branch succeeds, the other one is cancelled, exactly like [[race]] does. If
    * both branches complete (successfully or not) at effectively the same time, which one is
    * treated as "first" is nondeterministic — including which failure is reported when both fail
    * simultaneously.
    *
    * Example:
    * {{{
    * val result = Async.raceSuccess(
    *   { /* fails fast */ throw new RuntimeException("boom") },
    *   { /* succeeds slowly */ 42 }
    * )
    * // result == 42, the fast failure is ignored
    * }}}
    *
    * @param block1
    *   the first computation
    * @param block2
    *   the second computation
    * @param async
    *   the async context
    * @tparam R1
    *   the result type of the first computation
    * @tparam R2
    *   the result type of the second computation
    * @return
    *   the result of the first computation to succeed
    * @throws Throwable
    *   the last failure observed, if both computations fail
    * @see
    *   [[race]] for a race that returns the first branch to complete, win or lose
    */
  def raceSuccess[R1, R2](block1: => R1, block2: => R2)(using async: Async): R1 | R2 = {
    val fiber1 = JvmAsync.attemptFork[R1]("fiber1")(block1)
    val fiber2 = JvmAsync.attemptFork[R2]("fiber2")(block2)
    val winner = CompletableFuture[R1 | R2]()

    // Tracks the most recent *genuine* failure (as opposed to a cancellation) seen on either
    // branch, plus how many branches have reached a non-winning terminal state (failed or
    // cancelled). A branch can be cancelled for a reason that has nothing to do with this race —
    // e.g. an unrelated sibling fiber failing and shutting down the enclosing scope — so a
    // cancellation never overwrites a genuine failure already observed on the other branch, and
    // is only ever reported itself when neither branch ever produced one.
    val lock                                  = new Object
    var lastGenuineFailure: Option[Throwable] = None
    var terminatedCount                       = 0

    def onSuccess(value: R1 | R2, loser: Fiber[?]): Unit = {
      loser.cancel()
      winner.complete(value)
    }

    def onFailure(error: Throwable): Unit = {
      val outcome = lock.synchronized {
        error match {
          case _: CancellationException => ()
          case genuine                  => lastGenuineFailure = Some(genuine)
        }
        terminatedCount += 1
        if (terminatedCount < 2) None else Some(lastGenuineFailure.getOrElse(error))
      }
      outcome.foreach(winner.completeExceptionally)
    }

    fiber1.onComplete(value1 => onSuccess(value1, fiber2))
    fiber2.onComplete(value2 => onSuccess(value2, fiber1))
    fiber1.onFailure(onFailure)
    fiber2.onFailure(onFailure)

    try winner.get()
    catch { case ee: ExecutionException => throw ee.getCause }
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

  /** Executes a function over all elements of a collection in parallel, returning results in order.
    *
    * Each element is processed concurrently using a forked fiber. Results are collected preserving
    * the input order. If any computation fails, all remaining fibers are automatically cancelled.
    *
    * Example:
    * {{{
    * val profiles: Seq[UserProfile] = Async.run {
    *   Async.parTraverse(List(1, 2, 3, 4, 5))(fetchUserProfile)
    * }
    * }}}
    *
    * @param items
    *   the collection of elements to process
    * @param f
    *   the function to apply to each element
    * @param async
    *   the async context
    * @tparam A
    *   the type of input elements
    * @tparam B
    *   the type of output elements
    * @return
    *   a sequence of results in the same order as the input
    */
  def parTraverse[A, B](items: Seq[A])(f: A => B)(using async: Async): Seq[B] = {
    val fibers = items.zipWithIndex.map { case (a, idx) =>
      forkNamed(s"parTraverse-$idx")(f(a))
    }
    try {
      fibers.foreach(_.join())
    } catch {
      case t: Throwable =>
        fibers.foreach(_.cancel())
        throw t
    }
    fibers.map(_.unsafeValue)
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
    val fiber1  = forkNamed("fiber1")(block1)
    val fiber2  = forkNamed("fiber2")(block2)

    fiber1.onComplete { result1 =>
      promise.complete(Left((result1, fiber2)))
    }
    fiber1.onFailure { ex =>
      promise.completeExceptionally(ex)
    }
    fiber2.onComplete { result2 =>
      promise.complete(Right((fiber1, result2)))
    }
    fiber2.onFailure { ex =>
      promise.completeExceptionally(ex)
    }

    try {
      promise.get()
    } catch {
      case ee: ExecutionException => throw ee.getCause
    }
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
    * It can also be nested inside an existing scope (e.g. an [[unsupervised]] block); the enclosing
    * scope is saved and restored so it is left untouched.
    *
    * @param block
    *   the async computation to run
    * @return
    *   the result of the computation
    */
  inline def run[A](block: Async ?=> A): A = {
    val async     = new JvmAsync()
    val loomScope = StructuredTaskScope.open[A, Void](
      Joiner.awaitAllSuccessfulOrThrow(),
      configure => configure.withName("yaes-async-handler")
    )
    // In JDK 25, fork() can only be called by the scope owner. Run program directly on
    // the calling thread so it is the owner and can fork child fibers on loomScope.
    val prev = JvmAsync.scope.get()
    JvmAsync.scope.set(loomScope.asInstanceOf[StructuredTaskScope[Any, Any]])
    try {
      val result = block(using async)
      loomScope.join()
      result
    } catch {
      case fe: FailedException =>
        throw fe.getCause
      case t: Throwable =>
        JvmAsync.ensureJoined(loomScope)
        Thread.interrupted()
        throw t
    } finally {
      // Restore the previous scope (if any), so that a nested run leaves an enclosing scope intact.
      if (prev != null) JvmAsync.scope.set(prev)
      else JvmAsync.scope.remove()
      loomScope.close()
    }
  }

  /** Runs an asynchronous computation in an unsupervised scope.
    *
    * Unlike [[run]], an unsupervised scope does not wait for the fibers forked inside it to
    * complete naturally, and it does not fail fast when one of those fibers throws. The block runs
    * to completion; as soon as it returns (or throws), any fiber still running is cancelled via
    * cooperative interruption, and the method returns only after cancellation has propagated.
    *
    * This mirrors an Ox-style unsupervised scope: the supervision model is a property of the active
    * scope, not of the [[fork]] call, so `Async.fork` is reused unchanged. A fiber that fails and
    * is never joined does not propagate its exception to the enclosing scope, and sibling fibers
    * are not cancelled when one of them fails. To observe a fiber's failure, join it explicitly
    * with [[Fiber.join]] or [[Fiber.value]].
    *
    * An exception thrown from the main body of the block still propagates to the caller.
    *
    * Like [[run]], `Async.unsupervised` is a standalone entry point: it provides its own [[Async]]
    * capability to the block. It can also be nested inside an existing scope (e.g. an [[run]]
    * block); the enclosing scope is saved and restored so it is left untouched.
    *
    * Example:
    * {{{
    * Async.run {
    *   Async.unsupervised {
    *     // This fiber is never joined; when the block returns it is cancelled
    *     Async.fork {
    *       Async.delay(10.seconds)
    *       neverReached()
    *     }
    *     42
    *   } // returns 42 promptly, then cancels the forked fiber
    * }
    * }}}
    *
    * @param block
    *   the async computation to run in the unsupervised scope
    * @tparam A
    *   the result type of the computation
    * @return
    *   the result of the block; still-running fibers are cancelled once it completes
    */
  def unsupervised[A](block: Async ?=> A): A = {
    val async = new JvmAsync()
    val scope = StructuredTaskScope.open[Any, Void](
      Joiner.awaitAll[Any](),
      configure => configure.withName("yaes-async-unsupervised")
    )
    val prev = JvmAsync.scope.get()
    JvmAsync.scope.set(scope.asInstanceOf[StructuredTaskScope[Any, Any]])
    try {
      block(using async)
    } finally {
      // Runs on both the normal and exceptional paths. Interrupt trick: ensureJoined sets
      // the interrupt flag so join() returns immediately, making close() cancel remaining
      // fibers instead of waiting for them to finish naturally.
      JvmAsync.ensureJoined(scope)
      Thread.interrupted() // clear the interrupt flag before returning or rethrowing
      // Restore the previous scope (if any).
      if (prev != null) JvmAsync.scope.set(prev)
      else JvmAsync.scope.remove()
      scope.close()
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
    *   - When the main task completes, the scope shuts down immediately and cancels remaining
    *     fibers
    *   - If the main task doesn't complete within the deadline after shutdown is initiated, the
    *     timeout enforcer triggers, remaining fibers are cancelled via cooperative interruption,
    *     and [[ShutdownTimedOut]] is raised
    *   - Any forked fibers that fail with an exception cause immediate scope shutdown (fail-fast)
    *
    * **Lifecycle:**
    *   1. Main task and any forked fibers start running
    *   1. Shutdown is initiated (via JVM hook or `Shutdown.initiateShutdown()`)
    *   1. Shutdown hook triggers `scope.initiateGracefulShutdown()`
    *   1. Main task continues running, allowing cleanup code to execute
    *   1. When main task completes, scope shuts down and cancels remaining fibers
    *   1. If deadline expires before main task completes, remaining fibers are cancelled and
    *      [[ShutdownTimedOut]] is raised
    *   1. `scope.join()` completes when all fibers finish (or are cancelled)
    *
    * **Integration with Shutdown Effect:** This method returns
    * `(Shutdown, Raise[ShutdownTimedOut]) ?=> A`, meaning it requires both a Shutdown context and a
    * Raise[ShutdownTimedOut] context. It automatically registers a hook with `Shutdown.onShutdown`
    * to trigger graceful shutdown when the Shutdown effect transitions to shutting down state.
    *
    * **Error Handling:** When the deadline expires before the main task completes, the method
    * raises [[ShutdownTimedOut]]. Handle this error using `Raise.either`, `Raise.run`, or other
    * Raise handlers.
    *
    * Example:
    * {{{
    * Shutdown.run {
    *   Raise.either {
    *     Async.withGracefulShutdown(Deadline.after(30.seconds)) {
    *       val serverFiber = Async.forkNamed("server") {
    *         while (!Shutdown.isShuttingDown()) {
    *           handleRequest()
    *         }
    *         // Graceful cleanup after shutdown initiated
    *       }
    *       serverFiber.join()
    *     }
    *   }
    * } // Returns Either[ShutdownTimedOut, Unit]
    * }}}
    *
    * @param deadline
    *   Maximum time to wait for the main task to complete after shutdown is initiated before
    *   cancelling remaining fibers and raising [[ShutdownTimedOut]]
    * @param block
    *   The async computation to run
    * @tparam A
    *   The result type of the computation
    * @return
    *   A program requiring Shutdown and Raise[ShutdownTimedOut] contexts that blocks until the
    *   computation completes or raises [[ShutdownTimedOut]] if the deadline expires
    */
  def withGracefulShutdown[A](
      deadline: Deadline
  )(block: Async ?=> A): (Shutdown, Raise[ShutdownTimedOut]) ?=> A = {

    val shutdownLatch = new CountDownLatch(1)

    Shutdown.onShutdown {
      shutdownLatch.countDown()
    }

    // If shutdown was already in progress before we registered the hook,
    // the hook will have been silently ignored. Count down immediately
    // so the deadline is still enforced.
    if (Shutdown.isShuttingDown()) {
      shutdownLatch.countDown()
    }

    val raceResult: Either[ShutdownTimedOut, A] = Async.run {
      Async.race(
        {
          shutdownLatch.await()
          Async.delay(deadline)
          Left(ShutdownTimedOut)
        }, {
          Right(block)
        }
      )
    }

    raceResult match {
      case Right(result) => result
      case Left(timeout) => Raise.raise(timeout)
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
