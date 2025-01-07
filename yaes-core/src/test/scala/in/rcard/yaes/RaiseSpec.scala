package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RaiseSpec extends AnyFlatSpec with Matchers {

  "Raise effect" should "be able to raise a typed error" in {
    val actualResult = Raise.run {
      Raise {
        Raise.raise("Error")
      }
    }

    actualResult shouldBe "Error"
  }

  it should "be able to run a block that does not raise an error" in {
    val actualResult = Raise.run {
      Raise {
        42
      }
    }

    actualResult shouldBe 42
  }

  it should "not catch exceptions" in {
    assertThrows[RuntimeException] {
      Raise.run {
        Raise { throw new RuntimeException("Boom!") }
      }
    }
  }

  it should "compose with other effects" in {
    val actualResult: (Effect[SideEffect], Raise[String]) ?=> Int = for {
      io <- IO { 42 }
      result <- Raise {
        if (io != 42) io
        else Raise.raise("Boom!")
      }
    } yield result

    IO.run { Raise.run { actualResult } } shouldBe "Boom!"
  }
}
