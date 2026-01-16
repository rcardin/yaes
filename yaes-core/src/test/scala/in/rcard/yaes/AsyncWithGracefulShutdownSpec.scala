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
    val actualResult = Shutdown.run {
      Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(1.second)) {
          Shutdown.initiateShutdown()
        }
      }
    }

    actualResult shouldBe ()
  }

  it should "run until shutdown initiated" in {
    val counter = new AtomicInteger(0)

    val actualResult = Shutdown.run {
      Raise.run {
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
    }

    counter.get() should be >= 45
    actualResult shouldBe Async.ShutdownTimedOut
  }

  it should "cancel remaining fibers after timeout" in {
    val completed = new AtomicBoolean(false)

    val actualResult = Shutdown.run {
      Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(500.millis)) {
          Async.fork("long-task") {
            Async.delay(10.seconds) // Will be cancelled by timeout
            completed.set(true)
          }

          // Initiate shutdown immediately
          Shutdown.initiateShutdown()
        }
      }
    }

    completed.get() shouldBe false
    actualResult shouldBe Async.ShutdownTimedOut
  }

  it should "complete naturally if work finishes before timeout" in {
    val completed = new AtomicBoolean(false)

    val actualResult = Shutdown.run {
      Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(10.seconds)) {
          Async.fork("quick-task") {
            Async.delay(200.millis)
            completed.set(true)
          }

          Async.delay(500.millis)
          Shutdown.initiateShutdown()
        }
      }
    }

    completed.get() shouldBe true
    actualResult shouldBe ()
  }

  it should "handle multiple forked fibers when shutdown is initiated" in {
    val results = new ConcurrentLinkedQueue[String]()

    val actualResult = Shutdown.run {
      Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(10.seconds)) {
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

          Shutdown.initiateShutdown()
        }
      }
    }

    actualResult shouldBe ()
    results.toArray should contain theSameElementsAs List("fiber1", "fiber2", "fiber3")
  }

  it should "handle multiple forked fibers when no shutdown is initiated" in {
    val results = new ConcurrentLinkedQueue[String]()

    val actualResult = Shutdown.run {
      Raise.run {
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
          ()
        }
      }
    }

    results.toArray should contain theSameElementsAs List("fiber1", "fiber2", "fiber3")
    actualResult shouldBe ()
  }

  it should "invoke shutdown hooks registered with Shutdown effect" in {
    val hookCalled = new AtomicBoolean(false)

    val actualResult = Shutdown.run {
      Shutdown.onShutdown {
        hookCalled.set(true)
      }

      Raise.run {
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
    }

    actualResult shouldBe ()
    hookCalled.get() shouldBe true
  }

  it should "not block indefinitely when fibers do not check shutdown state" in {

    val actualResult = Shutdown.run {
      Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(1.second)) {
          Async.fork("infinite-loop") {
            while (true) {
              Async.delay(100.millis)
            }
          }

          Async.delay(200.millis)
          Shutdown.initiateShutdown()
        }
      }
    }

    actualResult shouldBe Async.ShutdownTimedOut
  }

  it should "propagate exceptions from main program" in {
    val actualResult = Shutdown.run {
      Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(5.seconds)) {

          Raise.raise("Error from daemon")
          "should not reach here"
        }
      }
    }

    actualResult shouldBe "Error from daemon"
  }

  it should "work with nested Async.fork calls" in {
    val results = new ConcurrentLinkedQueue[String]()

    val actualResult = Shutdown.run {
      Raise.run {
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
    }

    actualResult shouldBe ()
    results.toArray should contain allOf ("parent", "child1", "child2")
  }

  it should "wait for forked fibers within deadline after shutdown initiated" in {
    val mainCompleted  = new AtomicBoolean(false)
    val fiberCompleted = new AtomicBoolean(false)

    val actualResult = Shutdown.run {
      Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(5.seconds)) {
          Async.fork("background") {
            // Fiber takes 200ms - well within the 5 second deadline
            Async.delay(200.millis)
            fiberCompleted.set(true)
          }

          // Initiate shutdown immediately, then wait in main task
          // The main task must not exit until fibers complete (or deadline)
          Shutdown.initiateShutdown()
          mainCompleted.set(true)
        }
      }
    }

    // Both should complete - main task waited for fiber
    actualResult shouldBe ()
    mainCompleted.get() shouldBe true
    fiberCompleted.get() shouldBe true
  }

  it should "handle shutdown with no forked fibers" in {
    val completed = new AtomicBoolean(false)

    val actualResult = Shutdown.run {
      Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(1.second)) {
          // No Async.fork calls - just main task work
          Async.delay(100.millis)
          completed.set(true)
          Shutdown.initiateShutdown()
        }
      }
    }

    completed.get() shouldBe true
  }

  it should "propagate exceptions from forked fibers" in {
    var caughtException: Throwable = null

    try {
      Shutdown.run {
        Raise.run {
          Async.withGracefulShutdown(deadline = Deadline.after(5.seconds)) {
            Async.fork("failing-fiber") {
              Async.delay(100.millis)
              throw new RuntimeException("Fiber failed!")
            }

            // Main task waits longer than the fiber
            Async.delay(500.millis)
          }
        }
      }
    } catch {
      case e: RuntimeException => caughtException = e
    }

    caughtException should not be null
    caughtException.getMessage shouldBe "Fiber failed!"
  }

  it should "handle multiple calls to initiateShutdown idempotently" in {
    val shutdownCount = new AtomicInteger(0)

    val actualResult = Shutdown.run {
      Shutdown.onShutdown {
        shutdownCount.incrementAndGet()
      }

      Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(5.seconds)) {
          Async.fork("worker") {
            while (!Shutdown.isShuttingDown()) {
              Async.delay(50.millis)
            }
          }

          Async.delay(100.millis)

          // Call initiateShutdown multiple times
          Shutdown.initiateShutdown()
          Shutdown.initiateShutdown()
          Shutdown.initiateShutdown()
        }
      }
    }

    // Hook should only be called once despite multiple initiateShutdown calls
    shutdownCount.get() shouldBe 1
    actualResult shouldBe ()
  }

  it should "shutdown cleanly even if no initiateShutdown is called" in {
    val completed = new AtomicBoolean(false)

    val actualResult = Shutdown.run {
      Raise.run {
        Async.withGracefulShutdown(deadline = Deadline.after(1.second)) {
          Async.delay(100.millis)
          completed.set(true)
          // No explicit shutdown initiated
        }
      }
    }

    actualResult shouldBe ()
    completed.get() shouldBe true
  }
}
