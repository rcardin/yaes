package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import in.rcard.yaes.Async.Deadline

class AsyncWithGracefulShutdownSpec extends AnyFlatSpec with Matchers {

  "Async.withGracefulShutdown" should "complete when shutdown is initiated immediately" in {
    // Simplest test - just initiate shutdown immediately
    Shutdown.run {
      Async.withGracefulShutdown(deadline = Deadline.after(1.second)) {
        Shutdown.initiateShutdown()
      }
    }
    // Should complete quickly if shutdown works
    succeed
  }

  it should "run until shutdown initiated" in {
    val counter = new AtomicInteger(0)

    Shutdown.run {
      Async.withGracefulShutdown(deadline = Deadline.after(5.seconds)) {
        Async.fork("worker") {
          while (true) {
            counter.incrementAndGet()
            Async.delay(100.millis)
          }
        }

        // Let it run for a bit then shutdown
        Async.delay(500.millis)
        Shutdown.initiateShutdown()
      }
    }

    counter.get() should be >= 3
  }

  it should "cancel remaining fibers after timeout" in {
    val completed = new AtomicBoolean(false)

    Shutdown.run {
      Async.withGracefulShutdown(deadline = Deadline.after(500.millis)) {
        Async.fork("long-task") {
          Async.delay(10.seconds) // Will be cancelled by timeout
          completed.set(true)
        }

        // Initiate shutdown immediately
        Shutdown.initiateShutdown()
      }
    }

    completed.get() shouldBe false
  }

  it should "complete naturally if work finishes before timeout" in {
    val completed = new AtomicBoolean(false)

    Shutdown.run {
      Async.withGracefulShutdown(deadline = Deadline.after(10.seconds)) {
        Async.fork("quick-task") {
          Async.delay(200.millis)
          completed.set(true)
        }

        Async.delay(500.millis)
        Shutdown.initiateShutdown()
      }
    }

    completed.get() shouldBe true
  }

  it should "handle multiple forked fibers" in {
    val results = new ConcurrentLinkedQueue[String]()

    Shutdown.run {
      Async.withGracefulShutdown(deadline = Deadline.after(5.seconds)) {
        Async.fork("fiber1") {
          Async.delay(100.millis)
          results.add("fiber1")
        }
        Async.fork("fiber2") {
          Async.delay(200.millis)
          results.add("fiber2")
        }
        Async.fork("fiber3") {
          Async.delay(300.millis)
          results.add("fiber3")
        }

        Async.delay(500.millis)
        Shutdown.initiateShutdown()
      }
    }

    results.toArray should contain theSameElementsAs List("fiber1", "fiber2", "fiber3")
  }

  it should "invoke shutdown hooks registered with Shutdown effect" in {
    val hookCalled = new AtomicBoolean(false)

    Shutdown.run {
      Shutdown.onShutdown {
        hookCalled.set(true)
      }

      Async.withGracefulShutdown(deadline = Deadline.after(5.seconds)) {
        Async.fork("worker") {
          while (!Shutdown.isShuttingDown()) {
            Async.delay(100.millis)
          }
        }

        Async.delay(300.millis)
        Shutdown.initiateShutdown()
      }
    }

    hookCalled.get() shouldBe true
  }

  it should "not block indefinitely when fibers do not check shutdown state" in {
    // This test ensures the timeout enforcer correctly cancels fibers
    val startTime = java.lang.System.currentTimeMillis()

    Shutdown.run {
      Async.withGracefulShutdown(deadline = Deadline.after(1.second)) {
        Async.fork("infinite-loop") {
          // Simulates a fiber that doesn't check isShuttingDown
          // but has interruptible points (delay)
          while (true) {
            Async.delay(100.millis)
          }
        }

        Async.delay(200.millis)
        Shutdown.initiateShutdown()
      }
    }

    val elapsed = java.lang.System.currentTimeMillis() - startTime
    // Should complete within timeout + some margin
    elapsed.should(be < 3000L)
  }

  it should "propagate exceptions from main program" in {
    var capturedError: Option[String] = None

    Shutdown.run {
      val result = Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(5.seconds)) {

          Raise.raise("Error from daemon")
          "should not reach here"
        }
      }
      capturedError = Some(result)
    }

    capturedError shouldBe Some("Error from daemon")
  }

  it should "work with nested Async.fork calls" in {
    val results = new ConcurrentLinkedQueue[String]()

    Shutdown.run {
      Async.withGracefulShutdown(deadline = Deadline.after(5.seconds)) {
        Async.fork("parent") {
          Async.fork("child1") {
            Async.delay(100.millis)
            results.add("child1")
          }
          Async.fork("child2") {
            Async.delay(150.millis)
            results.add("child2")
          }
          Async.delay(200.millis)
          results.add("parent")
        }

        Async.delay(500.millis)
        Shutdown.initiateShutdown()
      }
    }

    results.toArray should contain allOf ("parent", "child1", "child2")
  }
}
