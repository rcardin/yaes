package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._
import java.util.concurrent.Flow.{Publisher, Subscriber, Subscription}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
}
