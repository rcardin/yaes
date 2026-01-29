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
      .map(_.millis)

  private def genKillTime(delays: List[Duration]): Gen[Duration] = {
    val emissions = delays.scanLeft(delays.head)(_ + _)
    Gen
      .oneOf(emissions)
      .map(_ + 25.millis)
  }

  private def genFlow[A](as: A*)(using async: Async): Gen[Flow[A]] =
    for {
      delays <- Gen.listOfN(as.length + 1, genMillis)
    } yield Flow.flow[A] {
      delays.zip(as :+ null).foreach { case (delay, element) =>
        Async.delay(delay)
        if (element != null) Flow.emit(element.nn)
      }
    }

  private final case class InterruptedFlow[A](
    flow: Flow[A],
    delays: List[Duration],
    killed: Duration
  )

  private def genInterruptedFlow[A](as: A*)(using async: Async): Gen[InterruptedFlow[A]] =
    for {
      delays   <- Gen.listOfN(as.length + 1, genMillis)
      killedAt <- genKillTime(delays.init) // The last delay is after last emitted
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
    } yield InterruptedFlow(flow, delays.init, killedAt)

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

  it should "stop zipping when one of the flows is interrupted" in {
    Async.run {
      val elements1 = List('A', 'B', 'C', 'D', 'F', 'G', 'H', 'I', 'J', 'K')
      val elements2 = 1 to 20
      forAll(genInterruptedFlow(elements1*), genInterruptedFlow(elements2*)) {
        case (InterruptedFlow(flow1, delays1, killed1), InterruptedFlow(flow2, delays2, killed2)) =>
          // The delay of each pair is that of the last one
          val mixedDelays = delays1.zip(delays2).map { case (d1, d2) => d1.max(d2) }
          // Pairs at emitted at the accumulated delay
          val emissions  = mixedDelays.scanLeft(Duration.Zero: Duration)(_ + _)
          // Only those pairs emitted before the first kill are considered
          val killTime   = killed1.min(killed2)
          val beforeKill = emissions.count(_ < killTime) - 1  // -1: Omits first zero
          val expected   = elements1.zip(elements2).take(beforeKill)

          zipToArray(flow1, flow2) should contain theSameElementsInOrderAs expected
      }
    }
  }
}
