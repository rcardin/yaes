package io.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import io.yaes.Async.*

class AsyncNestedRunSpec extends AnyFlatSpec with Matchers {

  "A nested Async.run" should "restore the enclosing unsupervised scope when it returns" in {
    val queue = new ConcurrentLinkedQueue[String]()

    val result = Async.unsupervised {
      val nested = Async.run {
        queue.add("nested")
        1
      }
      // Forking here fails with a NullPointerException if the nested run wiped the enclosing scope.
      val fiber = Async.fork {
        queue.add("outer-fiber")
      }
      fiber.join()
      nested + 1
    }

    result shouldBe 2
    queue.toArray should contain theSameElementsInOrderAs List("nested", "outer-fiber")
  }

  it should "restore the enclosing unsupervised scope when it throws" in {
    val counter = new AtomicInteger(0)

    val result = Async.unsupervised {
      try {
        Async.run {
          throw new RuntimeException("nested boom")
        }
      } catch {
        case _: RuntimeException => ()
      }
      // The enclosing scope must survive the failure of the nested run.
      val fiber = Async.fork {
        counter.incrementAndGet()
      }
      fiber.join()
      counter.get()
    }

    result shouldBe 1
  }

  it should "restore the enclosing run scope when it returns" in {
    val counter = new AtomicInteger(0)

    val result = Async.run {
      val nested = Async.run {
        counter.incrementAndGet()
      }
      val fiber = Async.fork {
        counter.incrementAndGet()
      }
      fiber.join()
      nested + counter.get()
    }

    result shouldBe 3
  }
}
