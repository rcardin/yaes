package io.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.TimeLimits
import org.scalatest.concurrent.Signaler
import org.scalatest.concurrent.ThreadSignaler
import org.scalatest.time.Seconds
import org.scalatest.time.Span

import scala.concurrent.duration.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class AsyncNeverSpec extends AnyFlatSpec with Matchers with TimeLimits {

  /** Interrupts the test thread when a `failAfter` limit expires.
    *
    * `Async.never` is designed to block forever. A regression (e.g. the park getting swallowed, or
    * cancellation not reaching it) would hang `Async.run`'s `join()` forever instead of failing the
    * test. Interrupting the test thread unwinds that `join()`, whose `close()` then cancels any
    * still-parked fiber, turning a regression into a clean test failure instead of an unbounded
    * hang.
    */
  private given Signaler = ThreadSignaler

  /** Upper bound for every test in this spec. Generous enough to absorb CI jitter. */
  private val unblockTimeLimit: Span = Span(30, Seconds)

  "Async.never" should "not return on its own, keeping the fiber alive until cancelled" in failAfter(
    unblockTimeLimit
  ) {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    Async.run {
      val fiber = Async.fork {
        val neverReturned: Int = Async.never[Int]
        // Statically unreachable, but only as long as `never` honours its contract: a regression
        // making it return on its own would run this line and add a second element to the queue,
        // which is what lets the assertion below tell a still-parked fiber apart from one that
        // already finished. Without it the test would pass even if `never` returned immediately.
        actualQueue.add("never-returned")
        neverReturned
      }
      // Give the fiber ample time to reach the park (and, on a regression, to record its
      // unexpected return) before we sample the queue.
      Async.delay(300.millis)
      actualQueue.add("still-running")
      fiber.cancel()
      fiber.join()
    }

    actualQueue.toArray should contain theSameElementsInOrderAs List("still-running")
  }

  it should "park the fiber instead of busy-waiting, observable as a blocked thread state" in failAfter(
    unblockTimeLimit
  ) {
    val threadRef  = new AtomicReference[Thread]()
    val readyLatch = new CountDownLatch(1)
    Async.run {
      val fiber = Async.fork {
        threadRef.set(Thread.currentThread())
        readyLatch.countDown()
        Async.never[Int]
      }
      // Wait for the fiber to have actually started (and recorded its thread) before sampling
      // its state, instead of guessing with a fixed sleep: on a loaded CI runner the fiber may
      // not have been scheduled yet, which would otherwise read `threadRef` as null.
      readyLatch.await()
      // A short settle after the handshake so the fiber has time to move from RUNNABLE (just
      // past the countDown) into the park inside `never`.
      Async.delay(100.millis)
      val state = threadRef.get().getState
      // A busy-wait / spin loop would observe RUNNABLE here. A real park on an uncounted
      // CountDownLatch.await() (AQS `acquireSharedInterruptibly` plus `LockSupport.park`, not a
      // `Condition` await) always reports WAITING, never TIMED_WAITING.
      state shouldBe Thread.State.WAITING
      fiber.cancel()
      fiber.join()
    }
  }

  it should "stop promptly through the standard interruption path when the fiber is cancelled" in failAfter(
    unblockTimeLimit
  ) {
    val start = java.lang.System.nanoTime()
    Async.run {
      val fiber = Async.fork {
        Async.never[Int]
      }
      Async.delay(200.millis)
      fiber.cancel()
      fiber.join()
    }
    val elapsedMillis = (java.lang.System.nanoTime() - start) / 1000000L

    // Cancellation should be near-instant on top of the 200ms internal delay. Tight enough that
    // a regression where cancellation itself took seconds (rather than being near-instant) would
    // fail this test, unlike the previous 5000L bound which had roughly 25x slack.
    elapsedMillis should be < 800L
  }

  it should "stop promptly when an unsupervised scope exits, without an explicit cancel()" in failAfter(
    unblockTimeLimit
  ) {
    // `Async.run` waits for every forked fiber via structured-concurrency join semantics, so a
    // `never` fiber left un-cancelled there is expected to block `run` forever (that's the
    // documented behavior of a supervised scope, not something `never` should defeat). An
    // `unsupervised` scope, by contrast, is documented to cancel any fiber still running as soon
    // as its block returns, without waiting on it first; that is the scope-exit cancellation path
    // this test exercises.
    val start = java.lang.System.nanoTime()
    Async.run {
      Async.unsupervised {
        Async.fork {
          Async.never[Int]
        }
        // Never joined or cancelled explicitly; unsupervised scope teardown must cancel it.
        42
      }
    } shouldBe 42
    val elapsedMillis = (java.lang.System.nanoTime() - start) / 1000000L

    // Tightened to match the sibling test above: this test has no internal delay at all, so it
    // has less fixed overhead to absorb and can afford the same tight bound.
    elapsedMillis should be < 800L
  }

  it should "not throw any exception when joining a fiber cancelled while parked in never" in failAfter(
    unblockTimeLimit
  ) {
    val actualResult = Async.run {
      val fiber = Async.fork {
        Async.never[Int]
      }
      Async.delay(200.millis)
      fiber.cancel()
      fiber.join()
      42
    }

    actualResult shouldBe 42
  }

  it should "raise Cancelled (not an untracked exception) when asking for the value of a fiber cancelled while parked in never" in failAfter(
    unblockTimeLimit
  ) {
    val actualResult: Int | Async.Cancelled = Raise.run {
      Async.run {
        val fiber = Async.fork {
          Async.never[Int]
        }
        Async.delay(200.millis)
        fiber.cancel()
        fiber.value
      }
    }

    actualResult shouldBe Async.Cancelled
  }

  it should "lose a race against a fast computation, which is cancelled once the fast one wins" in failAfter(
    unblockTimeLimit
  ) {
    val actualResult: Int = Async.run {
      Async.race(
        Async.never[Int],
        {
          Async.delay(200.millis)
          42
        }
      )
    }

    actualResult shouldBe 42
  }

  it should "lose a race regardless of argument order" in failAfter(unblockTimeLimit) {
    val actualResult: Int = Async.run {
      Async.race(
        {
          Async.delay(200.millis)
          42
        },
        Async.never[Int]
      )
    }

    actualResult shouldBe 42
  }

  it should "lose a raceSuccess against a fast computation, which is cancelled once the fast one wins" in failAfter(
    unblockTimeLimit
  ) {
    val actualResult: Int = Async.run {
      Async.raceSuccess(
        Async.never[Int],
        {
          Async.delay(200.millis)
          42
        }
      )
    }

    actualResult shouldBe 42
  }

  it should "lose a raceSuccess regardless of argument order" in failAfter(unblockTimeLimit) {
    val actualResult: Int = Async.run {
      Async.raceSuccess(
        {
          Async.delay(200.millis)
          42
        },
        Async.never[Int]
      )
    }

    actualResult shouldBe 42
  }

  it should "time out through Async.timeout, raising TimedOut" in failAfter(unblockTimeLimit) {
    val actualResult: Int | Async.TimedOut = Raise.run {
      Async.run {
        Async.timeout(200.millis) {
          Async.never[Int]
        }
      }
    }

    actualResult shouldBe Async.TimedOut
  }

  it should "be usable directly as the block's result type without an explicit call site cast" in failAfter(
    unblockTimeLimit
  ) {
    // Compilation-level proof that `never[A]` unifies with any `A`, including a losing branch of
    // `race` paired with a concrete, unrelated result type (String here, Int in other tests).
    val actualResult: String = Async.run {
      Async.race(
        Async.never[String],
        {
          Async.delay(150.millis)
          "done"
        }
      )
    }

    actualResult shouldBe "done"
  }
}
