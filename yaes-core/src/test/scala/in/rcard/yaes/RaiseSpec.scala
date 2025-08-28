package in.rcard.yaes

import in.rcard.yaes.Random.*
import in.rcard.yaes.Yaes.*
import org.scalatest.TryValues.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.IOException
import in.rcard.yaes.Raise.*
import scala.util.Try
import scala.util.Success
import scala.util.Failure

class RaiseSpec extends AsyncFlatSpec with Matchers {

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

  // FIXME
  // it should "compose with other effects" in {
  //   val actualResult: (Random, Raise[String]) ?=> Int = for {
  //     io <- Random.nextInt
  //     result <- Raise {
  //       if (io != 42) io
  //       else Raise.raise("Boom!")
  //     }
  //   } yield result

  //   for {
  //     actualResult <- IO.run { Raise.run { actualResult } }
  //   } yield actualResult shouldBe "Boom!"
  // }

  "withDefault" should "be able to provide a default value if an error is risen" in {
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

  "recover" should "be able to recover from an error" in {
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

  "Raise.either" should "be able to return an Either with the error as the Left value" in {
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

  "Raise.option" should "be able to return an Option with the result as a Some" in {
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

  "Raise.nullable" should "be able to return a nullable value with the result" in {
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

  "ensure" should "raise an error if a condition is not met" in {
    val meaningOfLife = 42
    val actualResult  = Raise.run {
      Raise.ensure(meaningOfLife == 43)("Error")
    }

    actualResult shouldBe "Error"
  }

  it should "not raise an error if a condition is met" in {
    val meaningOfLife = 42
    val actualResult  = Raise.run {
      Raise.ensure(meaningOfLife == 42)("Error")
      42
    }

    actualResult shouldBe 42
  }

  "ensureNotNull" should "return the value if it is not null" in {
    val actualResult = Raise.run {
      Raise.ensureNotNull(42) { "error" }
    }
    actualResult should be(42)
  }

  it should "return the error if the value is null" in {
    val actualResult = Raise.run {
      Raise.ensureNotNull(null) { 43 }
    }
    actualResult should be(43)
  }

  "Raise.catching" should "be able to catch an exception and raise a typed error" in {
    val actualResult = Raise.run {
      Raise.catching {
        throw new RuntimeException("Boom!")
      } { case ex => ex.getMessage }
    }

    actualResult shouldBe "Boom!"
  }

  it should "not catch a fatal exception" in {
    assertThrows[OutOfMemoryError] {
      Raise.run {
        Raise.catching {
          throw new OutOfMemoryError("Boom!")
        } { case ex => ex.getMessage }
      }
    }
  }

  it should "not raise any error if no exception is thrown" in {
    val actualResult = Raise.run {
      Raise.catching {
        42
      } { case ex => ex.getMessage }
    }

    actualResult shouldBe 42
  }

  it should "be able to catch an exception of a given type and raise it" in {
    val actualResult = Raise.run {
      Raise.catching[IOException, Int] {
        throw new IOException("Boom!")
      }
    }

    actualResult shouldBe a[IOException]
    actualResult.asInstanceOf[IOException].getMessage shouldBe "Boom!"
  }

  it should "not catch an exception of a different type" in {
    assertThrows[RuntimeException] {
      Raise.run {
        Raise.catching[IOException, Int] {
          throw new RuntimeException("Boom!")
        }
      }
    }
  }

  it should "not raise an fatal exception" in {
    assertThrows[OutOfMemoryError] {
      Raise.run {
        Raise.catching[OutOfMemoryError, Int] {
          throw new OutOfMemoryError("Boom!")
        }
      }
    }
  }

  "withError" should "return the value if it is not an error" in {
    val actual = Raise.either {
      Raise.withError[Int, String, Int](s => s.length) { 42 }
    }

    actual should be(Right(42))
  }

  it should "return the transformed error if the value is an error" in {
    val actual = Raise.either {
      Raise.withError[Int, String, Int](s => s.length) { Raise.raise("error") }
    }

    actual should be(Left(5))
  }

  "MapError" should "allow defining a strategy that map an error to another one" in {
    val finalLambda: Raise[Int] ?=> String = {
      given MapError[String, Int] = MapError { _.length }
      Raise.raise("Oops!")
    }
    val result: Int | String = Raise.run(finalLambda)
    result shouldBe 5
  }

  it should "return the happy path value if no error is raised" in {
    val finalLambda: Raise[Int] ?=> String = {
      given MapError[String, Int] = MapError { _.length }
      "Hello"
    }
    val result: Int | String = Raise.run(finalLambda)
    result shouldBe "Hello"
  }

  "Either.value" should "return the value for a Right instance" in {
    val one: Either[Nothing, Int] = Right(1)

    val actualResult = Raise.run {
      one.value
    }

    actualResult should be(1)
  }

  it should "raise an error for a Left instance" in {
    val left: Either[String, Int] = Left("Error")

    val actualResult = Raise.run {
      left.value
    }

    actualResult should be("Error")
  }

  "Option.value" should "return the value for a Some instance" in {
    val some: Option[Int] = Some(1)

    val actualResult = Raise.run {
      some.value
    }

    actualResult should be(1)
  }

  it should "raise an error for a None instance" in {
    val none: Option[Int] = None

    val actualResult = Raise.run {
      none.value
    }

    actualResult should be(None)
  }

  "Try.value" should "return the value for a Success instance" in {
    val one: Try[Int]     = Success(1)

    val actualResult = Raise.run {
      one.value
    }

    actualResult should be(1)
  }

  it should "raise an error for a Failure instance" in {
    val exception = new RuntimeException("Error")
    val failure: Try[Int] = Failure(exception)

    val actualResult = Raise.run {
      failure.value
    }

    actualResult should be(exception)
  }
}
