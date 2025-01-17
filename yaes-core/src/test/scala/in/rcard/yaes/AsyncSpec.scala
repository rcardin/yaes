package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import java.util.concurrent.ConcurrentLinkedQueue
import org.scalatest.TryValues.*

import scala.util.Try
import java.util.concurrent.CancellationException
import org.scalatest.EitherValues
import in.rcard.yaes.Async.TimedOut

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
    val raiseResult = Raise.run {
      Async.run {
        val fb1 = Async.fork("fb1") {
          Async.delay(1.second)
          results.add("fb1")
        }
        val fb2 = Async.fork("fb2") {
          Async.delay(500.millis)
          results.add("fb2")
          Raise.raise("Error")
        }
        val fb3 = Async.fork("fb3") {
          Async.delay(100.millis)
          results.add("fb3")
        }
      }
    }

    raiseResult shouldBe "Error"
    results.toArray should contain theSameElementsInOrderAs List("fb3", "fb2")
  }

  it should "stop the execution if a child fiber throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val raiseResult = Raise.run {
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
            Raise.raise("Error")
          }
        }
        val fb3 = Async.fork {
          Async.delay(100.millis)
          results.add("fb3")
        }
      }
    }

    raiseResult shouldBe "Error"
    results.toArray should contain theSameElementsInOrderAs List("fb3", "fb2")
  }

  it should "stop the execution if the block throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val raiseResult = Raise.run {
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
        Raise.raise("Error")
      }
    }

    raiseResult shouldBe "Error"
    results.toArray shouldBe empty
  }

  it should "join the values of different fibers" in {
    val queue = new ConcurrentLinkedQueue[String]()
    val result = Raise.run {
      Async.run {
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
    val actualQueue = Async.run {
      val queue = new ConcurrentLinkedQueue[String]()
      val cancellable = Async.fork {
        Async.delay(2.seconds)
        queue.add("cancellable")
      }
      val fb = Async.fork {
        Async.delay(500.millis)
        cancellable.cancel()
        queue.add("fb2")
      }
      queue
    }
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2")
  }

  it should "not throw an exception if a cancelled fiber is joined" in {

    val actualQueue = Async.run {
      val queue = new ConcurrentLinkedQueue[String]()
      val cancellable = Async.fork {
        Async.delay(2.seconds)
        queue.add("cancellable")
      }
      val fb = Async.fork {
        Async.delay(500.millis)
        cancellable.cancel()
        queue.add("fb2")
      }
      cancellable.join()
      queue
    }
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2")
  }

  it should "not cancel parent fiber if a child fiber was cancelled" in {

    val actualQueue = Async.run {
      val queue = new ConcurrentLinkedQueue[String]()
      val fb1 = Async.fork {
        val innerCancellablefb = Async.fork {
          Async.delay(2.seconds)
          queue.add("cancellable")
        }
        Async.delay(1.second)
        innerCancellablefb.cancel()
        queue.add("fb1")
      }
      val fb = Async.fork {
        Async.delay(500.millis)
        queue.add("fb2")
      }
      queue
    }
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2", "fb1")
  }

  it should "cancel children fibers" in {
    val actualQueue = Async.run {
      val queue = new ConcurrentLinkedQueue[String]()
      val fb1 = Async.fork("fb1") {
        Async.fork("inner-fb") {
          Async.fork("inner-inner-fb") {
            Async.delay(6.seconds)
            queue.add("inner-inner-fb")
          }

          Async.delay(5.seconds)
          queue.add("innerfb")
        }
        Async.delay(1.second)
        queue.add("fb1")
      }
      Async.fork("fb2") {
        Async.delay(500.millis)
        fb1.cancel()
        queue.add("fb2")
      }
      queue
    }
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2")
  }

  it should "not throw any exception when joining a cancelled fiber" in {
    val actualResult = Async.run {
      val cancellable = Async.fork {
        Async.delay(2.seconds)
      }
      Async.delay(500.millis)
      cancellable.cancel()
      cancellable.join()
      42
    }

    actualResult shouldBe 42
  }

  it should "not throw any exception if a fiber is cancelled twice" in {
    val actualResult = Async.run {
      val cancellable = Async.fork {
        Async.delay(2.seconds)
      }
      Async.delay(500.millis)
      cancellable.cancel()
      cancellable.cancel()
      42
    }

    actualResult shouldBe 42
  }

  it should "throw an exception when asking for the value of a cancelled fiber" in {
    // FIXME Abstract on the type of the exception
    val actualResult: Unit | Cancelled = Raise.run {
      Async.run {
        val cancellable = Async.fork {
          Async.delay(2.seconds)
        }
        Async.delay(500.millis)
        cancellable.cancel()
        cancellable.value
      }
    }

    actualResult.isInstanceOf[Cancelled] shouldBe true
  }

  it should "racePair two fibers and return the fastest result if both succeed" in {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val actualResult = Async.run {
      val raceResult = Async.racePair(
        {
          Async.delay(1.second)
          actualQueue.add("fb1")
          42
        }, {
          Async.delay(500.millis)
          actualQueue.add("fb2")
          43
        }
      )

      raceResult match {
        case Right((fb1, result2)) =>
          fb1.join()
          result2
        case Left((result1, fb2)) =>
          fb2.join()
          result1
      }
    }

    actualResult shouldBe 43
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2", "fb1")
  }

  it should "racePair two fibers and return the fastest result if the slowest fails" in {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val actualResult = Raise.run {
      Async.run {
        val raceResult = Async.racePair(
          {
            Async.delay(1.second)
            Raise.raise("Error")
            actualQueue.add("fb1")
            42
          }, {
            Async.delay(500.millis)
            actualQueue.add("fb2")
            43
          }
        )

        raceResult match {
          case Right((fb1, result2)) =>
            fb1.cancel()
            result2
          case Left((result1, fb2)) =>
            fb2.join()
            result1
        }
      }
    }

    actualResult shouldBe 43
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2")
  }

  it should "racePair two fibers and return the error of the fastest one, cancelling the other" in {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val actualResult = Raise.run {
      Async.run {
        val raceResult = Async.racePair(
          {
            Async.delay(1.second)
            actualQueue.add("fb1")
            42
          }, {
            Async.delay(500.millis)
            actualQueue.add("fb2")
            Raise.raise("Error")
            43
          }
        )

        raceResult match {
          case Right((fb1, result2)) =>
            fb1.join()
            result2
          case Left((result1, fb2)) =>
            fb2.join()
            result1
        }
      }
    }

    actualResult shouldBe "Error"
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2")
  }

  it should "race two fibers and return the fastest result and cancel the other" in {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val actualResult = Async.run {
      Async.race(
        {
          Async.delay(1.second)
          actualQueue.add("fb1")
          42
        }, {
          Async.delay(500.millis)
          actualQueue.add("fb2")
          43
        }
      )
    }

    actualResult shouldBe 43
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2")
  }

  it should "race two fibers and return the fastest result if the slowest fails" in {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val actualResult = Raise.run {
      Async.run {
        Async.race(
          {
            Async.delay(1.second)
            Raise.raise("Error")
            actualQueue.add("fb1")
            42
          }, {
            Async.delay(500.millis)
            actualQueue.add("fb2")
            43
          }
        )
      }
    }

    actualResult shouldBe 43
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2")
  }

  it should "race two fibers and return the error of the fastest one, cancelling the other" in {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val actualResult = Raise.run {
      Async.run {
        val raceResult = Async.race(
          {
            Async.delay(1.second)
            actualQueue.add("fb1")
            42
          }, {
            Async.delay(500.millis)
            actualQueue.add("fb2")
            Raise.raise("Error")
            43
          }
        )
      }
    }

    actualResult shouldBe "Error"
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2")
  }

  it should "par two computation and return the result if both succeed" in {
    Async.run {
      val (result1, result2) = Async.par(
        {
          Async.delay(1.second)
          42
        }, {
          Async.delay(500.millis)
          43
        }
      )
      result1 + result2
    } shouldBe 85
  }

  it should "par two computation and return the error of the failing one and cancel the other" in {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val actualResult = Raise.run {
      Async.run {
        val (result1, result2) = Async.par(
          {
            Async.delay(1.second)
            actualQueue.add("fb1")
            42
          }, {
            Async.delay(500.millis)
            actualQueue.add("fb2")
            Raise.raise("Error")
            43
          }
        )
        result1 + result2
      }
    }

    actualResult shouldBe "Error"
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2")
  }

  it should "par two computation and return the error of slowest" in {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val actualResult = Raise.run {
      Async.run {
        val (result1, result2) = Async.par(
          {
            Async.delay(1.second)
            actualQueue.add("fb1")
            Raise.raise("Error")
            42
          }, {
            Async.delay(500.millis)
            actualQueue.add("fb2")
            43
          }
        )
        result1 + result2
      }
    }

    actualResult shouldBe "Error"
    actualQueue.toArray should contain theSameElementsInOrderAs List("fb2", "fb1")
  }

  it should "return the fiber value if completes before timeout" in {
    val actualResult = Raise.run {
      Async.run {
        Async.timeout(1.seconds) {
          Async.delay(500.millis)
          42
        }
      }
    }

    actualResult shouldBe 42
  }

  it should "raise a TimedOut error and cancel the computation if the timeout is reached" in {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val actualResult: Int | TimedOut = Raise.run {
      Async.run {
        Async.timeout(500.millis) {
          Async.delay(1.seconds)
          actualQueue.add("fb1")
          42
        }
      }
    }

    actualQueue.toArray shouldBe empty
    actualResult shouldBe TimedOut
  }
}
