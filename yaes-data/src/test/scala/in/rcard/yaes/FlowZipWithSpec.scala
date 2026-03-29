package in.rcard.yaes

import in.rcard.yaes.Async.*
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}

class FlowZipWithSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  private val genChars: Gen[List[Char]] =
    Gen.choose(0, 10).flatMap(n => Gen.listOfN(n, Gen.alphaChar))

  private val genInts: Gen[List[Int]] =
    Gen.choose(0, 10).flatMap(n => Gen.listOfN(n, Gen.choose(1, 100)))

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
      zipToArray(Flow[Char](), Flow[Int]()) shouldBe empty
    }
  }

  it should "zip one empty flow with one non empty" in {
    Async.run {
      forAll(genChars.suchThat(_.nonEmpty)) { chars =>
        zipToArray(Flow[Int](), Flow(chars*)) shouldBe empty
      }
    }
  }

  it should "zip one non empty with one empty" in {
    Async.run {
      forAll(genChars.suchThat(_.nonEmpty)) { chars =>
        zipToArray(Flow(chars*), Flow[Int]()) shouldBe empty
      }
    }
  }

  it should "zip two flows of one element" in {
    Async.run {
      forAll(Gen.alphaChar, Gen.choose(1, 100)) { (c, i) =>
        zipToArray(Flow(c), Flow(i)) should contain theSameElementsInOrderAs Seq(c -> i)
      }
    }
  }

  it should "zip two flows of equal length" in {
    Async.run {
      forAll(genChars.suchThat(_.nonEmpty), genInts.suchThat(_.nonEmpty)) { (chars, ints) =>
        val minLen   = chars.length.min(ints.length)
        val left     = chars.take(minLen)
        val right    = ints.take(minLen)
        val expected = left.zip(right)
        zipToArray(Flow(left*), Flow(right*)) should contain theSameElementsInOrderAs expected
      }
    }
  }

  it should "stop at the shorter flow" in {
    Async.run {
      forAll(genChars, genInts) { (chars, ints) =>
        val expected = chars.zip(ints)
        zipToArray(Flow(chars*), Flow(ints*)) should contain theSameElementsInOrderAs expected
      }
    }
  }

  it should "apply the combining function to paired elements" in {
    Async.run {
      forAll(genChars.suchThat(_.nonEmpty), genInts.suchThat(_.nonEmpty)) { (chars, ints) =>
        val queue    = new ConcurrentLinkedQueue[String]()
        val left     = Flow(chars*)
        val right    = Flow(ints*)
        val combined = left.zipWith(right)((c, i) => s"$c:$i")
        combined.collect { queue.add(_) }

        val result   = queue.toArray(Array.empty[String]).toSeq
        val expected = chars.zip(ints).map((c, i) => s"$c:$i")
        result should contain theSameElementsInOrderAs expected
      }
    }
  }

  it should "stop zipping when the operation is cancelled externally" in {
    Async.run {
      val cancelAfter = 3

      forAll(genChars.suchThat(_.length > cancelAfter), genInts.suchThat(_.length > cancelAfter)) {
        (chars, ints) =>
          val result       = new ConcurrentLinkedQueue[(Char, Int)]()
          val pairsEmitted = new CountDownLatch(cancelAfter)

          val flow1 = Flow(chars*)
          val flow2 = Flow(ints*)

          Async.race(
            {
              flow1.zipWith(flow2)((_, _)).collect { pair =>
                result.add(pair)
                pairsEmitted.countDown()
                Thread.sleep(1)
              }
            },
            {
              pairsEmitted.await(5, TimeUnit.SECONDS)
            }
          )

          val collected = result.toArray(Array.empty[(Char, Int)])
          collected.length should be >= cancelAfter
          // Ensure that cancellation actually stops further emission and we don't get all possible pairs
          collected.length should be < chars.zip(ints).length
          val expected = chars.zip(ints).take(cancelAfter)
          collected.take(cancelAfter) should contain theSameElementsInOrderAs expected
      }
    }
  }
}
