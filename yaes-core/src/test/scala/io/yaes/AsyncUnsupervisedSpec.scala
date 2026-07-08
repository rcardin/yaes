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

  it should "keep sibling fibers running to completion when one forked fiber fails" in {
    val queue        = new ConcurrentLinkedQueue[String]()
    val failed       = new CountDownLatch(1)
    val siblingsDone = new CountDownLatch(2)

    val result = Async.run {
      Async.unsupervised {
        // A fiber that fails after a short delay and is never joined.
        Async.fork {
          try {
            Async.delay(100.millis)
            throw new RuntimeException("fiber boom")
          } finally failed.countDown()
        }
        // Sibling fibers that complete successfully and must not be cancelled.
        Async.fork {
          queue.add("sibling-1")
          siblingsDone.countDown()
        }
        Async.fork {
          queue.add("sibling-2")
          siblingsDone.countDown()
        }
        // Wait for the failing fiber to fail and the siblings to finish before leaving.
        failed.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
        siblingsDone.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
        "ok"
      }
    }

    // The unjoined failure did not propagate, and both siblings ran to completion.
    result shouldBe "ok"
    queue.toArray should contain theSameElementsAs List("sibling-1", "sibling-2")
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
}
