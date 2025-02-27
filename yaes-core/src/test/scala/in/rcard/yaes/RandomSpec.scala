package in.rcard.yaes

import in.rcard.yaes.Effect.*
import org.scalatest.TryValues.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RandomSpec extends AnyFlatSpec with Matchers {

  "The Random effect" should "be able to generate a random integer" in {
    val randomInt: Random ?=> Int = Random {
      Random.nextInt
    }

    val actualResult = Random.run(randomInt)

    actualResult shouldBe a[Int]
  }

  "The Random effect" should "be able to generate a random boolean" in {
    val randomBool: Random ?=> Boolean = Random {
      Random.nextBoolean
    }

    val actualResult = Random.run(randomBool)

    actualResult shouldBe a[Boolean]
  }

  it should "be able to generate a random double" in {
    val randomDouble: Random ?=> Double = Random {
      Random.nextDouble
    }

    val actualResult = Random.run(randomDouble)

    actualResult shouldBe a[Double]
  }

  it should "be able to generate a random long" in {
    val randomLong: Random ?=> Long = Random {
      Random.nextLong
    }

    val actualResult = Random.run(randomLong)

    actualResult shouldBe a[Long]
  }

  it should "be able to run a block that does not generate a random number" in {
    val randomInt: Random ?=> Int = Random {
      42
    }

    val actualResult = Random.run(randomInt)

    actualResult shouldBe 42
  }

  it should "compose with other effects" in {
    val actualResult: (IO, Random) ?=> Int = for {
      io <- IO { 42 }
      result <- Random {
        if (io != 42) io
        else scala.util.Random.nextInt()
      }
    } yield result

    IO.run { Random.run { actualResult } }.success.value shouldBe a[Int]
  }
}
