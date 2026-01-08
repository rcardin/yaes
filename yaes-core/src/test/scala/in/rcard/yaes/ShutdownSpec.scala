package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ShutdownSpec extends AnyFlatSpec with Matchers {

  "Shutdown" should "initially not be shutting down" in {
    Shutdown.run {
      Shutdown.isShuttingDown() shouldBe false
    }
  }

  it should "transition to shutting down when initiated" in {
    Shutdown.run {
      val before = Shutdown.isShuttingDown()
      Shutdown.initiateShutdown()
      val after = Shutdown.isShuttingDown()

      before shouldBe false
      after shouldBe true
    }
  }

  it should "be idempotent when calling initiateShutdown multiple times" in {
    Shutdown.run {
      Shutdown.initiateShutdown()
      Shutdown.initiateShutdown()
      Shutdown.initiateShutdown()

      Shutdown.isShuttingDown() shouldBe true
    }
  }

  it should "invoke registered hooks when shutdown is initiated" in {
    Shutdown.run {
      @volatile var hook1Called = false
      @volatile var hook2Called = false

      Shutdown.onShutdown(() => hook1Called = true)
      Shutdown.onShutdown(() => hook2Called = true)

      Shutdown.initiateShutdown()

      hook1Called shouldBe true
      hook2Called shouldBe true
    }
  }

  it should "not fail if a hook throws an exception" in {
    Shutdown.run {
      @volatile var hook2Called = false

      Shutdown.onShutdown(() => throw new RuntimeException("Hook 1 failed"))
      Shutdown.onShutdown(() => hook2Called = true)

      Shutdown.initiateShutdown()

      hook2Called shouldBe true
    }
  }

  it should "not invoke hooks multiple times on repeated shutdown calls" in {
    Shutdown.run {
      @volatile var callCount = 0

      Shutdown.onShutdown(() => callCount += 1)

      Shutdown.initiateShutdown()
      Shutdown.initiateShutdown()
      Shutdown.initiateShutdown()

      callCount shouldBe 1
    }
  }

  it should "ignore hooks registered after shutdown has started" in {
    Shutdown.run {
      @volatile var lateHookCalled = false

      Shutdown.onShutdown(() => {
        // Try to register a hook during shutdown
        Shutdown.onShutdown(() => lateHookCalled = true)
      })

      Shutdown.initiateShutdown()

      lateHookCalled shouldBe false
    }
  }

  it should "handle concurrent hook registration safely" in {
    Shutdown.run {
      @volatile var hookCount = 0
      val threads = (1 to 10).map { _ =>
        new Thread(() => {
          Shutdown.onShutdown(() => {
            hookCount += 1
          })
        })
      }

      threads.foreach(_.start())
      threads.foreach(_.join())

      Shutdown.initiateShutdown()

      hookCount shouldBe 10
    }
  }

  it should "handle concurrent shutdown initiation safely" in {
    Shutdown.run {
      @volatile var hookCallCount = 0

      Shutdown.onShutdown(() => hookCallCount += 1)

      val threads = (1 to 10).map { _ =>
        new Thread(() => {
          Shutdown.initiateShutdown()
        })
      }

      threads.foreach(_.start())
      threads.foreach(_.join())

      hookCallCount shouldBe 1
    }
  }

  it should "not deadlock when hook tries to check shutdown state" in {
    Shutdown.run {
      @volatile var stateChecked = false

      Shutdown.onShutdown(() => {
        // This should not deadlock
        val isShuttingDown = Shutdown.isShuttingDown()
        stateChecked = isShuttingDown
      })

      Shutdown.initiateShutdown()

      stateChecked shouldBe true
    }
  }
}
