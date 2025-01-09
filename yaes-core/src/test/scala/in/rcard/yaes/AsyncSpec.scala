package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import java.util.concurrent.ConcurrentLinkedQueue
import org.scalatest.TryValues.*

import scala.util.Try

class AsyncSpec extends AnyFlatSpec with Matchers {
  "The Async effect" should "wait the completion of all the forked jobs" in {
    val results = Async.run {
      val queue = new ConcurrentLinkedQueue[String]()
      val job1 = Async.fork {
        Async.delay(1.second)
        queue.add("job1")
      }
      val job2 = Async.fork {
        Async.delay(500.millis)
        queue.add("job2")
      }
      val job3 = Async.fork {
        Async.delay(100.millis)
        queue.add("job3")
      }
      queue
    }

    results.toArray should contain theSameElementsInOrderAs List("job3", "job2", "job1")
  }

  it should "stop the execution if one the job throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val tryResult = Try {
      Async.run {
        val job1 = Async.fork {
          Async.delay(1.second)
          results.add("job1")
        }
        val job2 = Async.fork {
          Async.delay(500.millis)
          results.add("job2")
          throw new RuntimeException("Error")
        }
        val job3 = Async.fork {
          Async.delay(100.millis)
          results.add("job3")
        }
      }
    }

    tryResult.failure.exception shouldBe a[RuntimeException]
    tryResult.failure.exception.getMessage shouldBe "Error"
    results.toArray should contain theSameElementsInOrderAs List("job3", "job2")
  }

  it should "stop the execution if a child job throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val tryResult = Try {
      Async.run {
        val job1 = Async.fork {
          Async.delay(1.second)
          results.add("job1")
        }
        val job2 = Async.fork {
          Async.delay(500.millis)
          results.add("job2")
          Async.fork {
            Async.delay(100.millis)
            throw new RuntimeException("Error")
          }
        }
        val job3 = Async.fork {
          Async.delay(100.millis)
          results.add("job3")
        }
      }
    }

    tryResult.failure.exception shouldBe a[RuntimeException]
    tryResult.failure.exception.getMessage shouldBe "Error"
    results.toArray should contain theSameElementsInOrderAs List("job3", "job2")
  }

  it should "stop the execution if the block throws an exception" in {
    val results = new ConcurrentLinkedQueue[String]()
    val tryResult = Try {
      Async.run {
        val job1 = Async.fork {
          Async.delay(1.second)
          results.add("job1")
        }
        val job2 = Async.fork {
          Async.delay(500.millis)
          results.add("job2")
        }
        val job3 = Async.fork {
          Async.delay(100.millis)
          results.add("job3")
        }
        throw new RuntimeException("Error")
      }
    }

    tryResult.failure.exception shouldBe a[RuntimeException]
    tryResult.failure.exception.getMessage shouldBe "Error"
    results.toArray shouldBe empty
  }

  it should "join the values of different jobs" in {
    val queue = new ConcurrentLinkedQueue[String]()
    val result = Async.run {
      val job1 = Async.fork {
        Async.delay(1.second)
        queue.add("job1")
        42
      }
      val job2 = Async.fork {
        Async.delay(500.millis)
        queue.add("job2")
        43
      }
      job1.value + job2.value
    }

    queue.toArray should contain theSameElementsInOrderAs List("job2", "job1")
    result shouldBe 85
  }

  it should "wait for children jobs to finish" in {
    val results = Async.run {
      val queue = new ConcurrentLinkedQueue[String]()
      val job1 = Async.fork {
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
}
