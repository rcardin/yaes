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
import ju.concurrent.atomic.AtomicBoolean
import ju.concurrent.atomic.AtomicInteger
import ju.concurrent.atomic.AtomicReferenceArray

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

/** A handle to a detached background computation started by [[Async.detached]].
  *
  * Unlike [[Fiber]], a `DetachedFiber` is not bound to any [[Async]] scope and offers no
  * `join`/`cancel`/`value`: the computation it represents already runs on its own background daemon
  * thread, independent of any [[StructuredTaskScope]], so there is no scope left to join or cancel
  * it from. The only supported operation is attaching an observer that runs once the detached
  * computation completes, successfully or not.
  *
  * Example:
  * {{{
  * val handle: DetachedFiber[Int] = Async.run {
  *   Async.detached {
  *     42
  *   }
  * }
  * handle.onComplete(value => println(s"got $value"))
  * handle.onFailure(err => println(s"failed: ${err.getMessage}"))
  * }}}
  *
  * @tparam A
  *   the type of value produced by the detached computation
  */
trait DetachedFiber[A] {

  /** Registers a callback to run once the detached computation completes successfully.
    *
    * The callback does not require an ambient [[Async]] capability. If the detached computation has
    * not completed yet, the callback runs on the detached background thread itself once it does. If
    * the computation has ALREADY completed successfully by the time this is called, the callback
    * instead runs synchronously and inline, on whatever thread called `onComplete` — so a slow or
    * blocking callback registered late blocks the registering thread until it returns. Either way,
    * the callback never runs on a thread of its own.
    *
    * An observer that throws is not propagated anywhere and does not prevent other observers
    * registered on the same handle from running: the exception is silently discarded. Since
    * observers are the only feedback channel for detached work, a throwing observer's failure is
    * otherwise invisible.
    *
    * @param result
    *   the callback function, receiving the computed value
    */
  def onComplete(result: A => Unit): Unit

  /** Registers a callback to run if the detached computation fails with an exception.
    *
    * As with [[onComplete]], the callback does not require an ambient [[Async]] capability, runs
    * either on the detached background thread or, if the computation has already failed,
    * synchronously and inline on the registering thread, and an observer that throws is silently
    * discarded without affecting other observers. It may also run well after the spawning scope has
    * already exited.
    *
    * @param handler
    *   the callback function, receiving the exception the computation failed with
    */
  def onFailure(handler: Throwable => Unit): Unit
}

/** JVM implementation of [[DetachedFiber]], backed by a plain [[CompletableFuture]].
  *
  * Unlike [[JvmFiber]], there is no `forkedThread` handshake and no cancellation: the underlying
  * daemon thread is not owned by any [[StructuredTaskScope]], so there is nothing structured to
  * cancel it from.
  *
  * @param promise
  *   the CompletableFuture holding the detached computation's result
  * @tparam A
  *   the type of value produced by the detached computation
  */
class JvmDetachedFiber[A](private val promise: CompletableFuture[A]) extends DetachedFiber[A] {

  override def onComplete(fn: A => Unit): Unit =
    promise.thenAccept(result => fn(result))

  override def onFailure(handler: Throwable => Unit): Unit =
    promise.whenComplete { (_, ex) =>
      if (ex != null) handler(ex)
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

  override def fork[A](name: String)(block: => A): Fiber[A] =
    JvmAsync.forkImpl(name, rethrowOnFailure = true)(block)

  override def attemptFork[A](name: String)(block: => A): Fiber[A] =
    JvmAsync.forkImpl(name, rethrowOnFailure = false)(block)

  override def never(): Nothing = {
    new CountDownLatch(1).await()
    // Unreachable: the latch above is never counted down, so `await()` can only return by
    // throwing InterruptedException once this thread is interrupted. Throwing that same
    // exception here (rather than e.g. IllegalStateException) keeps this a call site the
    // JvmAsync.forkImpl's `case _: InterruptedException` arm still recognizes, so even this
    // unreachable path would cancel the fiber cleanly instead of poisoning the enclosing scope
    // with what would otherwise look like a genuine failure.
    throw new InterruptedException()
  }

  override def detached[A](block: Async ?=> A): DetachedFiber[A] = {
    val promise = CompletableFuture[A]()
    Thread
      .ofVirtual()
      .name(s"yaes-detached-${java.util.UUID.randomUUID()}")
      .start(() => {
        try {
          val result = Async.run(block)
          promise.complete(result)
        } catch {
          case ie: InterruptedException =>
            // Treated as an ordinary failure, not a cancellation: this thread belongs to no
            // StructuredTaskScope whose cancellation semantics `promise.cancel(true)` could
            // mirror (that would also destroy the original exception, contradicting onFailure's
            // documented contract of receiving the exception the computation failed with).
            // Deliberately NOT restoring the interrupt status here (unlike standard JVM practice
            // for a thread that keeps running): `CompletableFuture` runs any dependents already
            // registered via `onComplete`/`onFailure` synchronously, on this very thread, as part
            // of `completeExceptionally` below. Setting the interrupt flag first would make an
            // observer's own interruptible blocking calls fail spuriously. This thread is about to
            // terminate right after, so no later code could ever observe the flag anyway -- there
            // is nothing for restoring it to protect.
            promise.completeExceptionally(ie)
          case NonFatal(t) =>
            // Contained, not rethrown: `block`'s failure must never propagate anywhere, since
            // this thread belongs to no structured scope that could observe it.
            promise.completeExceptionally(t)
          case fatal: Throwable =>
            // Everything NonFatal does not match: genuine fatal errors (VirtualMachineError,
            // LinkageError, ...) as well as scala.util.control.ControlThrowable (e.g. Raise's
            // own private AccumulationError, see Raise.scala). Still recorded for observers, but
            // also rethrown so it reaches this thread's default uncaught-exception handler,
            // exactly like an equivalent failure would on any other unmanaged thread. This
            // thread is not part of any StructuredTaskScope, so rethrowing here cannot reach the
            // spawning scope or caller either way. Mirrors JvmAsync.forkImpl's identical
            // treatment of this case.
            promise.completeExceptionally(fatal)
            throw fatal
        }
      })
    new JvmDetachedFiber[A](promise)
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

  /** Shared JDK 25 structured-concurrency teardown behind both [[JvmAsync.fork]] and
    * [[JvmAsync.attemptFork]].
    *
    * The two differ in whether a `NonFatal` failure of `block` rethrows into the parent scope
    * (`fork`) or is captured on the fiber's promise instead (`attemptFork`, used by
    * [[Async.raceSuccess]], see its scaladoc for why), and — only on the `attemptFork` path — in
    * the extra cancellation-detection logic needed to tell a genuine failure apart from an
    * interrupt that `block` disguised as a plain exception (see the `case NonFatal(t)` arm below).
    * The scope open/close handshake, the `forkedThread` handshake, the `ThreadLocal` juggling, and
    * the cancellation/fatal-error arms otherwise stay identical between the two. Keeping two copies
    * of that teardown in sync by hand is a standing hazard, so both `fork` and `attemptFork` now
    * delegate here, selecting their behavior with `rethrowOnFailure`.
    *
    * @param name
    *   the name of the fiber
    * @param rethrowOnFailure
    *   `true` for `fork`'s semantics: a failure of `block` — whether thrown directly or surfaced by
    *   joining the nested scope opened for it — always rethrows into the parent scope. `false` for
    *   `attemptFork`'s semantics: a `NonFatal` failure is captured on the returned fiber's promise
    *   and never rethrown. Cancellation and fatal errors (`VirtualMachineError`, `LinkageError`,
    *   ...) are handled identically either way: cancellation never rethrows and fatal errors always
    *   do.
    * @param block
    *   the code to execute asynchronously
    * @tparam A
    *   the type of value produced by the block
    * @return
    *   a [[Fiber]] representing the forked computation
    */
  private def forkImpl[A](name: String, rethrowOnFailure: Boolean)(block: => A): Fiber[A] = {
    val promise      = CompletableFuture[A]()
    val forkedThread = CompletableFuture[Thread]()
    val outerScope   = JvmAsync.scope.get()
    outerScope
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
            // left to join before close() — unlike the other branches below. `fork` always
            // rethrows the unwrapped cause; `attemptFork` only rethrows it when it is genuinely
            // fatal, capturing it on the promise either way.
            val cause = fe.getCause
            promise.completeExceptionally(cause)
            if (rethrowOnFailure || !NonFatal(cause)) throw cause
          case NonFatal(t) if !rethrowOnFailure =>
            // The thread may have been interrupted by the *outer* scope being cancelled (e.g. an
            // unrelated sibling fiber failing) rather than by `block` itself. If `block` catches
            // that `InterruptedException` and rethrows it wrapped in a plain exception — a common
            // Java interop idiom — the interrupt flag is typically already cleared by the time it
            // does so (interruptible blocking calls clear it before throwing), so `outerScope`'s
            // own cancellation state is the reliable signal here; the thread's own flag is checked
            // too in case it is still set. Either way this must be reported as a cancellation, not
            // a genuine failure, or it would overwrite a real failure observed on a sibling branch
            // (see raceSuccess's `onFailure`).
            //
            // Deliberate trade (confirmed, not a bug): checking `outerScope.isCancelled()` also
            // means that if this branch *survives* an interrupt (clears its own flag, keeps
            // running) and then fails later for a genuinely independent reason, that failure is
            // still misreported as a cancellation for as long as the outer scope remains
            // cancelled. That sacrifices a rarer case (a branch outliving an interrupt and then
            // failing on its own) to protect a far more common one (a branch disguising the
            // interrupt itself as an ordinary exception), and the sacrificed case can only arise
            // while the enclosing scope is already being torn down. Do not "fix" this by dropping
            // the `outerScope.isCancelled()` check.
            //
            // Ordering constraint (load-bearing): `Thread.currentThread().isInterrupted()` is read
            // here *before* `JvmAsync.ensureJoined(innerScope)` runs below. `ensureJoined` itself
            // sets this thread's interrupt flag (see its scaladoc), so reading the flag after
            // calling it would make this check unconditionally true.
            if (outerScope.isCancelled() || Thread.currentThread().isInterrupted()) {
              promise.cancel(true)
            } else {
              promise.completeExceptionally(t)
            }
            JvmAsync.ensureJoined(innerScope)
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

  /** Blocks the calling fiber indefinitely, never returning on its own.
    *
    * `never` parks the fiber efficiently (a blocking wait, not a busy loop) until the fiber is
    * cancelled, either explicitly via [[Fiber.cancel]] or through the same interrupt-based
    * cancellation that reaches any other blocking `Async` operation such as [[delay]], for example
    * when [[unsupervised]] tears down a scope whose block already returned.
    *
    * @note
    *   `never` must be forked (directly with [[fork]]/[[attemptFork]], or indirectly through a
    *   combinator such as [[race]] that forks its branches for you), and that fork must itself be
    *   cancelled, raced away, or wrapped in [[timeout]]. A bare `Async.run { Async.never }`, with no
    *   fork in between, runs on the caller thread with nothing able to cancel it and hangs forever;
    *   `Async.run` cannot help here because it only reaches its own teardown after the block
    *   returns, which a `never` inside that block prevents. The same is true of a forked `never`
    *   that is left un-cancelled and un-raced directly inside [[run]]: `run` waits for every forked
    *   fiber to finish, joined or not, so it hangs just the same. [[unsupervised]] does not have
    *   that failure mode: it cancels any fiber still running as soon as its block returns, so a
    *   `never` forked inside it is torn down correctly with no explicit `cancel()` needed.
    * @note
    *   On the forked path, the interrupt that unparks `never` is handled by [[fork]]'s own
    *   cancellation logic, so it never escapes to user code as an untracked exception. That
    *   guarantee is specific to the forked path; see the note above for the unforked case, where no
    *   such handling exists and the interrupt is left to propagate on its own.
    *
    * This is useful for a computation that must "run until cancelled", or for a branch of [[race]]
    * or [[timeout]] that should never complete on its own. [[raceSuccess]] also works with `never`,
    * but only helps when the other branch can actually succeed. [[par]] and [[parTraverse]], by
    * contrast, wait for *every* branch to finish before returning, so pairing either of them with
    * `never` deadlocks unconditionally, not just in the unlucky case.
    *
    * Example:
    * {{{
    * val result = Async.run {
    *   Async.race(
    *     Async.never, // never completes on its own
    *     {
    *       Async.delay(1.second)
    *       42
    *     }
    *   )
    * }
    * // result == 42; the `never` branch is cancelled once the other one wins
    * }}}
    *
    * @param async
    *   the async context; delegates to [[Async.Unsafe.never]] for the actual parking
    *   implementation, exactly like every other `Async` operation delegates to its backend
    * @tparam A
    *   the type of value this method would produce, if it ever returned one
    * @return
    *   never returns; the calling fiber parks until it is cancelled
    * @see
    *   [[race]], [[timeout]], [[raceSuccess]] for typical use sites of a branch that never
    *   completes on its own
    */
  def never[A](using async: Async): A = async.never()

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
    *   the async context; the [[Async.Unsafe.attemptFork]] operation it provides is what actually
    *   runs each branch, so a custom [[Async]] implementation is honored the same way [[race]]
    *   honors it
    * @tparam R1
    *   the result type of the first computation
    * @tparam R2
    *   the result type of the second computation
    * @return
    *   the result of the first computation to succeed
    * @throws Throwable
    *   the last genuine failure observed, if both computations fail. In the rare case where both
    *   branches are cancelled without either ever producing a genuine failure of their own — e.g.
    *   an unrelated sibling fiber poisoning the enclosing scope while both branches are still
    *   running — a bare `java.util.concurrent.CancellationException` is thrown instead.
    * @see
    *   [[race]] for a race that returns the first branch to complete, win or lose
    */
  def raceSuccess[R1, R2](block1: => R1, block2: => R2)(using async: Async): R1 | R2 = {
    val fiber1 = async.attemptFork[R1]("fiber1")(block1)
    val fiber2 = async.attemptFork[R2]("fiber2")(block2)
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
      // Complete the winner before cancelling the loser: `cancel()` blocks on the loser's
      // `forkedThread` future until its thread has actually been forked, so completing the
      // winner first ensures the caller observes a result even if that wait were ever to stall.
      //
      // Only cancel when `complete` actually won. If both branches succeed at effectively the
      // same time, this callback runs on both of them; for the one that lost the `complete` race
      // the "loser" it holds is in fact the branch that already won, which is still on its own
      // thread finishing its completion and scope teardown. Interrupting it there would cancel
      // the winner mid-cleanup for no benefit — both branches have already produced a value, so
      // there is nothing left to cancel either way.
      if (winner.complete(value)) {
        loser.cancel()
      }
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

  /** Executes a function over all elements of a collection in parallel, like [[parTraverse]], but
    * bounding how many fibers, and therefore how many invocations of `f`, run at the same time.
    *
    * Unlike [[parTraverse]], which forks one fiber per element, this forks at most `concurrency`
    * worker fibers, capped at the number of elements: `math.min(math.max(1, concurrency),
    * items.size)`. Each worker repeatedly claims the next unclaimed element from a shared counter
    * and applies `f` to it, so no more than `concurrency` invocations of `f` ever run at the same
    * time, and the number of fibers and virtual threads created no longer grows with the size of
    * `items`. Results are collected preserving the input order, regardless of completion or claim
    * order.
    *
    * `items` is materialized once, up front, into an indexed sequence before any worker is forked,
    * so a lazy `Seq` (for example a `LazyList`) does not serialize the traversal.
    *
    * If any invocation of `f` fails, every worker is cancelled, exactly as [[parTraverse]] cancels
    * its siblings on failure. The failing worker flags the failure before its exception unwinds, so
    * once that failure has been observed no worker invokes `f` on an element it had not already
    * started; this is checked in addition to, and faster than, the cooperative interrupt used to
    * cancel the other workers. A worker can still take an index from the shared counter after the
    * flag is set, it simply never applies `f` to that element. An element a worker has already
    * started still runs to completion, since cancellation is cooperative, so this cannot guarantee
    * that no additional element ever starts, only that no not yet started one does. If a worker is
    * cancelled after claiming an element but before finishing
    * it, that element is never produced, and the traversal fails with a
    * `java.util.concurrent.CancellationException` instead of silently returning a partial result,
    * mirroring how [[parTraverse]] surfaces cancellation through `Fiber.unsafeValue`.
    *
    * A `concurrency` of `items.size` or greater produces the same result as [[parTraverse]], but it
    * does not guarantee that every element runs on its own fiber: workers still claim indices from
    * a shared counter, so a fast worker can claim and run several elements before a slower sibling
    * claims its first one. Computations that need every element to run at the same time, such as a
    * rendezvous or a barrier shared across all elements, must use [[parTraverse]] instead. A
    * non-positive `concurrency` is clamped to `1`, making the traversal fully sequential, one
    * element at a time, rather than throwing: this follows this project's error handling philosophy
    * of clamping invalid input to a sensible default instead of raising an exception from a public
    * API (see the "Error Handling Philosophy" section of `CLAUDE.md`).
    *
    * Example:
    * {{{
    * val profiles: Seq[UserProfile] = Async.run {
    *   // At most 3 calls to fetchUserProfile run at the same time, using at most 3 fibers.
    *   Async.parTraverseLimit(List(1, 2, 3, 4, 5), concurrency = 3)(fetchUserProfile)
    * }
    * }}}
    *
    * @param items
    *   the collection of elements to process
    * @param concurrency
    *   the maximum number of invocations of `f`, and worker fibers, that run at the same time; a
    *   non-positive value is clamped to `1`
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
    * @throws java.util.concurrent.CancellationException
    *   if the traversal is cancelled by an external cancellation of the enclosing scope, or if `f`
    *   itself throws `InterruptedException`, before every element has been computed; a genuine
    *   failure of `f` propagates unchanged instead
    * @see
    *   [[parTraverse]] for the unbounded variant this builds on
    */
  def parTraverseLimit[A, B](items: Seq[A], concurrency: Int)(f: A => B)(using
      async: Async
  ): Seq[B] = {
    val elements    = items.toIndexedSeq
    val size        = elements.size
    val workerCount = math.min(math.max(1, concurrency), size)
    val nextIndex   = new AtomicInteger(0)
    val results     = new AtomicReferenceArray[AnyRef](size)
    val completed   = new AtomicInteger(0)
    val aborted     = new AtomicBoolean(false)

    val workers = (0 until workerCount).map { workerId =>
      forkNamed(s"parTraverseLimit-worker-$workerId") {
        try {
          var idx = nextIndex.getAndIncrement()
          while (idx < size && !aborted.get() && !Thread.currentThread().isInterrupted()) {
            results.set(idx, f(elements(idx)).asInstanceOf[AnyRef])
            completed.incrementAndGet()
            idx = nextIndex.getAndIncrement()
          }
        } catch {
          case t: Throwable =>
            // Flag the failure before unwinding so sibling workers stop applying `f` to
            // not yet started elements as soon as possible, without waiting for the
            // cooperative interrupt that cancels them to land. A worker can still take an
            // index from `nextIndex` after this, the loop condition just stops it before
            // it invokes `f`.
            aborted.set(true)
            throw t
        }
      }
    }
    try {
      workers.foreach(_.join())
    } catch {
      case t: Throwable =>
        workers.foreach(_.cancel())
        throw t
    }
    // JvmFiber.join() deliberately swallows CancellationException, so a cancelled worker
    // (whether from an external scope cancellation or from claiming an element and then
    // being interrupted before computing it) joins normally instead of throwing here. The
    // completed count is the only reliable signal that every element was actually computed;
    // without it, unwritten slots of `results` would be read as null, matching parTraverse's
    // behaviour of rethrowing cancellation via Fiber.unsafeValue rather than returning a
    // partial result.
    if (completed.get() != size)
      throw new CancellationException("parTraverseLimit was cancelled before completing")
    (0 until size).map(idx => results.get(idx).asInstanceOf[B])
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

  /** Starts a detached computation on its own background daemon virtual thread, completely outside
    * any structured concurrency scope.
    *
    * Every other entry point in this object — [[fork]], [[run]], [[unsupervised]] — binds the
    * computation it starts to a [[StructuredTaskScope]], so it cannot outlive the scope that
    * started it. `detached` is the deliberate exception: it starts `block` on a brand new daemon
    * virtual thread that belongs to no scope at all, gives that thread its own fresh,
    * self-contained [[Async]] capability (via an internal [[run]]), and returns immediately without
    * waiting for it. A failure inside `block` is contained on that background thread; it is
    * captured for observers but never rethrown into the caller, so it can neither fail nor cancel
    * the spawning scope. The one exception is a genuinely fatal error (`VirtualMachineError`,
    * `LinkageError`, ...): it is still captured for observers first, but is then also rethrown on
    * the detached background thread itself, reaching that thread's default uncaught-exception
    * handler -- never the caller's scope either way, since that thread belongs to no structured
    * scope the caller could observe a rethrow through.
    *
    * Being a virtual thread, the background thread is a daemon by construction (the JVM's virtual
    * threads are always daemons), so a detached computation left running never keeps the JVM alive
    * on its own.
    *
    * The returned [[DetachedFiber]] is fire-and-forget: it exposes no `join`/`cancel`, only
    * [[DetachedFiber.onComplete]] / [[DetachedFiber.onFailure]] to observe the eventual outcome.
    *
    * @note
    *   '''This escapes structured concurrency.''' Unlike [[fork]], [[run]], and [[unsupervised]],
    *   the computation started by `detached` is not cancelled when the spawning scope exits, its
    *   failure is never surfaced to that scope, and there is no way to join it from the caller.
    *   Reach for [[fork]] (inside [[run]] or [[unsupervised]]) for anything that should still
    *   respect structured concurrency; reach for `detached` only for genuine fire-and-forget
    *   background work (e.g. best-effort telemetry or logging) that must outlive the scope that
    *   started it. Starting it still requires an ambient [[Async]] capability, exactly like every
    *   other operation in this object: `detached` is a way to escape the spawning *scope*, not a
    *   way to spawn concurrent work from code that holds no [[Async]] capability at all.
    * @note
    *   `block` must be self-terminating. `detached` offers no `cancel`, so a block that never
    *   completes (for example one that calls [[never]] without anything to eventually interrupt
    *   it, since the fresh scope `detached` opens for `block` has nothing left to cancel it either)
    *   leaves the returned [[DetachedFiber]] permanently unsettled — no observer ever fires — and
    *   leaks its parked background thread for the lifetime of the JVM, with no way to reclaim it.
    * @note
    *   An `InterruptedException` thrown by `block` is treated as an ordinary failure, not a
    *   cancellation: it is reported to [[DetachedFiber.onFailure]] with its original type and
    *   message intact. The background thread's interrupt status is deliberately NOT restored:
    *   observers registered before completion run synchronously on this same, about-to-terminate
    *   thread, and setting the flag would make an observer's own interruptible blocking calls fail
    *   spuriously; nothing downstream ever gets a chance to observe the flag anyway. There is no
    *   structured scope here for a cancellation signal to have come from, so, unlike [[fork]],
    *   there is nothing to distinguish it from any other failure.
    * @note
    *   Observer callbacks passed to [[DetachedFiber.onComplete]] / [[DetachedFiber.onFailure]] are
    *   plain `A => Unit` / `Throwable => Unit` functions: they do not receive an ambient [[Async]],
    *   since the spawning scope (and the [[Async]] it provided) may already be gone by the time
    *   they run. A callback that itself needs an [[Async]] capability can open its own with [[run]]
    *   or [[unsupervised]].
    * @note
    *   `block` runs inside its own, freshly opened [[run]] scope, not bare. That scope applies
    *   [[run]]'s usual rule: it waits for every fiber [[fork]]ed inside `block`, joined or not, and
    *   if any of them fails, that failure wins over whatever `block` itself returned or threw. So
    *   `Async.detached { Async.fork { throw Boom() }; 42 }` settles the returned [[DetachedFiber]]
    *   exceptionally with `Boom()` -- the `42` is discarded -- even though `block` itself neither
    *   threw nor was cancelled. Fibers forked inside `block` should be joined (or their failure
    *   otherwise handled) before `block` returns, exactly as with a plain [[run]] call.
    * @note
    *   A [[Raise]] capability captured from the spawning scope and then used inside `block` does
    *   not deliver its typed error at all: `Raise.raise` implements control flow via
    *   `scala.util.boundary`/`break`, and the `boundary` frame that captured capability closed
    *   over lives on the spawning thread's stack, not on this detached background thread. Using it
    *   here throws a bare `scala.util.boundary.Break` instead, which is delivered to
    *   [[DetachedFiber.onFailure]] like any other `NonFatal` failure rather than propagating the
    *   typed error. Open a fresh [[Raise]] handler inside `block` instead.
    *
    * Example:
    * {{{
    * Async.run {
    *   val handle = Async.detached {
    *     sendTelemetry() // keeps running even after this Async.run block returns
    *   }
    *   handle.onComplete(_ => println("telemetry sent"))
    *   handle.onFailure(err => println(s"telemetry failed: ${err.getMessage}"))
    *   "done"
    * } // returns "done" immediately; the detached fiber is not waited on
    * }}}
    *
    * @param block
    *   the computation to run detached; it receives its own freshly created [[Async]] capability,
    *   with its own structured scope, independent of the caller's
    * @param async
    *   the async context; delegates to [[Async.Unsafe.detached]] for the actual spawning
    *   implementation, exactly like every other `Async` operation delegates to its backend
    * @tparam A
    *   the type of value produced by the detached computation
    * @return
    *   a [[DetachedFiber]] handle for attaching completion/failure observers; it offers no
    *   `join`/`cancel`, by design (see the note above)
    * @see
    *   [[unsupervised]], [[fork]] for the structured alternatives that remain bound to the spawning
    *   scope
    */
  def detached[A](block: Async ?=> A)(using async: Async): DetachedFiber[A] = async.detached(block)

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

    /** Like [[fork]], but signals that `block` failing is an expected, recoverable outcome rather
      * than a supervision event.
      *
      * The required property is: a `NonFatal` failure of `block` must be captured on the returned
      * fiber's promise and must NOT be rethrown into the enclosing structured scope — cancellation
      * and fatal errors should still propagate exactly like [[fork]] does. [[Async.raceSuccess]] is
      * built on this: it only fails when *both* branches fail, which requires one losing branch's
      * failure to never abort the race by poisoning the scope.
      *
      * The default implementation below does NOT provide that property — it simply delegates to
      * [[fork]], which always rethrows a `NonFatal` failure into the enclosing scope. Consequently,
      * on a backend that inherits this default, [[Async.raceSuccess]] does NOT honour its
      * documented contract: a losing branch's failure will abort the whole race and surface to the
      * caller instead of being discarded in favor of the winner's value.
      *
      * Any [[Async.Unsafe]] backend that supports `raceSuccess` MUST override this method with an
      * implementation that actually captures the failure instead of rethrowing it; see the JVM
      * backend's `attemptFork` for the JDK structured-concurrency version.
      *
      * @param name
      *   the name of the fiber
      * @param block
      *   the code to execute asynchronously
      * @tparam A
      *   the type of value produced by the block
      * @return
      *   a [[Fiber]] representing the forked computation
      */
    def attemptFork[A](name: String)(block: => A): Fiber[A] = fork(name)(block)

    /** Parks the calling thread until it is cancelled, never returning a value on its own.
      *
      * This is the backend seam behind [[Async.never]]. The JVM backend ([[JvmAsync]]) parks on an
      * uncounted [[java.util.concurrent.CountDownLatch]] that is never counted down: the only way
      * `await()` on it returns is by throwing `InterruptedException` once the thread is
      * interrupted, the same cancellation signal [[delay]] and [[fork]] already rely on.
      *
      * This method has no default implementation; every backend must supply its own. No default
      * would be safe here: returning a value is impossible given the `Nothing` result type,
      * throwing an untracked exception would violate this project's error handling philosophy, and
      * looping to avoid both would burn CPU forever and defeat the purpose of parking.
      *
      * On an interrupt based backend, implement this by parking on any interruptible primitive, the
      * same way [[JvmAsync]] parks on an uncounted `CountDownLatch`. On a backend whose cancellation
      * mechanism is not interrupt based, i.e. one that does not ultimately call `Thread.interrupt()`
      * to cancel a fiber, park on whatever signal that backend uses instead to cancel a fiber.
      *
      * @return
      *   never returns normally
      */
    def never(): Nothing

    /** Starts `block` on its own background computation, detached from any structured concurrency
      * scope, and returns a handle for observing its eventual outcome.
      *
      * This is the backend seam behind [[Async.detached]]. The JVM backend ([[JvmAsync]]) starts
      * `block` on a brand new daemon virtual thread, gives it its own fresh [[Async]] capability via
      * an internal [[Async.run]], and reports its outcome on a plain
      * [[java.util.concurrent.CompletableFuture]] wrapped in a [[JvmDetachedFiber]]. `block`'s
      * failure is always contained here (captured for observers, never rethrown to any caller),
      * since the background computation this method starts belongs to no structured scope that
      * could observe a rethrow. The one exception is a genuinely fatal error: it is still captured
      * for observers first, but is then also rethrown on the detached background thread itself, so
      * it still reaches that thread's own default uncaught-exception handler -- it just never
      * reaches a caller, since there is no caller scope to reach.
      *
      * This method has no default implementation; every backend must supply its own, the same way
      * [[never]] does, so that spawning genuinely detached work is not hard-coded to a single
      * backend's concurrency primitive. Implement this by starting `block` on whatever background
      * execution primitive the backend uses for concurrent work (a daemon thread, an unmanaged
      * fiber, ...), running it fully outside of any structured scope the backend maintains, and
      * reporting its outcome through a [[DetachedFiber]] implementation appropriate to the backend.
      *
      * @param block
      *   the computation to run detached; it receives its own freshly created [[Async]] capability
      * @tparam A
      *   the type of value produced by the detached computation
      * @return
      *   a [[DetachedFiber]] handle for attaching completion/failure observers
      */
    def detached[A](block: Async ?=> A): DetachedFiber[A]
  }
}
