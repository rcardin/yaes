package io.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}

import io.yaes.Async.*

class AsyncUnsupervisedSpec extends AnyFlatSpec with Matchers {

  /** Waits for a latch to reach zero, bounding every synchronisation point in this spec with the
    * same timeout. Returns `false` on timeout so call sites can assert on it.
    */
  private def awaitLatch(latch: CountDownLatch): Boolean =
    latch.await(5, TimeUnit.SECONDS)

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
        awaitLatch(started) shouldBe true
        42
      }
    }

    result shouldBe 42
    // The block returned without waiting for the 10-second delay, and the fiber was cancelled.
    awaitLatch(cancelled) shouldBe true
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
        awaitLatch(failed) shouldBe true
        123
      }
    }

    result shouldBe 123
  }

  it should "keep sibling fibers running to completion when one forked fiber fails" in {
    val queue           = new ConcurrentLinkedQueue[String]()
    val siblingsStarted = new CountDownLatch(2)
    val failed          = new CountDownLatch(1)
    val siblingsDone    = new CountDownLatch(2)

    // Every fiber only signals progress through latches, so the interleaving is fully
    // determined by the handshake below and never by wall-clock timing:
    //   1. both siblings start and then wait on `failed`
    //   2. only once the main body has seen both of them start does the third fiber throw
    //   3. the siblings wake up and do their work
    // Reaching step 3 is what proves a sibling was alive at the moment a peer failed
    // and was not cancelled because of it. Step 2 is gated from the main body rather
    // than from inside the failing fiber, because an assertion inside an unjoined fiber
    // would be swallowed by the unsupervised scope and could not fail the test.
    def sibling(name: String)(using Async): Unit = {
      Async.fork {
        siblingsStarted.countDown()
        // The sibling is alive and waiting while the peer fiber fails.
        if (awaitLatch(failed)) {
          queue.add(name)
          siblingsDone.countDown()
        }
      }
      ()
    }

    val result = Async.run {
      Async.unsupervised {
        sibling("sibling-1")
        sibling("sibling-2")
        // Both siblings are alive before the peer is allowed to fail. Asserting here, in
        // the main body, means a starved fork fails the test instead of silently
        // degrading the handshake.
        awaitLatch(siblingsStarted) shouldBe true
        // A fiber that fails while both siblings are running, and is never joined.
        Async.fork {
          try throw new RuntimeException("fiber boom")
          finally failed.countDown()
        }
        // Leaving the block cancels whatever is still running, so wait for the failure
        // to happen and for the siblings to finish their work afterwards.
        awaitLatch(failed) shouldBe true
        awaitLatch(siblingsDone) shouldBe true
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

  it should "propagate a joined fiber's exception to the caller of Async.unsupervised" in {
    // The interception sits directly around Async.unsupervised so the test pins that the
    // unsupervised scope itself rethrows what join() observed.
    val thrown = Async.run {
      intercept[RuntimeException] {
        Async.unsupervised {
          val fiber = Async.fork {
            throw new RuntimeException("joined boom")
          }
          // join() rethrows the fiber's failure, so the block never completes normally.
          fiber.join()
        }
      }
    }

    thrown.getMessage shouldBe "joined boom"
  }

  it should "return a joined fiber's value via fiber.value" in {
    val result = Raise.either[Cancelled, Int] {
      Async.run {
        Async.unsupervised {
          val fiber = Async.fork {
            7
          }
          fiber.join()
          fiber.value
        }
      }
    }

    result shouldBe Right(7)
  }

  it should "not throw when a cancelled fiber is joined" in {
    val started     = new CountDownLatch(1)
    val interrupted = new CountDownLatch(1)

    val result = Async.run {
      Async.unsupervised {
        val fiber = Async.fork {
          try {
            started.countDown()
            Async.delay(10.seconds)
            "unreached"
          } catch {
            case ie: InterruptedException =>
              // Record the interrupt, then let it propagate so the fiber is really cancelled
              // and join() exercises the cancellation path rather than a normal completion.
              interrupted.countDown()
              throw ie
          }
        }
        // Cancel only after the fiber is actually running so the interrupt lands.
        awaitLatch(started) shouldBe true
        fiber.cancel()
        // A no-op cancel() would fail here within the latch timeout instead of quietly
        // waiting out the full 10-second delay.
        awaitLatch(interrupted) shouldBe true
        fiber.join()
        "ok"
      }
    }

    result shouldBe "ok"
  }
}
