package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues.*

class IOSpec extends AnyFlatSpec with Matchers {

  "The IO effect" should "be able to run a side-effecting operation" in {
    val fortyTwo: IO ?=> Int = IO {
      42
    }

    val fortyThree: IO ?=> Int  = fortyTwo + 1

    IO.run(fortyThree).success.value shouldBe 43
  }

  it should "be able to run a side-effecting operation that throws an exception" in {
    val fortyTwo: IO ?=> Int  = IO {
      throw new RuntimeException("Boom!")
    }

    val fortyThree: IO ?=> Int  = fortyTwo + 1

    val actualResult = IO.run(fortyThree)

    actualResult.failure.exception should have message "Boom!"
  }

  it should "be use map and flatMap from Effect type" in {
    val fortyThree: IO ?=> Int  = for {
      a <- IO(42)
      b <- IO(1)
    } yield a + b

    IO.run(fortyThree).success.value shouldBe 43
  }
}
