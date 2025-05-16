package in.rcard.yaes

import in.rcard.yaes.Async.*
import org.scalacheck.{Gen, Shrink}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.Duration
import scala.language.postfixOps

class FlowZipWithSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  val genMillis: Gen[Duration] =
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

  "zipWith" should "zip two empty flows" in {
    Async.run {
      forAll(genFlow(), genFlow()) { (flow1, flow2) =>
        val actualQueue = new ConcurrentLinkedQueue[(Any, Any)]()
        val zipped      = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
        actualQueue shouldBe empty
      }
    }
  }

  it should "zip one empty flow with one non empty" in {
    Async.run {
      forAll(genFlow(), genFlow('A')) { (flow1, flow2) =>
        val actualQueue = new ConcurrentLinkedQueue[(Any, Char)]()
        val zipped      = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
        actualQueue shouldBe empty
      }
    }
  }

  it should "zip one non empty with one empty" in {
    Async.run {
      forAll(genFlow('A'), genFlow()) { (flow1, flow2) =>
        val actualQueue = new ConcurrentLinkedQueue[(Char, Any)]()
        val zipped      = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
        actualQueue shouldBe empty
      }
    }
  }

  it should "zip to flows of one element" in {
    Async.run {
      forAll(genFlow('A'), genFlow(1)) { (flow1, flow2) =>
        val actualQueue = new ConcurrentLinkedQueue[(Char, Int)]()
        val zipped      = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
        actualQueue.toArray should contain theSameElementsInOrderAs Seq('A' -> 1)
      }
    }
  }

  it should "zip a flow with one element with another with two" in {
    Async.run {
      forAll(genFlow('A'), genFlow(1, 2)) { (flow1, flow2) =>
        val actualQueue = new ConcurrentLinkedQueue[(Char, Int)]()
        val zipped      = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
        actualQueue.toArray should contain theSameElementsInOrderAs Seq('A' -> 1)
      }
    }
  }

  it should "zip a flow with two elements with another with one" in {
    Async.run {
      forAll(genFlow('A', 'B'), genFlow(1)) { (flow1, flow2) =>
        val actualQueue = new ConcurrentLinkedQueue[(Char, Int)]()
        val zipped      = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
        actualQueue.toArray should contain theSameElementsInOrderAs Seq('A' -> 1)
      }
    }
  }

  it should "zip two flows with two elements each" in {
    Async.run {
      forAll(genFlow('A', 'B'), genFlow(1, 2)) { (flow1, flow2) =>
        val actualQueue = new ConcurrentLinkedQueue[(Char, Int)]()
        val zipped      = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
        actualQueue.toArray should contain theSameElementsInOrderAs Seq('A' -> 1, 'B' -> 2)
      }
    }
  }
}
