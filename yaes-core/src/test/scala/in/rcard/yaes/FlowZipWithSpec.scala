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

  // I disable the shrinking for the generator of Durations
  implicit val noShrink: Shrink[Duration] = Shrink.shrinkAny[Duration]

  private def delayedEmpty(t1: Duration)(using async: Async): Flow[Int] = Flow.flow {
    Async.delay(t1)
  }

  private def delayedOne[A](t1: Duration, t2: Duration)(a: A)(using async: Async): Flow[A] =
    Flow.flow {
      Async.delay(t1)
      Flow.emit(a)
      Async.delay(t2)
    }

  private def delayedTwo[A](t1: Duration, t2: Duration, t3: Duration)(a1: A, a2: A)(using
      async: Async
  ): Flow[A] = Flow.flow {
    Async.delay(t1)
    Flow.emit(a1)
    Async.delay(t2)
    Flow.emit(a2)
    Async.delay(t3)
  }

  "zipWith" should "zip two empty flows" in {
    forAll(genMillis, genMillis) { (delay1, delay2) =>
      val actualQueue = new ConcurrentLinkedQueue[(Int, Int)]()
      Async.run {
        val flow1  = delayedEmpty(delay1)
        val flow2  = delayedEmpty(delay2)
        val zipped = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
      }
      actualQueue shouldBe empty
    }
  }

  it should "zip one empty flow with one non empty" in {
    forAll(genMillis, genMillis, genMillis) { (delay1, delay2, delay3) =>
      val actualQueue = new ConcurrentLinkedQueue[(Int, Int)]()
      Async.run {
        val flow1  = delayedEmpty(delay1)
        val flow2  = delayedOne(delay2, delay3)(1)
        val zipped = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
      }
      actualQueue shouldBe empty
    }
  }

  it should "zip one non empty with one empty" in {
    forAll(genMillis, genMillis, genMillis) { (delay1, delay2, delay3) =>
      val actualQueue = new ConcurrentLinkedQueue[(Int, Int)]()
      Async.run {
        val flow1  = delayedOne(delay1, delay2)(1)
        val flow2  = delayedEmpty(delay3)
        val zipped = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
      }
      actualQueue shouldBe empty
    }
  }

  it should "zip to flows of one element" in {
    forAll(genMillis, genMillis, genMillis, genMillis) { (delay1, delay2, delay3, delay4) =>
      val actualQueue = new ConcurrentLinkedQueue[(Char, Int)]()
      Async.run {
        val flow1  = delayedOne(delay1, delay2)('A')
        val flow2  = delayedOne(delay3, delay4)(1)
        val zipped = flow1.zipWith(flow2)((_, _))
        zipped.collect {
          actualQueue.add(_)
        }
      }
      actualQueue.toArray should contain theSameElementsInOrderAs Seq('A' -> 1)
    }
  }

  it should "zip a flow with one element with another with two" in {
    forAll(genMillis, genMillis, genMillis, genMillis, genMillis) {
      (delay1, delay2, delay3, delay4, delay5) =>
        val actualQueue = new ConcurrentLinkedQueue[(Char, Int)]()
        Async.run {
          lazy val flow1 = delayedOne(delay1, delay2)('A')
          lazy val flow2 = delayedTwo(delay3, delay4, delay5)(1, 2)
          val zipped     = flow1.zipWith(flow2)((_, _))
          zipped.collect {
            actualQueue.add(_)
          }
        }
        actualQueue.toArray should contain theSameElementsInOrderAs Seq('A' -> 1)
    }
  }

  it should "zip a flow with two elements with another with one" in {
    forAll(genMillis, genMillis, genMillis, genMillis, genMillis) {
      (delay1, delay2, delay3, delay4, delay5) =>
        val actualQueue = new ConcurrentLinkedQueue[(Char, Int)]()
        Async.run {
          lazy val flow1 = delayedTwo(delay1, delay2, delay3)('A', 'B')
          lazy val flow2 = delayedOne(delay3, delay4)(1)
          val zipped     = flow1.zipWith(flow2)((_, _))
          zipped.collect {
            actualQueue.add(_)
          }
        }
        actualQueue.toArray should contain theSameElementsInOrderAs Seq('A' -> 1)
    }
  }

  it should "zip two flows with two elements each" in {
    forAll(genMillis, genMillis, genMillis, genMillis, genMillis, genMillis) {
      (delay1, delay2, delay3, delay4, delay5, delay6) =>
        val actualQueue = new ConcurrentLinkedQueue[(Char, Int)]()
        Async.run {
          lazy val flow1 = delayedTwo(delay1, delay2, delay3)('A', 'B')
          lazy val flow2 = delayedTwo(delay3, delay4, delay5)(1, 2)
          val zipped     = flow1.zipWith(flow2)((_, _))
          zipped.collect {
            actualQueue.add(_)
          }
        }
        actualQueue.toArray should contain theSameElementsInOrderAs Seq('A' -> 1, 'B' -> 2)
    }
  }
}
