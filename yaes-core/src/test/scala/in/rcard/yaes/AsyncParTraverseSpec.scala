package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class AsyncParTraverseSpec extends AnyFlatSpec with Matchers {

  "Async.parTraverse" should "process all elements in parallel and return results in order" in {
    val result = Async.run {
      Async.parTraverse(List(1, 2, 3, 4, 5))(x => x * 2)
    }

    result shouldBe Seq(2, 4, 6, 8, 10)
  }

  it should "return an empty sequence for an empty input" in {
    val result = Async.run {
      Async.parTraverse(Seq.empty[Int])(x => x * 2)
    }

    result shouldBe Seq.empty
  }

  it should "handle a single element" in {
    val result = Async.run {
      Async.parTraverse(Seq(42))(x => x.toString)
    }

    result shouldBe Seq("42")
  }

  it should "execute elements concurrently" in {
    val executionOrder = new ConcurrentLinkedQueue[Int]()

    val result = Async.run {
      Async.parTraverse(Seq(1, 2, 3)) { x =>
        // Element 1 is slowest, element 3 is fastest
        Async.delay((100 * (4 - x)).millis)
        executionOrder.add(x)
        x * 10
      }
    }

    // Results should be in input order regardless of execution order
    result shouldBe Seq(10, 20, 30)
    // Execution order should reflect timing (fastest first)
    executionOrder.toArray should contain theSameElementsInOrderAs List(3, 2, 1)
  }

  it should "cancel remaining fibers when one computation fails with an exception" in {
    val executed = new ConcurrentLinkedQueue[Int]()

    val tryResult = scala.util.Try {
      Async.run {
        Async.parTraverse(Seq(1, 2, 3)) { x =>
          if (x == 2) {
            Async.delay(50.millis)
            executed.add(x)
            throw new RuntimeException("boom")
          }
          Async.delay(1.second)
          executed.add(x)
          x
        }
      }
    }

    tryResult.isFailure shouldBe true
    tryResult.failed.get shouldBe a[RuntimeException]
    tryResult.failed.get.getMessage shouldBe "boom"
    // Slow fibers (1 and 3) should have been cancelled before executing
    executed.toArray should not contain 1
    executed.toArray should not contain 3
  }

  it should "propagate Raise errors and cancel remaining fibers" in {
    val executed = new ConcurrentLinkedQueue[Int]()

    val result = Raise.run {
      Async.run {
        Async.parTraverse(Seq(1, 2, 3)) { x =>
          if (x == 2) {
            Async.delay(50.millis)
            executed.add(x)
            Raise.raise("Error from element 2")
          }
          Async.delay(1.second)
          executed.add(x)
          x
        }
      }
    }

    result shouldBe "Error from element 2"
    executed.toArray should contain only 2
  }

  it should "work with different input and output types" in {
    val result = Async.run {
      Async.parTraverse(Seq("hello", "world", "test"))(_.length)
    }

    result shouldBe Seq(5, 5, 4)
  }

  it should "handle a large collection" in {
    val items = (1 to 100).toList

    val result = Async.run {
      Async.parTraverse(items)(x => x * x)
    }

    result shouldBe items.map(x => x * x)
  }

  it should "work with Raise effect for typed error handling" in {
    val result: Either[String, Seq[Int]] = Raise.either {
      Async.run {
        Async.parTraverse(Seq(2, 4, 6)) { x =>
          if (x % 2 != 0) Raise.raise(s"$x is odd")
          x / 2
        }
      }
    }

    result shouldBe Right(Seq(1, 2, 3))
  }

  it should "raise the first error when multiple elements would fail" in {
    val result: Either[String, Seq[Int]] = Raise.either {
      Async.run {
        Async.parTraverse(Seq(1, 3, 5)) { x =>
          Async.delay((50 * x).millis)
          Raise.raise(s"$x failed")
          x
        }
      }
    }

    // The fastest failing fiber (element 1) should win
    result shouldBe Left("1 failed")
  }

  it should "preserve order with delayed computations" in {
    val result = Async.run {
      Async.parTraverse(Seq(3, 1, 2)) { x =>
        Async.delay((x * 100).millis)
        x
      }
    }

    result shouldBe Seq(3, 1, 2)
  }

  it should "work with side-effecting functions" in {
    val counter = new AtomicInteger(0)

    val result = Async.run {
      Async.parTraverse(Seq(10, 20, 30)) { x =>
        counter.incrementAndGet()
        x + 1
      }
    }

    result shouldBe Seq(11, 21, 31)
    counter.get() shouldBe 3
  }
}
