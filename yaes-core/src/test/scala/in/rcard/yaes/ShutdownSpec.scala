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
}
