package in.rcard.yaes

import in.rcard.yaes.Channel.OverflowStrategy
import in.rcard.yaes.Channel.buffer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*
import scala.language.postfixOps

class FlowBufferSpec extends AnyFlatSpec with Matchers {

  "buffer" should "emit all values from the original flow with unbounded channel (default)" in {

    val flow = Flow(1, 2, 3)

    val result = ArrayBuffer[Int]()
    flow.buffer().collect { value =>
      result += value
    }

    result should contain theSameElementsInOrderAs Seq(1, 2, 3)
  }

  it should "emit all values with bounded channel" in {

    val flow = Flow(1, 2, 3, 4, 5)

    val result = ArrayBuffer[Int]()
    flow.buffer(Channel.Type.Bounded(2)).collect { value =>
      result += value
    }

    result should contain theSameElementsInOrderAs Seq(1, 2, 3, 4, 5)

  }

  it should "emit all values with rendezvous channel" in {

    val flow = Flow(1, 2, 3)

    val result = ArrayBuffer[Int]()
    flow.buffer(Channel.Type.Rendezvous).collect { value =>
      result += value
    }

    result should contain theSameElementsInOrderAs Seq(1, 2, 3)

  }

  it should "handle empty flow" in {

    val flow = Flow[Int]()

    val result = ArrayBuffer[Int]()
    flow.buffer().collect { value =>
      result += value
    }

    result shouldBe empty

  }

  it should "be a cold operator (producer starts on collect)" in {

    val started = new AtomicBoolean(false)

    val flow = Flow.flow[Int] {
      started.set(true)
      Flow.emit(1)
      Flow.emit(2)
    }

    val bufferedFlow = flow.buffer()

    // Producer should not have started yet
    started.get() shouldBe false

    val result = ArrayBuffer[Int]()
    bufferedFlow.collect { value =>
      result += value
    }

    // Now producer should have started and completed
    started.get() shouldBe true
    result should contain theSameElementsInOrderAs Seq(1, 2)

  }

  it should "drop oldest values with bounded channel and DROP_OLDEST strategy when buffer overflows" in {

    // Create a flow that emits values faster than consumer can process
    val flow = Flow.flow[Int] {
      (1 to 5).foreach { i =>
        Flow.emit(i)
      }
    }

    val result = ArrayBuffer[Int]()

    // Use a bounded channel with capacity 2 and DROP_OLDEST strategy
    // The consumer will be slow, so buffer will overflow
    Async.run {
      flow
        .buffer(Channel.Type.Bounded(2, OverflowStrategy.DROP_OLDEST))
        .collect { value =>
          // Simulate slow consumer on first element to let buffer overflow
          if (value == 1) {
            Async.delay(100.millis)
          }
          result += value
        }
    }

    // With DROP_OLDEST, when buffer is full, oldest values are dropped
    // The exact values depend on timing, but we should have some values dropped
    result.size should be < 5
    // The last values should be preserved (newest)
    result.last shouldBe 5
  }

  it should "drop latest values with bounded channel and DROP_LATEST strategy when buffer overflows" in {
    // Create a flow that emits values faster than consumer can process
    val flow = Flow.flow[Int] {
      (1 to 5).foreach { i =>
        Flow.emit(i)
      }
    }

    val result = ArrayBuffer[Int]()

    // Use a bounded channel with capacity 2 and DROP_LATEST strategy
    Async.run {
      flow
        .buffer(Channel.Type.Bounded(2, OverflowStrategy.DROP_LATEST))
        .collect { value =>
          // Simulate slow consumer on first element to let buffer overflow
          if (value == 1) {
            Async.delay(100.millis)
          }
          result += value
        }
    }
    // With DROP_LATEST, when buffer is full, new values are dropped
    // The exact values depend on timing, but we should have some values dropped
    result.size should be < 5
    // The first values should be preserved (oldest)
    result.head shouldBe 1
  }
}
