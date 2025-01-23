package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues.*

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
    val actualResult: (IO, Raise[String]) ?=> Int = for {
      io <- IO { 42 }
      result <- Raise {
        if (io != 42) io
        else Raise.raise("Boom!")
      }
    } yield result

    IO.run { Raise.run { actualResult } }.success.value shouldBe "Boom!"
  }

  it should "be able to provide a default value if an error is risen" in {
    val actualResult = Raise.withDefault(42) {
      Raise.raise("Error")
      43
    }

    actualResult shouldBe 42
  }

  it should "not provide any default value if no error is risen" in {
    val actualResult = Raise.withDefault(42) {
      24
    }

    actualResult shouldBe 24
  }

  it should "be able to recover from an error" in {
    val actualResult = Raise.recover {
      Raise.raise("Error")
      43
    } { case "Error" => 42 }

    actualResult shouldBe 42
  }

  it should "not recover from an error if the error is not the expected one" in {
    assertThrows[RuntimeException] {
      Raise.recover {
        Raise.raise("Error")
      } { case "Boom!" => 42 }
    }
  }

  it should "do not recover if no error is risen" in {
    val actualResult = Raise.recover {
      42
    } { case "Error" => 24 }

    actualResult shouldBe 42
  }

  it should "be able to return an Either with the error as the Left value" in {
    val actualResult: Either[String, Int] = Raise.either {
      Raise.raise("Error")
      42
    }

    actualResult shouldBe Left("Error")
  }

  it should "be able to return an Either with the result as the Right value" in {
    val actualResult: Either[String, Int] = Raise.either {
      42
    }

    actualResult shouldBe Right(42)
  }

  it should "be able to return an Option with the result as a Some" in {
    val actualResult: Option[Int] = Raise.option {
      42
    }

    actualResult shouldBe Some(42)
  }

  it should "be able to return an Option with None if an error is risen" in {
    val actualResult: Option[Int] = Raise.option {
      Raise.raise("Error")
      42
    }

    actualResult shouldBe None
  }

  it should "be able to return a nullable value with the result" in {
    val actualResult: Int | Null = Raise.nullable {
      42
    }

    actualResult shouldBe 42
  }

  it should "be able to return a nullable value with null if an error is risen" in {
    val actualResult: Int | Null = Raise.nullable {
      Raise.raise("Error")
      42
    }

    actualResult shouldBe null.asInstanceOf[Int | Null]
  }
}
