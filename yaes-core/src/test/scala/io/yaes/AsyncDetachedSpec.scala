package io.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.TimeLimits
import org.scalatest.concurrent.Signaler
import org.scalatest.concurrent.ThreadSignaler
import org.scalatest.time.Seconds
import org.scalatest.time.Span

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import io.yaes.Async.*

class AsyncDetachedSpec extends AnyFlatSpec with Matchers with TimeLimits {

  /** Interrupts the test thread when a `failAfter` limit expires, turning a regression that hangs
    * forever into a clean test failure.
    */
  private given Signaler = ThreadSignaler

  /** Upper bound for every test in this spec. Generous enough to absorb CI jitter. */
  private val unblockTimeLimit: Span = Span(30, Seconds)

  /** Upper bound for every latch wait in this suite. */
  private val AwaitTimeout = 5.seconds

  /** Dedicated failure type for these tests, so `intercept`/`catch` clauses matching
    * `RuntimeException` cannot accidentally absorb a failed assertion (`TestFailedException` is
    * itself a `RuntimeException`).
    */
  private final class Boom(message: String) extends RuntimeException(message)

  /** Waits for `latch`, bounded by [[AwaitTimeout]]. Returns `true` if the latch reached zero. */
  private def awaitLatch(latch: CountDownLatch): Boolean =
    latch.await(AwaitTimeout.toMillis, TimeUnit.MILLISECONDS)

  "Async.detached" should "keep running and reach completion after the spawning Async.run block has already returned" in failAfter(
    unblockTimeLimit
  ) {
    val release   = new CountDownLatch(1)
    val completed = new CountDownLatch(1)
    val order     = new ConcurrentLinkedQueue[String]()

    Async.run {
      Async.detached {
        // Only proceeds once the parent test releases it, which happens strictly after
        // Async.run below has already returned. If this ran inside the spawning scope,
        // Async.run's own join() would have to wait for it and could never return first.
        awaitLatch(release) shouldBe true
        order.add("detached-done")
        completed.countDown()
      }
      ()
    }
    order.add("run-returned")
    release.countDown()

    awaitLatch(completed) shouldBe true
    order.toArray should contain theSameElementsInOrderAs List("run-returned", "detached-done")
  }

  it should "keep running and reach completion after the spawning Async.unsupervised block has already returned" in failAfter(
    unblockTimeLimit
  ) {
    val release   = new CountDownLatch(1)
    val completed = new CountDownLatch(1)
    val order     = new ConcurrentLinkedQueue[String]()

    Async.run {
      Async.unsupervised {
        Async.detached {
          awaitLatch(release) shouldBe true
          order.add("detached-done")
          completed.countDown()
        }
        ()
      }
      order.add("unsupervised-returned")
    }
    release.countDown()

    awaitLatch(completed) shouldBe true
    order.toArray should contain theSameElementsInOrderAs List(
      "unsupervised-returned",
      "detached-done"
    )
  }

  it should "require an ambient Async capability to be started, yet still let the computation escape the scope that started it" in failAfter(
    unblockTimeLimit
  ) {
    // `detached` takes `using Async`, the same as every other capability-gated operation in this
    // object. Unlike the comment-only claim this test used to make, this line actually exercises
    // the gate: with no ambient Async capability in scope, `Async.detached { 42 }` fails to
    // compile. Removing `(using async: Async)` from `Async.detached` would make this assertion
    // itself fail (the snippet would newly compile), so this line has teeth where the runtime
    // assertions below do not.
    assertDoesNotCompile("Async.detached { 42 }")

    val release   = new CountDownLatch(1)
    val completed = new CountDownLatch(1)
    val order     = new ConcurrentLinkedQueue[String]()

    // The capability is needed only to *start* the background computation; once started, that
    // computation still runs independent of, and outlives, the scope that provided the
    // capability -- proven the same way as the first test above.
    val handle = Async.run {
      Async.detached[Int] {
        awaitLatch(release) shouldBe true
        order.add("detached-done")
        42
      }
    }
    order.add("run-returned")
    release.countDown()

    handle.onComplete(_ => completed.countDown())

    awaitLatch(completed) shouldBe true
    order.toArray should contain theSameElementsInOrderAs List("run-returned", "detached-done")
  }

  it should "start the computation on a virtual thread, which the JVM always treats as a daemon" in failAfter(
    unblockTimeLimit
  ) {
    val threadRef = new AtomicReference[Thread]()
    val done      = new CountDownLatch(1)

    Async.run {
      Async.detached {
        threadRef.set(Thread.currentThread())
        done.countDown()
      }
      ()
    }

    awaitLatch(done) shouldBe true
    threadRef.get() should not be null
    // This proves the background thread has the daemon/virtual properties that make "a detached
    // computation left running never keeps the JVM alive on its own" true by construction (the
    // JVM never waits for daemon threads before exiting). It does not itself observe the JVM
    // actually exiting with only detached work outstanding, which would require driving a
    // separate process.
    threadRef.get().isDaemon shouldBe true
    threadRef.get().isVirtual shouldBe true
  }

  it should "not propagate a failure thrown inside the detached computation to the spawning scope" in failAfter(
    unblockTimeLimit
  ) {
    val release     = new CountDownLatch(1)
    val aboutToFail = new CountDownLatch(1)

    // The detached computation only throws once released, and it is released only after
    // Async.run below has already returned. If the failure were somehow bound to the spawning
    // scope (e.g. joined by it), Async.run could not have returned "parent-ok" first: it would
    // have had to wait for, and then rethrow, the detached failure.
    val result = Async.run {
      Async.detached {
        awaitLatch(release) shouldBe true
        aboutToFail.countDown()
        throw new Boom("detached boom")
      }
      "parent-ok"
    }

    result shouldBe "parent-ok"
    release.countDown()

    // Confirm the detached computation actually reached its throw, rather than relying on the
    // 2-3ms window between Async.run returning and the assertion above being too short for the
    // (not yet even started) detached block to have failed -- a bug that delayed or dropped the
    // failure entirely would otherwise hide behind that timing coincidence.
    awaitLatch(aboutToFail) shouldBe true
  }

  it should "not cancel sibling work in the spawning scope when the detached computation fails" in failAfter(
    unblockTimeLimit
  ) {
    val siblingDone = new CountDownLatch(1)
    val aboutToFail = new CountDownLatch(1)

    val result = Async.run {
      Async.detached {
        aboutToFail.countDown()
        throw new Boom("detached boom")
      }
      // Wait for the detached computation to have actually reached its throw before forking the
      // sibling, so this test cannot pass merely because the detached block had not even started
      // yet by the time the sibling ran.
      awaitLatch(aboutToFail) shouldBe true

      // A sibling fiber forked in the very same scope must run to completion untouched by the
      // detached failure.
      val sibling = Async.fork {
        siblingDone.countDown()
      }
      sibling.join()
      "sibling-ok"
    }

    result shouldBe "sibling-ok"
    awaitLatch(siblingDone) shouldBe true
  }

  it should "surface an InterruptedException thrown by the detached computation to the failure observer with its original type and message intact" in failAfter(
    unblockTimeLimit
  ) {
    val observed         = new CountDownLatch(1)
    val observedFailure  = new AtomicReference[Throwable]()
    val completeObserved = new AtomicBoolean(false)

    val handle = Async.run {
      Async.detached[Unit] {
        throw new InterruptedException("interrupted boom")
      }
    }
    handle.onComplete(_ => completeObserved.set(true))
    handle.onFailure { err =>
      observedFailure.set(err)
      observed.countDown()
    }

    // Also proves the promise is never left incomplete on this path: if it were, neither observer
    // above would ever fire and this await would time out and fail.
    awaitLatch(observed) shouldBe true
    observedFailure.get() shouldBe a[InterruptedException]
    observedFailure.get().getMessage shouldBe "interrupted boom"
    completeObserved.get() shouldBe false
  }

  it should "not leave the detached background thread's interrupt flag set for an onFailure observer registered before the InterruptedException is thrown" in failAfter(
    unblockTimeLimit
  ) {
    val observerRegistered    = new CountDownLatch(1)
    val observed              = new CountDownLatch(1)
    val interruptedInObserver = new AtomicBoolean(true)

    // The block only throws once the observer below has been registered, and registration
    // happens strictly before this latch is released. That guarantees `promise.completeExceptionally`
    // runs only after `onFailure` has already registered its callback with the still-incomplete
    // `CompletableFuture`, so the callback is guaranteed to run synchronously, on this very
    // detached background thread, rather than racily on whichever thread happens to register late.
    val handle = Async.run {
      Async.detached[Unit] {
        awaitLatch(observerRegistered) shouldBe true
        throw new InterruptedException("interrupted boom")
      }
    }
    handle.onFailure { _ =>
      // If the background thread's interrupt flag were set (e.g. by a `Thread.currentThread().interrupt()`
      // call before `completeExceptionally`), it would be visible right here, since this callback
      // runs on that same thread, synchronously, as part of completing the promise.
      interruptedInObserver.set(Thread.currentThread().isInterrupted)
      observed.countDown()
    }
    observerRegistered.countDown()

    awaitLatch(observed) shouldBe true
    interruptedInObserver.get() shouldBe false
  }

  it should "not leave the detached background thread's interrupt flag set for an onFailure observer registered before a NonFatal exception is thrown" in failAfter(
    unblockTimeLimit
  ) {
    val observerRegistered    = new CountDownLatch(1)
    val observed              = new CountDownLatch(1)
    val interruptedInObserver = new AtomicBoolean(true)

    // Mirrors the InterruptedException-path test above, but exercises the far more common
    // NonFatal path instead. Its clear interrupt flag is not intrinsic to that arm: `block`
    // runs inside its own internal `Async.run`, and it is that inner `Async.run`'s own
    // `Thread.interrupted()` call (Async.scala's `run`, in its `case t: Throwable` arm) that
    // undoes the interrupt flag `JvmAsync.ensureJoined` sets while tearing down the scope, before
    // the exception is rethrown to this NonFatal arm and the promise is completed. An onFailure
    // observer registered before completion runs synchronously on this same thread, so a stray
    // `Thread.currentThread().interrupt()` anywhere on this path would be visible right here.
    val handle = Async.run {
      Async.detached[Unit] {
        awaitLatch(observerRegistered) shouldBe true
        throw new Boom("nonfatal boom")
      }
    }
    handle.onFailure { _ =>
      interruptedInObserver.set(Thread.currentThread().isInterrupted)
      observed.countDown()
    }
    observerRegistered.countDown()

    awaitLatch(observed) shouldBe true
    interruptedInObserver.get() shouldBe false
  }

  it should "let a completion observer run with the value after the detached computation finishes, even once the spawning scope has exited" in failAfter(
    unblockTimeLimit
  ) {
    val release       = new CountDownLatch(1)
    val observed      = new CountDownLatch(1)
    val observedValue = new AtomicReference[Int]()

    val outerResult = Async.run {
      val handle = Async.detached[Int] {
        awaitLatch(release) shouldBe true
        42
      }
      handle.onComplete { value =>
        observedValue.set(value)
        observed.countDown()
      }
      "scope-done"
    }

    outerResult shouldBe "scope-done"
    // The spawning scope has already returned, and the observer has not fired yet: it does not
    // require the scope, or an ambient Async, to eventually run.
    observed.getCount shouldBe 1
    release.countDown()

    awaitLatch(observed) shouldBe true
    observedValue.get() shouldBe 42
  }

  it should "let a failure observer run with the exception when the detached computation fails" in failAfter(
    unblockTimeLimit
  ) {
    val release         = new CountDownLatch(1)
    val observed        = new CountDownLatch(1)
    val observedFailure = new AtomicReference[Throwable]()

    Async.run {
      val handle = Async.detached[Unit] {
        awaitLatch(release) shouldBe true
        throw new Boom("observed boom")
      }
      handle.onFailure { err =>
        observedFailure.set(err)
        observed.countDown()
      }
      ()
    }

    release.countDown()
    awaitLatch(observed) shouldBe true
    observedFailure.get() shouldBe a[Boom]
    observedFailure.get().getMessage shouldBe "observed boom"
  }

  it should "run a completion observer registered after the detached computation has already finished" in failAfter(
    unblockTimeLimit
  ) {
    val firstObserved  = new CountDownLatch(1)
    val secondObserved = new CountDownLatch(1)
    val secondValue    = new AtomicReference[Int]()

    val handle = Async.run {
      Async.detached[Int] {
        99
      }
    }

    handle.onComplete(_ => firstObserved.countDown())
    awaitLatch(firstObserved) shouldBe true

    // At this point the detached computation has already completed; a second, late observer,
    // registered entirely outside any Async scope, must still fire.
    handle.onComplete { value =>
      secondValue.set(value)
      secondObserved.countDown()
    }

    awaitLatch(secondObserved) shouldBe true
    secondValue.get() shouldBe 99
  }

  it should "not fire the failure observer when the detached computation succeeds" in failAfter(
    unblockTimeLimit
  ) {
    val completeObserved = new CountDownLatch(1)
    val failureObserved  = new AtomicBoolean(false)

    val handle = Async.run {
      Async.detached[Int] { 7 }
    }
    handle.onFailure(_ => failureObserved.set(true))
    handle.onComplete(_ => completeObserved.countDown())

    // Wait on the *other* (successful) observer to know the computation has settled and both
    // observers have had their chance to run, then assert the failure observer never fired.
    awaitLatch(completeObserved) shouldBe true
    failureObserved.get() shouldBe false
  }

  it should "not fire the completion observer when the detached computation fails" in failAfter(
    unblockTimeLimit
  ) {
    val failureObserved  = new CountDownLatch(1)
    val completeObserved = new AtomicBoolean(false)

    val handle = Async.run {
      Async.detached[Int] { throw new Boom("discrimination boom") }
    }
    handle.onComplete(_ => completeObserved.set(true))
    handle.onFailure(_ => failureObserved.countDown())

    // Wait on the *other* (failure) observer to know the computation has settled and both
    // observers have had their chance to run, then assert the completion observer never fired.
    awaitLatch(failureObserved) shouldBe true
    completeObserved.get() shouldBe false
  }

  it should "not let a throwing observer prevent a second observer registered on the same handle from running" in failAfter(
    unblockTimeLimit
  ) {
    val secondObserved = new CountDownLatch(1)
    val secondValue    = new AtomicReference[Int]()

    val handle = Async.run {
      Async.detached[Int] { 7 }
    }
    handle.onComplete(_ => throw new Boom("observer boom"))
    handle.onComplete { value =>
      secondValue.set(value)
      secondObserved.countDown()
    }

    awaitLatch(secondObserved) shouldBe true
    secondValue.get() shouldBe 7
  }

  it should "not let a failing detached computation's exception escape to the JVM's default uncaught exception handler" in failAfter(
    unblockTimeLimit
  ) {
    val threadRef       = new AtomicReference[Thread]()
    val observed        = new CountDownLatch(1)
    val uncaught        = new AtomicReference[Throwable]()
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler

    Thread.setDefaultUncaughtExceptionHandler((_, ex) => uncaught.set(ex))
    try {
      val handle = Async.run {
        Async.detached[Unit] {
          threadRef.set(Thread.currentThread())
          throw new Boom("uncaught-check boom")
        }
      }
      handle.onFailure(_ => observed.countDown())

      awaitLatch(observed) shouldBe true
      // Wait for the background thread to fully terminate before checking the handler: if the
      // exception had escaped (e.g. a `throw t` added after `promise.completeExceptionally(t)`),
      // dispatching it to the handler happens as part of the thread's termination, which can race
      // the onFailure callback above. Joining the thread waits past that point deterministically.
      threadRef.get().join(AwaitTimeout.toMillis)
      threadRef.get().isAlive shouldBe false
      uncaught.get() shouldBe null
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }
  }

  it should "surface a fatal error thrown inside the detached computation to both the failure observer and the JVM's default uncaught exception handler" in failAfter(
    unblockTimeLimit
  ) {
    val threadRef       = new AtomicReference[Thread]()
    val observed        = new CountDownLatch(1)
    val observedFailure = new AtomicReference[Throwable]()
    val uncaught        = new AtomicReference[Throwable]()
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler
    val fatal           = new LinkageError("fatal boom")

    // Unlike a NonFatal failure (the test above), a genuinely fatal error is not only contained
    // for observers: it is also rethrown on the detached background thread itself, so it must
    // still reach that thread's own default uncaught-exception handler, exactly like an
    // equivalent failure would on any other unmanaged thread.
    Thread.setDefaultUncaughtExceptionHandler((_, ex) => uncaught.set(ex))
    try {
      val handle = Async.run {
        Async.detached[Unit] {
          threadRef.set(Thread.currentThread())
          throw fatal
        }
      }
      handle.onFailure { err =>
        observedFailure.set(err)
        observed.countDown()
      }

      awaitLatch(observed) shouldBe true
      observedFailure.get() shouldBe fatal

      // Wait for the background thread to fully terminate before checking the handler: dispatch
      // to the handler happens as part of the thread's termination, which can race the onFailure
      // callback above. Joining the thread waits past that point deterministically.
      threadRef.get().join(AwaitTimeout.toMillis)
      threadRef.get().isAlive shouldBe false
      uncaught.get() shouldBe fatal
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }
  }

  it should "support a detached block that forks its own fibers internally" in failAfter(
    unblockTimeLimit
  ) {
    val done  = new CountDownLatch(1)
    val value = new AtomicReference[Int]()

    val handle = Async.run {
      Async.detached[Int] {
        val innerResult = new AtomicReference[Int]()
        val fiber       = Async.fork {
          innerResult.set(21)
          ()
        }
        fiber.join()
        innerResult.get() * 2
      }
    }
    handle.onComplete { v =>
      value.set(v)
      done.countDown()
    }

    awaitLatch(done) shouldBe true
    value.get() shouldBe 42
  }

  it should "fail the handle with an unjoined child fiber's exception even though the block itself returned a value" in failAfter(
    unblockTimeLimit
  ) {
    val observed         = new CountDownLatch(1)
    val observedFailure  = new AtomicReference[Throwable]()
    val completeObserved = new AtomicBoolean(false)

    // `block` runs inside its own internal `Async.run` scope. That scope waits for every fiber
    // forked inside it, joined or not, exactly like a top-level `Async.run` call would; if any of
    // them fails, that failure -- not the value `block` returns -- becomes the outcome. Here
    // `block` forks a fiber that fails and never joins it, then returns `42` and neither throws
    // nor is itself cancelled; the handle must still fail with the child's exception, discarding
    // the `42`.
    val handle = Async.run {
      Async.detached[Int] {
        Async.fork {
          throw new Boom("unjoined child boom")
        }
        42
      }
    }
    handle.onComplete(_ => completeObserved.set(true))
    handle.onFailure { err =>
      observedFailure.set(err)
      observed.countDown()
    }

    awaitLatch(observed) shouldBe true
    observedFailure.get() shouldBe a[Boom]
    observedFailure.get().getMessage shouldBe "unjoined child boom"
    completeObserved.get() shouldBe false
  }

  it should "support a detached block nested inside another detached block" in failAfter(
    unblockTimeLimit
  ) {
    val innerDone  = new CountDownLatch(1)
    val innerValue = new AtomicReference[Int]()

    Async.run {
      Async.detached[Unit] {
        val innerHandle = Async.detached[Int] { 5 }
        innerHandle.onComplete { v =>
          innerValue.set(v)
          innerDone.countDown()
        }
        ()
      }
      ()
    }

    awaitLatch(innerDone) shouldBe true
    innerValue.get() shouldBe 5
  }

  it should "let the completion observer fire with null when the detached block returns null" in failAfter(
    unblockTimeLimit
  ) {
    val done  = new CountDownLatch(1)
    val value = new AtomicReference[String]("not set")

    val handle = Async.run {
      Async.detached[String] {
        null
      }
    }
    handle.onComplete { v =>
      value.set(v)
      done.countDown()
    }

    awaitLatch(done) shouldBe true
    value.get() shouldBe null
  }

  it should "let many detached blocks spawned concurrently from multiple threads all settle" in failAfter(
    unblockTimeLimit
  ) {
    val spawnerCount = 8
    val perSpawner   = 5
    val total        = spawnerCount * perSpawner
    val allDone      = new CountDownLatch(total)
    val values       = new ConcurrentLinkedQueue[Int]()

    val spawners = (0 until spawnerCount).map { i =>
      val spawner = new Thread(() => {
        (0 until perSpawner).foreach { j =>
          val handle = Async.run {
            Async.detached[Int] { i * perSpawner + j }
          }
          handle.onComplete { v =>
            values.add(v)
            allDone.countDown()
          }
        }
      })
      spawner.start()
      spawner
    }
    spawners.foreach(_.join(AwaitTimeout.toMillis))

    awaitLatch(allDone) shouldBe true
    values.size() shouldBe total
    values.asScala.toSet shouldBe (0 until total).toSet
  }
}
