package in.rcard.yaes

import in.rcard.yaes.Async.*
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}

import scala.concurrent.duration.*

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
    val genEqualLengthPairs = for {
      n     <- Gen.choose(1, 10)
      chars <- Gen.listOfN(n, Gen.alphaChar)
      ints  <- Gen.listOfN(n, Gen.choose(1, 100))
    } yield (chars, ints)

    Async.run {
      forAll(genEqualLengthPairs) { (chars, ints) =>
        val expected = chars.zip(ints)
        zipToArray(Flow(chars*), Flow(ints*)) should contain theSameElementsInOrderAs expected
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
    val cancelAfter = 3
    val minSize     = cancelAfter + 4

    val genLargeChars = Gen.choose(minSize, 20).flatMap(n => Gen.listOfN(n, Gen.alphaChar))
    val genLargeInts  = Gen.choose(minSize, 20).flatMap(n => Gen.listOfN(n, Gen.choose(1, 100)))

    Async.run {
      forAll(genLargeChars, genLargeInts) { (chars, ints) =>
        val result       = new ConcurrentLinkedQueue[(Char, Int)]()
        val pairsEmitted = new CountDownLatch(cancelAfter)

        val flow1 = Flow(chars*)
        val flow2 = Flow(ints*)

        Async.racePair(
          {
            flow1.zipWith(flow2)((_, _)).collect { pair =>
              result.add(pair)
              pairsEmitted.countDown()
              Async.delay(1.millis)
            }
          },
          {
            val completed = pairsEmitted.await(5, java.util.concurrent.TimeUnit.SECONDS)
            assert(completed, "Timed out waiting for pairs to be emitted")
          }
        ) match {
          case Left((_, fiber))  => fiber.cancel(); fiber.join()
          case Right((fiber, _)) => fiber.cancel(); fiber.join()
        }

        val collected = result.toArray(Array.empty[(Char, Int)])
        collected.length should be >= cancelAfter
        collected.length should be < chars.zip(ints).length
        val expected = chars.zip(ints).take(cancelAfter)
        collected.take(cancelAfter) should contain theSameElementsInOrderAs expected
      }
    }
  }
}
