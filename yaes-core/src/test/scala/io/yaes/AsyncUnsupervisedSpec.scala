package io.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import io.yaes.Async.*

class AsyncUnsupervisedSpec extends AnyFlatSpec with Matchers {

  /** Dedicated failure type for these tests.
    *
    * ScalaTest's `TestFailedException` is itself a `RuntimeException`, so `intercept` and `catch`
    * clauses written against `RuntimeException` silently absorb failed assertions and report a
    * misleading message mismatch. Matching on this type keeps assertion failures visible.
    */
  private final class Boom(message: String) extends RuntimeException(message)

  /** Upper bound for every latch wait in this suite. */
  private val AwaitTimeout = 5.seconds

  /** Delay used by sentinel fibers: long enough that the fiber can only end by cancellation. */
  private val SentinelDelay = 10.seconds

  /** Waits for `latch`, bounded by [[AwaitTimeout]]. Returns `true` if the latch reached zero. */
  private def awaitLatch(latch: CountDownLatch): Boolean =
    latch.await(AwaitTimeout.toMillis, TimeUnit.MILLISECONDS)

  /** Forks a long-running fiber that counts `started` down as soon as it runs and `cancelled` down
    * when it is interrupted. It never completes on its own, so it can only be ended by the
    * enclosing scope cancelling it.
    */
  private def forkSentinel(started: CountDownLatch, cancelled: CountDownLatch)(using
      Async
  ): Fiber[Unit] =
    Async.fork {
      try {
        started.countDown()
        Async.delay(SentinelDelay)
      } catch {
        case _: InterruptedException => cancelled.countDown()
      }
    }

  "The Async.unsupervised scope" should "return promptly and cancel a still-running unjoined fiber" in {
    val started   = new CountDownLatch(1)
    val cancelled = new CountDownLatch(1)

    val result = Async.run {
      Async.unsupervised {
        forkSentinel(started, cancelled)
        // Wait until the fiber is actually running before leaving the block.
        awaitLatch(started) shouldBe true
        42
      }
    }

    result shouldBe 42
    // The block returned without waiting for the 10-second delay, and the fiber was cancelled.
    awaitLatch(cancelled) shouldBe true
  }

  it should "propagate an exception thrown from the main body of the block" in {
    val thrown = intercept[Boom] {
      Async.run {
        Async.unsupervised {
          Async.fork {
            Async.delay(SentinelDelay)
          }
          throw new Boom("boom")
        }
      }
    }

    thrown.getMessage shouldBe "boom"
  }

  it should "not fail the scope when an unjoined forked fiber fails" in {
    val failed = new CountDownLatch(1)

    val result = Async.run {
      Async.unsupervised {
        Async.fork {
          try throw new Boom("fiber boom")
          finally failed.countDown()
        }
        awaitLatch(failed) shouldBe true
        123
      }
    }

    result shouldBe 123
  }

  it should "run Async.fork inside the scope without modification and return the block result" in {
    val queue = new ConcurrentLinkedQueue[String]()

    val result = Async.run {
      Async.unsupervised {
        val fiber = Async.fork {
          queue.add("forked")
        }
        fiber.join()
        queue.add("main")
        "done"
      }
    }

    result shouldBe "done"
    queue.toArray should contain theSameElementsAs List("forked", "main")
  }

  "Nesting Async.unsupervised inside Async.run" should
    "not let a failing unjoined fiber in the inner unsupervised scope affect the outer supervised scope" in {
    val failed = new CountDownLatch(1)
    val queue  = new ConcurrentLinkedQueue[String]()

    val (outer, inner) = Async.run {
      val innerResult = Async.unsupervised {
        // Unjoined failing fiber: its failure must not escape the unsupervised scope.
        Async.fork {
          try throw new Boom("inner boom")
          finally failed.countDown()
        }
        awaitLatch(failed) shouldBe true
        "inner-done"
      }
      // The outer supervised scope is restored and keeps working after the inner block returns.
      val fiber = Async.fork {
        queue.add("outer-fiber")
      }
      fiber.join()
      ("outer-done", innerResult)
    }

    outer shouldBe "outer-done"
    inner shouldBe "inner-done"
    queue.toArray should contain theSameElementsAs List("outer-fiber")
  }

  it should "still fail fast the outer supervised scope when a fiber forked in it fails" in {
    val outerStarted    = new CountDownLatch(1)
    val outerCancelled  = new CountDownLatch(1)
    val nestedStarted   = new CountDownLatch(1)
    val nestedCancelled = new CountDownLatch(1)

    // Observations are recorded inside the scope and asserted outside it, so that a latch
    // timeout fails on its own terms instead of being absorbed by `intercept`.
    var outerSentinelRunning    = false
    var nestedSentinelRunning   = false
    var nestedSentinelCancelled = false
    var outerSentinelSurvived   = false

    val thrown = intercept[Boom] {
      Async.run {
        // A long-running sibling in the OUTER supervised scope; it must survive the nested
        // unsupervised scope and be cancelled only when another outer fiber fails.
        forkSentinel(outerStarted, outerCancelled)
        outerSentinelRunning = awaitLatch(outerStarted)

        // The nested unsupervised scope owns a fiber of its own and cancels it on exit.
        Async.unsupervised {
          forkSentinel(nestedStarted, nestedCancelled)
          nestedSentinelRunning = awaitLatch(nestedStarted)
        }
        // Leaving the nested scope cancelled only the fiber that scope owns...
        nestedSentinelCancelled = awaitLatch(nestedCancelled)
        // ...while the outer supervised scope, and its own fiber, keep running.
        outerSentinelSurvived = outerCancelled.getCount == 1

        // A failure in the outer supervised scope still fails the whole scope fast.
        Async.fork {
          throw new Boom("outer boom")
        }
      }
    }

    thrown.getMessage shouldBe "outer boom"
    outerSentinelRunning shouldBe true
    nestedSentinelRunning shouldBe true
    nestedSentinelCancelled shouldBe true
    outerSentinelSurvived shouldBe true
    awaitLatch(outerCancelled) shouldBe true
  }

  "Nesting Async.run inside Async.unsupervised" should
    "propagate a failure from the inner supervised Async.run out of the unsupervised scope when it is not caught" in {
    val thrown = intercept[Boom] {
      Async.unsupervised {
        Async.run {
          Async.fork {
            throw new Boom("inner run boom")
          }
        }
      }
    }

    thrown.getMessage shouldBe "inner run boom"
  }

  it should "leave the outer unsupervised scope usable when the inner Async.run failure is caught" in {
    val started   = new CountDownLatch(1)
    val cancelled = new CountDownLatch(1)
    val queue     = new ConcurrentLinkedQueue[String]()

    var caught = Option.empty[String]

    val result = Async.unsupervised {
      // An unjoined fiber in the outer unsupervised scope: it must be cancelled when the
      // scope exits, and must not be disturbed by the nested supervised run failing.
      forkSentinel(started, cancelled)
      awaitLatch(started) shouldBe true

      try {
        Async.run {
          Async.fork {
            throw new Boom("inner run boom")
          }
        }
      } catch {
        case boom: Boom => caught = Some(boom.getMessage)
      }

      // Observable proof that the nested Async.run handed the outer unsupervised scope back:
      // Async.fork resolves the ambient scope, so it only works if that scope was restored.
      val fiber = Async.fork {
        queue.add("after-inner-run")
      }
      fiber.join()

      "outer-done"
    }

    result shouldBe "outer-done"
    caught shouldBe Some("inner run boom")
    queue.toArray should contain theSameElementsAs List("after-inner-run")
    // The outer unsupervised scope still owns and cancels its unjoined fiber on exit.
    awaitLatch(cancelled) shouldBe true
  }
}
