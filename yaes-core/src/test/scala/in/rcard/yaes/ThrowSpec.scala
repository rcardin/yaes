package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ThrowSpec extends AnyFlatSpec with Matchers {

  "The Throw effect" should "be able to intercept a given exception" in {
    val failingBlock: Throw[RuntimeException, Int] = Throw[RuntimeException, Int] {
      throw new RuntimeException("Boom!")
    }

    val actualResult = Throw.run(failingBlock)

    actualResult shouldBe a[RuntimeException]
    actualResult.asInstanceOf[RuntimeException].getMessage shouldBe "Boom!"
  }

  it should "be able to run a block that does not throw an exception" in {
    val successfulBlock: Throw[RuntimeException, Int] = Throw[RuntimeException, Int] {
      42
    }

    val actualResult = Throw.run(successfulBlock)

    actualResult shouldBe 42
  }

  it should "not catch fatal exceptions" in {
    assertThrows[OutOfMemoryError] {
      Throw.run {
        throw new OutOfMemoryError("Boom!")
      }
    }
  }

  it should "not catch exceptions that are not declared in the type parameter" in {
    val failingBlock: Throw[RuntimeException, Int] = Throw[RuntimeException, Int] {
      throw Exception("Boom!")
    }
    assertThrows[Exception] {
      Throw.run {
        failingBlock
      }
    }
  }

  it should "compose with other effects" in {

    val actualResult: (Effect[SideEffect], Effect[Ex[RuntimeException]]) ?=> Int = for {
      io <- IO { 42 }
      result <- Throw[RuntimeException, Int] {
          if (io != 42) io
          else throw new RuntimeException("Boom!")
      }
    } yield result

    IO.run { Throw.run { actualResult } } shouldBe a[RuntimeException]
  }
}
