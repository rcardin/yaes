package io.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch

import io.yaes.Async.*

class AsyncUnsupervisedSpec extends AnyFlatSpec with Matchers {

  "The Async.unsupervised scope" should "return promptly and cancel a still-running unjoined fiber" in {
    val started   = new CountDownLatch(1)
    val cancelled = new CountDownLatch(1)

    val result = Async.run {
      Async.unsupervised {
        Async.fork {
          try {
            started.countDown()
            Async.delay(10.seconds)
          } catch {
            case _: InterruptedException => cancelled.countDown()
          }
        }
        // Wait until the fiber is actually running before leaving the block.
        started.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
        42
      }
    }

    result shouldBe 42
    // The block returned without waiting for the 10-second delay, and the fiber was cancelled.
    cancelled.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
  }

  it should "propagate an exception thrown from the main body of the block" in {
    val thrown = intercept[RuntimeException] {
      Async.run {
        Async.unsupervised {
          Async.fork {
            Async.delay(10.seconds)
          }
          throw new RuntimeException("boom")
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
          try throw new RuntimeException("fiber boom")
          finally failed.countDown()
        }
        failed.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
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

    val result = Async.run {
      val inner = Async.unsupervised {
        // Unjoined failing fiber: its failure must not escape the unsupervised scope.
        Async.fork {
          try throw new RuntimeException("inner boom")
          finally failed.countDown()
        }
        failed.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
        "inner-done"
      }
      // The outer supervised scope is restored and keeps working after the inner block returns.
      val fiber = Async.fork {
        queue.add("outer-fiber")
      }
      fiber.join()
      queue.add(inner)
      "outer-done"
    }

    result shouldBe "outer-done"
    queue.toArray should contain theSameElementsAs List("outer-fiber", "inner-done")
  }

  it should "still fail fast the outer supervised scope when a fiber forked in it fails" in {
    val cancelled = new CountDownLatch(1)
    val started   = new CountDownLatch(1)

    val thrown = intercept[RuntimeException] {
      Async.run {
        // A long-running sibling in the OUTER supervised scope; it must be cancelled
        // when another outer fiber fails.
        Async.fork {
          try {
            started.countDown()
            Async.delay(10.seconds)
          } catch {
            case _: InterruptedException => cancelled.countDown()
          }
        }
        started.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true

        // The nested unsupervised scope completes normally and is left untouched.
        Async.unsupervised {
          "ignored"
        }

        // A failure in the outer supervised scope still fails the whole scope fast.
        Async.fork {
          throw new RuntimeException("outer boom")
        }
      }
    }

    thrown.getMessage shouldBe "outer boom"
    cancelled.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
  }

  "Nesting Async.run inside Async.unsupervised" should
    "propagate a failure from the inner supervised Async.run out of the unsupervised scope when it is not caught" in {
    val thrown = intercept[RuntimeException] {
      Async.unsupervised {
        Async.run {
          Async.fork {
            throw new RuntimeException("inner run boom")
          }
        }
      }
    }

    thrown.getMessage shouldBe "inner run boom"
  }

  it should "leave the outer unsupervised scope unaffected when the inner Async.run failure is caught" in {
    val caught    = new CountDownLatch(1)
    val started   = new CountDownLatch(1)
    val cancelled = new CountDownLatch(1)

    val result = Async.unsupervised {
      // An unjoined fiber in the outer unsupervised scope: it must be cancelled when the
      // scope exits, and must not be disturbed by the nested supervised run failing.
      Async.fork {
        try {
          started.countDown()
          Async.delay(10.seconds)
        } catch {
          case _: InterruptedException => cancelled.countDown()
        }
      }
      started.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true

      try {
        Async.run {
          Async.fork {
            throw new RuntimeException("inner run boom")
          }
        }
      } catch {
        case e: RuntimeException =>
          e.getMessage shouldBe "inner run boom"
          caught.countDown()
      }

      // The failure was caught, so the outer unsupervised scope completes normally.
      "outer-done"
    }

    result shouldBe "outer-done"
    caught.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
    // The outer unsupervised scope still owns and cancels its unjoined fiber on exit.
    cancelled.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
  }
}
