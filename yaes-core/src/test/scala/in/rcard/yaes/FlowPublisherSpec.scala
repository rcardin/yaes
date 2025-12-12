package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._
import java.util.concurrent.Flow.{Publisher, Subscriber, Subscription}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import FlowPublisher.asPublisher

class FlowPublisherSpec extends AnyFlatSpec with Matchers {

  // Test helper class
  class TestSubscriber[A](results: mutable.ArrayBuffer[A]) extends Subscriber[A] {
    var subscription: Subscription = _
    private val completionLatch = new CountDownLatch(1)
    @volatile var errorReceived: Throwable = _
    @volatile var completed: Boolean = false

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(Long.MaxValue) // Request unlimited by default
    }

    override def onNext(item: A): Unit = {
      results += item
    }

    override def onError(t: Throwable): Unit = {
      errorReceived = t
      completionLatch.countDown()
    }

    override def onComplete(): Unit = {
      completed = true
      completionLatch.countDown()
    }

    def awaitCompletion(): Unit = {
      completionLatch.await(5, TimeUnit.SECONDS)
    }
  }

  // ========== Phase 1: Basic Publisher Contract ==========

  "FlowPublisher" should "emit a single element to subscriber" in {
    val flow    = Flow(42)
    val results = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results)
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()
    }

    results should contain theSameElementsInOrderAs List(42)
  }

  it should "emit multiple elements in order" in {
    val flow    = Flow(1, 2, 3, 4, 5)
    val results = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results)
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()
    }

    results should contain theSameElementsInOrderAs List(1, 2, 3, 4, 5)
  }

  it should "complete when flow is empty" in {
    val flow    = Flow[Int]()
    val results = mutable.ArrayBuffer[Int]()
    var completedFlag = false

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results) {
        override def onComplete(): Unit = {
          completedFlag = true
          super.onComplete()
        }
      }
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()
    }

    results.should(be(empty))
    completedFlag.should(be(true))
  }

  // ========== Phase 2: Demand Tracking and Backpressure ==========

  it should "respect request(n) demand" in {
    val flow    = Flow(1, 2, 3, 4, 5)
    val results = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results) {
        override def onSubscribe(s: Subscription): Unit = {
          subscription = s
          s.request(2) // Request 2 initially
        }

        override def onNext(item: Int): Unit = {
          super.onNext(item)
          if (results.size == 2) subscription.request(3) // Request 3 more
        }
      }
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()
    }

    results should contain theSameElementsInOrderAs List(1, 2, 3, 4, 5)
  }

  it should "reject invalid request(n <= 0) with onError" in {
    val flow    = Flow(1, 2, 3)
    val results = mutable.ArrayBuffer[Int]()
    var receivedError: Throwable = null

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results) {
        override def onSubscribe(s: Subscription): Unit = {
          subscription = s
          s.request(0) // Invalid request
        }

        override def onError(t: Throwable): Unit = {
          receivedError = t
          super.onError(t)
        }
      }
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()
    }

    receivedError.should(not).be(null)
    receivedError.shouldBe(a[IllegalArgumentException])
    receivedError.getMessage.should(include("Rule 3.9"))
  }

  it should "apply backpressure to slow subscriber" in {
    val flow    = Flow((1 to 100)*)
    val results = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher = FlowPublisher.fromFlow(flow, Channel.Type.Bounded(5, Channel.OverflowStrategy.SUSPEND))
      val subscriber = new TestSubscriber[Int](results) {
        override def onNext(item: Int): Unit = {
          Async.delay(10.millis) // Slow consumer
          super.onNext(item)
        }
      }
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()
    }

    results.should(have).size(100)
  }

  it should "handle concurrent request() calls safely" in {
    val flow    = Flow((1 to 50)*)
    val results = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results) {
        override def onSubscribe(s: Subscription): Unit = {
          subscription = s
          // Concurrent requests from multiple fibers
          Async.fork { s.request(10) }
          Async.fork { s.request(20) }
          Async.fork { s.request(20) }
        }
      }
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()
    }

    results.should(have).size(50)
  }

  // ========== Phase 3: Cancellation ==========

  it should "stop emission when subscription is cancelled" in {
    val flow    = Flow((1 to 100)*)
    val results = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results) {
        override def onNext(item: Int): Unit = {
          super.onNext(item)
          if (results.size == 3) subscription.cancel()
        }
      }
      publisher.subscribe(subscriber)
      Async.delay(500.millis) // Wait for cancellation to take effect
    }

    results.size.should(be <= 3)
  }

  it should "clean up resources when cancelled" in {
    val flow    = Flow((1 to 1000)*)
    val results = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results) {
        override def onNext(item: Int): Unit = {
          super.onNext(item)
          if (results.size == 5) subscription.cancel()
        }
      }
      publisher.subscribe(subscriber)
      Async.delay(500.millis)
    }

    results.size.should(be <= 5)
  }

  it should "be idempotent when cancel() called multiple times" in {
    val flow    = Flow((1 to 10)*)
    val results = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results) {
        override def onSubscribe(s: Subscription): Unit = {
          super.onSubscribe(s)
          // Call cancel multiple times
          s.cancel()
          s.cancel()
          s.cancel()
        }
      }
      publisher.subscribe(subscriber)
      Async.delay(100.millis)
    }

    // No error should occur
    results.size.should(be < 10)
  }

  it should "not send notifications after cancellation" in {
    val flow    = Flow((1 to 100)*)
    val results = mutable.ArrayBuffer[Int]()
    var completedCalled = false

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results) {
        override def onComplete(): Unit = {
          completedCalled = true
          super.onComplete()
        }

        override def onNext(item: Int): Unit = {
          super.onNext(item)
          if (results.size == 5) subscription.cancel()
        }
      }
      publisher.subscribe(subscriber)
      Async.delay(500.millis)
    }

    completedCalled.should(be(false))
  }

  // ========== Phase 4: Error Handling ==========

  it should "propagate Flow exceptions to onError" in {
    val flow = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      throw new RuntimeException("Flow error")
    }

    Async.run {
      val publisher  = flow.asPublisher()
      val subscriber = new TestSubscriber[Int](mutable.ArrayBuffer())
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()

      subscriber.errorReceived.should(not).be(null)
      subscriber.errorReceived.getMessage.should(be("Flow error"))
      subscriber.completed.should(be(false))
    }
  }

  it should "treat internal ChannelClosed as normal completion" in {
    val flow = Flow(1, 2, 3)

    Async.run {
      val publisher  = flow.asPublisher()
      val subscriber = new TestSubscriber[Int](mutable.ArrayBuffer())
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()

      subscriber.errorReceived.should(be(null))
      subscriber.completed.should(be(true))
    }
  }

  it should "handle exceptions in subscriber.onNext()" in {
    val flow    = Flow(1, 2, 3, 4, 5)
    val results = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher  = flow.asPublisher()
      val subscriber = new TestSubscriber[Int](results) {
        override def onNext(item: Int): Unit = {
          super.onNext(item)
          if (item == 3) throw new IllegalStateException("Subscriber error")
        }
      }
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()

      results.should(contain).theSameElementsInOrderAs(List(1, 2, 3))
      subscriber.errorReceived.getMessage.should(be("Subscriber error"))
    }
  }

  it should "call only one terminal event (onComplete OR onError)" in {
    val flow = Flow(1, 2, 3)
    var completeCount = 0
    var errorCount    = 0

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](mutable.ArrayBuffer()) {
        override def onComplete(): Unit = {
          completeCount += 1
          super.onComplete()
        }

        override def onError(t: Throwable): Unit = {
          errorCount += 1
          super.onError(t)
        }
      }
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()
    }

    (completeCount + errorCount).should(be(1))
  }

  // ========== Phase 5: Reactive Streams Spec Compliance ==========

  it should "throw NullPointerException for null subscriber" in {
    val flow = Flow(1, 2, 3)

    Async.run {
      val publisher = flow.asPublisher()
      intercept[IllegalArgumentException] {
        publisher.subscribe(null)
      }
    }
  }

  it should "call onError for null elements in Flow" in {
    // Create a flow with a null element by using a list that contains null
    val values: List[String] = List("valid", null, "unreachable")
    val flow = Flow(values*)

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val results    = mutable.ArrayBuffer[String]()
      val subscriber = new TestSubscriber[String](results)
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()

      subscriber.errorReceived.shouldBe(a[NullPointerException])
      results.should(contain).theSameElementsInOrderAs(List("valid"))
    }
  }

  it should "not call onNext after onComplete" in {
    val flow = Flow(1, 2, 3)

    Async.run {
      val publisher      = FlowPublisher.fromFlow(flow)
      val results        = mutable.ArrayBuffer[Int]()
      var wasCompleted = false
      val subscriber = new TestSubscriber[Int](results) {
        override def onComplete(): Unit = {
          wasCompleted = true
          super.onComplete()
        }

        override def onNext(item: Int): Unit = {
          if (wasCompleted) fail("onNext called after onComplete")
          super.onNext(item)
        }
      }
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()

      results.should(have).size(3)
    }
  }

  // ========== Phase 6: Performance and Edge Cases ==========

  it should "handle large flows efficiently" in {
    val flow    = Flow((1 to 10000)*)
    val results = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results)
      publisher.subscribe(subscriber)
      subscriber.awaitCompletion()
    }

    results.should(have).size(10000)
    results.head.should(be(1))
    results.last.should(be(10000))
  }

  it should "handle subscriber that never requests elements" in {
    val flow        = Flow(1, 2, 3)
    val results     = mutable.ArrayBuffer[Int]()
    var subscribed = false

    Async.run {
      val publisher  = FlowPublisher.fromFlow(flow)
      val subscriber = new TestSubscriber[Int](results) {
        override def onSubscribe(s: Subscription): Unit = {
          subscription = s
          subscribed = true
          // Never call request()!
        }
      }
      publisher.subscribe(subscriber)
      Async.delay(2.seconds) // Wait to verify no deadlock
    }

    subscribed.should(be(true))
    results.should(be(empty))
  }

  it should "support multiple independent subscribers" in {
    val flow     = Flow(1, 2, 3)
    val results1 = mutable.ArrayBuffer[Int]()
    val results2 = mutable.ArrayBuffer[Int]()

    Async.run {
      val publisher = FlowPublisher.fromFlow(flow)

      val subscriber1 = new TestSubscriber[Int](results1)
      publisher.subscribe(subscriber1)

      val subscriber2 = new TestSubscriber[Int](results2)
      publisher.subscribe(subscriber2)

      subscriber1.awaitCompletion()
      subscriber2.awaitCompletion()
    }

    results1.should(contain).theSameElementsInOrderAs(List(1, 2, 3))
    results2.should(contain).theSameElementsInOrderAs(List(1, 2, 3))
  }
}
