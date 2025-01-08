package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

import java.util.concurrent.ConcurrentLinkedQueue

class AsyncSpec extends AnyFlatSpec with Matchers {
  "An async effect" should "wait the completion of all the forked jobs" in {
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
}
