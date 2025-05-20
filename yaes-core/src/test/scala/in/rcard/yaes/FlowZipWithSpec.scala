package in.rcard.yaes

import in.rcard.yaes.Async.*
import org.scalacheck.{Gen, Shrink}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*
import scala.language.postfixOps

class FlowZipWithSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  private val genMillis: Gen[Duration] =
    Gen
      .oneOf[Long](0, 50, 100, 150, 200)
      .map(_ * 1000 * 1000) // to millis
      .map(Duration.fromNanos)

  private def genFlow[A](as: A*)(using async: Async): Gen[Flow[A]] =
    for {
      delays <- Gen.listOfN(as.length + 1, genMillis)
    } yield Flow.flow[A] {
      delays.zip(as :+ null).foreach { case (delay, element) =>
        Async.delay(delay)
        if (element != null) Flow.emit(element.nn)
      }
    }

  private final case class TestFlow[A](delays: List[Duration], flow: Flow[A], killed: Duration)

  private def genInterruptibleFlow[A](as: A*)(using async: Async): Gen[TestFlow[A]] =
    for {
      killedAt <- genMillis.map(_ * as.length / 2)
      delays   <- Gen.listOfN(as.length + 1, genMillis)
      flow = Flow.flow[A] {
        Raise.withDefault(()) {
          Async.timeout(killedAt) {
            delays.zip(as :+ null).foreach { case (delay, element) =>
              Async.delay(delay)
              if (element != null) Flow.emit(element.nn)
            }
          }
        }
      }
    } yield TestFlow(delays, flow, killedAt)

  private def zipToArray[A, B](left: Flow[A], right: Flow[B])(using async: Async): Array[(A, B)] = {
    val queue  = new ConcurrentLinkedQueue[(A, B)]()
    val zipped = left.zipWith(right)((_, _))
    zipped.collect {
      queue.add(_)
    }
    queue.toArray(Array.empty[(A, B)])
  }

  "zipWith" should "zip two empty flows" in {
    Async.run {
      forAll(genFlow(), genFlow()) { (flow1, flow2) =>
        zipToArray(flow1, flow2) shouldBe empty
      }
    }
  }

  it should "zip one empty flow with one non empty" in {
    Async.run {
      forAll(genFlow(), genFlow('A')) { (flow1, flow2) =>
        zipToArray(flow1, flow2) shouldBe empty
      }
    }
  }

  it should "zip one non empty with one empty" in {
    Async.run {
      forAll(genFlow('A'), genFlow()) { (flow1, flow2) =>
        zipToArray(flow1, flow2) shouldBe empty
      }
    }
  }

  it should "zip to flows of one element" in {
    Async.run {
      forAll(genFlow('A'), genFlow(1)) { (flow1, flow2) =>
        zipToArray(flow1, flow2) should contain theSameElementsInOrderAs Seq('A' -> 1)
      }
    }
  }

  it should "zip a flow with one element with another with two" in {
    Async.run {
      forAll(genFlow('A'), genFlow(1, 2)) { (flow1, flow2) =>
        zipToArray(flow1, flow2) should contain theSameElementsInOrderAs Seq('A' -> 1)
      }
    }
  }

  it should "zip a flow with two elements with another with one" in {
    Async.run {
      forAll(genFlow('A', 'B'), genFlow(1)) { (flow1, flow2) =>
        zipToArray(flow1, flow2) should contain theSameElementsInOrderAs Seq('A' -> 1)
      }
    }
  }

  it should "zip two flows with two elements each" in {
    Async.run {
      forAll(genFlow('A', 'B'), genFlow(1, 2)) { (flow1, flow2) =>
        zipToArray(flow1, flow2) should contain theSameElementsInOrderAs Seq('A' -> 1, 'B' -> 2)
      }
    }
  }

  it should "stop mixing when one flow is canceled" in {
    Async.run {
      val flow1 = Flow.flow {
        Async.delay(50.millis)
        Flow.emit(1)
        Async.delay(50.millis)
        Flow.emit(2)
        Async.delay(50.millis)
        Flow.emit(3)
        Async.delay(50.millis)
      }
      val flow2 = Flow.flow {
        Raise.withDefault(()) {
          Async.timeout(125.millis) {
            Flow.emit('A')
            Async.delay(50.millis)
            Flow.emit('B')
            Async.delay(50.millis)
            Flow.emit('C')
            Async.delay(50.millis)
            Flow.emit('D')
            Async.delay(50.millis)
            Flow.emit('E')
          }
        }
      }
      zipToArray(flow1, flow2) should contain theSameElementsInOrderAs Seq(1 -> 'A', 2 -> 'B')
    }
  }
}
