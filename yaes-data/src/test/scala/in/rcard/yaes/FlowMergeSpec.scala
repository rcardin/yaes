package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class FlowMergeSpec extends AnyFlatSpec with Matchers {

  // ========== Empty and Single Flow ==========

  "Flow.merge" should "produce an empty flow when given no flows" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()

    Flow.merge[Int]().collect { value => result += value }

    result shouldBe empty
  }

  it should "behave identically to the single source flow" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()

    Flow.merge(Flow(1, 2, 3)).collect { value => result += value }

    result should contain theSameElementsInOrderAs Seq(1, 2, 3)
  }

  // ========== Multiple Flows ==========

  it should "emit all elements from two flows" in {
    val result = new ConcurrentLinkedQueue[Int]()

    Flow.merge(Flow(1, 2, 3), Flow(4, 5, 6)).collect { value => result.add(value) }

    val collected = result.asScala.toSeq
    collected should contain allOf (1, 2, 3, 4, 5, 6)
    collected should have size 6
  }

  it should "emit all elements from three or more flows" in {
    val result = new ConcurrentLinkedQueue[Int]()

    Flow
      .merge(Flow(1, 2), Flow(3, 4), Flow(5, 6), Flow(7, 8))
      .collect { value => result.add(value) }

    val collected = result.asScala.toSeq
    collected should contain allOf (1, 2, 3, 4, 5, 6, 7, 8)
    collected should have size 8
  }

  // ========== Order Preservation ==========

  it should "preserve relative order within each source flow" in {
    val result = new ConcurrentLinkedQueue[String]()

    val flow1 = Flow.flow[String] {
      Async.run {
        Async.delay(10.millis)
        Flow.emit("a1")
        Async.delay(30.millis)
        Flow.emit("a2")
        Async.delay(30.millis)
        Flow.emit("a3")
      }
    }

    val flow2 = Flow.flow[String] {
      Async.run {
        Flow.emit("b1")
        Async.delay(30.millis)
        Flow.emit("b2")
        Async.delay(30.millis)
        Flow.emit("b3")
      }
    }

    Flow.merge(flow1, flow2).collect { value => result.add(value) }

    val collected = result.asScala.toSeq
    collected should have size 6

    // Elements from flow1 should appear in relative order
    val aElements = collected.filter(_.startsWith("a"))
    aElements should contain theSameElementsInOrderAs Seq("a1", "a2", "a3")

    // Elements from flow2 should appear in relative order
    val bElements = collected.filter(_.startsWith("b"))
    bElements should contain theSameElementsInOrderAs Seq("b1", "b2", "b3")
  }

  // ========== Completion Semantics ==========

  it should "complete only when all source flows have completed" in {
    val result = new ConcurrentLinkedQueue[Int]()

    val slowFlow = Flow.flow[Int] {
      Async.run {
        Async.delay(100.millis)
        Flow.emit(1)
      }
    }

    val fastFlow = Flow.flow[Int] {
      Flow.emit(2)
    }

    Flow.merge(slowFlow, fastFlow).collect { value => result.add(value) }

    val collected = result.asScala.toSeq
    collected should contain allOf (1, 2)
    collected should have size 2
  }

  it should "continue emitting from remaining sources when one completes early" in {
    val result = new ConcurrentLinkedQueue[String]()

    val shortFlow = Flow("short")

    val longFlow = Flow.flow[String] {
      Async.run {
        Flow.emit("long1")
        Async.delay(50.millis)
        Flow.emit("long2")
        Async.delay(50.millis)
        Flow.emit("long3")
      }
    }

    Flow.merge(shortFlow, longFlow).collect { value => result.add(value) }

    val collected = result.asScala.toSeq
    collected should contain allOf ("short", "long1", "long2", "long3")
    collected should have size 4
  }

  // ========== Error Propagation ==========

  it should "propagate errors from a failing source flow" in {
    val failingFlow = Flow.flow[Int] {
      Flow.emit(1)
      throw new RuntimeException("source failed")
    }

    val normalFlow = Flow.flow[Int] {
      Async.run {
        Async.delay(50.millis)
        Flow.emit(2)
      }
    }

    an[RuntimeException] should be thrownBy {
      Flow.merge(failingFlow, normalFlow).collect { _ => () }
    }
  }

  // ========== Non-deterministic Interleaving ==========

  it should "interleave elements based on timing" in {
    val result = new ConcurrentLinkedQueue[String]()

    val slowFlow = Flow.flow[String] {
      Async.run {
        Async.delay(500.millis)
        Flow.emit("slow")
      }
    }

    val fastFlow = Flow.flow[String] {
      Flow.emit("fast")
    }

    Flow.merge(slowFlow, fastFlow).collect { value => result.add(value) }

    val collected = result.asScala.toSeq
    collected should have size 2
    collected should contain allOf ("fast", "slow")
    // Fast element should arrive before slow (500ms gap makes this reliable)
    collected.indexOf("fast") should be < collected.indexOf("slow")
  }

  // ========== Integration with Flow Operators ==========

  it should "work with map operator" in {
    val result = new ConcurrentLinkedQueue[Int]()

    Flow
      .merge(Flow(1, 2), Flow(3, 4))
      .map(_ * 10)
      .collect { value => result.add(value) }

    val collected = result.asScala.toSeq
    collected should contain allOf (10, 20, 30, 40)
    collected should have size 4
  }

  it should "work with take operator" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()

    Flow.merge(Flow(1, 2, 3), Flow(4, 5, 6)).take(3).collect { value => result += value }

    result should have size 3
  }

  it should "work with filter operator" in {
    val result = new ConcurrentLinkedQueue[Int]()

    Flow
      .merge(Flow(1, 2, 3, 4), Flow(5, 6, 7, 8))
      .filter(_ % 2 == 0)
      .collect { value => result.add(value) }

    val collected = result.asScala.toSeq
    collected should contain allOf (2, 4, 6, 8)
    collected should have size 4
  }
}
