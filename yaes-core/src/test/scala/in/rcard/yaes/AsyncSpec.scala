package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import java.util.concurrent.ConcurrentLinkedQueue
import org.scalatest.TryValues.*

import scala.util.Try

class AsyncSpec extends AnyFlatSpec with Matchers {
  "The Async effect" should "wait the completion of all the forked fibers" in {
    val results = Async.run {
      val queue = new ConcurrentLinkedQueue[String]()
      val fb1 = Async.fork {
        Async.delay(1.second)
        queue.add("fb1")
      }
      val fb2 = Async.fork {
        Async.delay(500.millis)
        queue.add("fb2")
      }
      val fb3 = Async.fork {
        Async.delay(100.millis)
        queue.add("fb3")
      }
      queue
    }

    results.toArray should contain theSameElementsInOrderAs List("fb3", "fb2", "fb1")
  }

  it should "stop the execution if one the fiber throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val tryResult = Try {
      Async.run {
        val fb1 = Async.fork {
          Async.delay(1.second)
          results.add("fb1")
        }
        val fb2 = Async.fork {
          Async.delay(500.millis)
          results.add("fb2")
          throw new RuntimeException("Error")
        }
        val fb3 = Async.fork {
          Async.delay(100.millis)
          results.add("fb3")
        }
      }
    }

    tryResult.failure.exception shouldBe a[RuntimeException]
    tryResult.failure.exception.getMessage shouldBe "Error"
    results.toArray should contain theSameElementsInOrderAs List("fb3", "fb2")
  }

  it should "stop the execution if a child fiber throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val tryResult = Try {
      Async.run {
        val fb1 = Async.fork {
          Async.delay(1.second)
          results.add("fb1")
        }
        val fb2 = Async.fork {
          Async.delay(500.millis)
          results.add("fb2")
          Async.fork {
            Async.delay(100.millis)
            throw new RuntimeException("Error")
          }
        }
        val fb3 = Async.fork {
          Async.delay(100.millis)
          results.add("fb3")
        }
      }
    }

    tryResult.failure.exception shouldBe a[RuntimeException]
    tryResult.failure.exception.getMessage shouldBe "Error"
    results.toArray should contain theSameElementsInOrderAs List("fb3", "fb2")
  }

  it should "stop the execution if the block throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val tryResult = Try {
      Async.run {
        val fb1 = Async.fork {
          Async.delay(1.second)
          results.add("fb1")
        }
        val fb2 = Async.fork {
          Async.delay(500.millis)
          results.add("fb2")
        }
        val fb3 = Async.fork {
          Async.delay(100.millis)
          results.add("fb3")
        }
        throw new RuntimeException("Error")
      }
    }

    tryResult.failure.exception shouldBe a[RuntimeException]
    tryResult.failure.exception.getMessage shouldBe "Error"
    results.toArray shouldBe empty
  }

  it should "join the values of different fibers" in {
    val queue = new ConcurrentLinkedQueue[String]()
    val result = Async.run {
      val fb1 = Async.fork {
        Async.delay(1.second)
        queue.add("fb1")
        42
      }
      val fb2 = Async.fork {
        Async.delay(500.millis)
        queue.add("fb2")
        43
      }
      fb1.value + fb2.value
    }

    queue.toArray should contain theSameElementsInOrderAs List("fb2", "fb1")
    result shouldBe 85
  }

  it should "wait for children fibers to finish" in {
    val results = Async.run {
      val queue = new ConcurrentLinkedQueue[String]()
      val fb1 = Async.fork {
        Async.fork {
          Async.delay(1.second)
          queue.add("1")
        }
        Async.fork {
          Async.delay(500.millis)
          queue.add("2")
        }
        queue.add("3")
      }
      queue
    }

    results.toArray should contain theSameElementsInOrderAs List("3", "2", "1")
  }

  it should "cancel a fiber at the first suspending point" in {
    val expectedQueue = Async.run {
      val queue = new ConcurrentLinkedQueue[String]()
      val cancellable = Async.fork {
        Async.delay(2.seconds)
        queue.add("cancellable")
      }
      val job = Async.fork {
        Async.delay(500.millis)
        cancellable.cancel()
        queue.add("fb2")
      }
      queue
    }
    expectedQueue.toArray should contain theSameElementsInOrderAs List("fb2")
  }
}
