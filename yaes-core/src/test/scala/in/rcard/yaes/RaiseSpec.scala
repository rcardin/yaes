package in.rcard.yaes

import in.rcard.yaes.Raise.*
import in.rcard.yaes.Raise.given
import in.rcard.yaes.Random.*
import in.rcard.yaes.Yaes.*
import org.scalatest.TryValues.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.IOException
import scala.util.Failure
import scala.util.Success
import scala.util.Try

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

  it should "nest successfully with differently-typed instances" in {
    val actual = Raise.either {
      Raise.option {
        Raise.raise("error")
      }
    }

    actual should be(Left("error"))
  }

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
      Raise.raise(None)
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
      Raise.raise(null)
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

  "MapError" should "allow defining a strategy that maps an error to another one" in {
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
    val one: Try[Int] = Success(1)

    val actualResult = Raise.run {
      one.value
    }

    actualResult should be(1)
  }

  it should "raise an error for a Failure instance" in {
    val exception         = new RuntimeException("Error")
    val failure: Try[Int] = Failure(exception)

    val actualResult = Raise.run {
      failure.value
    }

    actualResult should be(exception)
  }

  "mapAccumulating" should "map all the element of the iterable" in {

    val actualResult = Raise.run {
      Raise.mapAccumulating(List(1, 2, 3, 4, 5)) { _ + 1 }
    }

    actualResult shouldBe List(2, 3, 4, 5, 6)
  }

  it should "accumulate all the errors" in {
    val actual = Raise.run {
      Raise.mapAccumulating(List(1, 2, 3, 4, 5)) { value =>
        if (value % 2 == 0) {
          Raise.raise(value.toString)
        } else {
          value
        }
      }
    }

    actual shouldBe List("2", "4")
  }

  case class TestError(errors: List[String])
  def combineErrors(error1: TestError, error2: TestError): TestError =
    TestError(error1.errors ++ error2.errors)

  "mapAccumulating with combine function" should "map all the element of the iterable" in {

    val actualResult = Raise.run {
      Raise.mapAccumulating(List(1, 2, 3, 4, 5), combineErrors) { value1 =>
        value1 + 1
      }
    }

    actualResult shouldBe List(2, 3, 4, 5, 6)
  }

  it should "accumulate all the errors using the combine function" in {

    val actualResult = Raise.run {
      Raise.mapAccumulating(List(1, 2, 3, 4, 5), combineErrors) { value =>
        if (value % 2 == 0) {
          Raise.raise(TestError(List(value.toString)))
        } else {
          value
        }
      }
    }

    actualResult shouldBe TestError(List("2", "4"))
  }

  "accumulate" should "combine different values" in {

    val actualResult = Raise.run {
      Raise.accumulate[String, List[Int]] {
        val a = accumulating { 1 }
        val b = accumulating { 2 }
        val c = accumulating { 3 }

        List(a, b, c)
      }
    }

    actualResult should be(List(1, 2, 3))
  }

  it should "accumulate errors" in {

    case class Person(name: String, age: Int)

    def validatePerson(name: String, age: Int): Raise[String] ?=> Person = {
      if (name.isEmpty) Raise.raise("Empty name")
      else if (age < 0) Raise.raise("Negative age")
      else Person(name, age)
    }

    val actualResult = Raise.run {
      Raise.accumulate[String, String] {
        val p1 = accumulating { validatePerson("Alice", 30) }
        val p2 = accumulating { validatePerson("", 25) }
        val p3 = accumulating {
          validatePerson("Charlie", -5)
        }

        s"People: ${p1.name}, ${p2.name}, ${p3.name}"
      }
    }

    actualResult should be(List("Empty name", "Negative age"))
  }

  it should "map all the element of the list" in {
    val actualResult = Raise.run {
      accumulate[String, List[Int]] {
        List(1, 2, 3, 4, 5).map { i =>
          accumulating { i + 1 }
        }
      }
    }

    actualResult shouldBe List(2, 3, 4, 5, 6)
  }

  it should "accumulate all the errors" in {
    val actualResult = Raise.run[List[String], List[Int]] {
      accumulate {
        val result = List(1, 2, 3, 4, 5).map { i =>
          accumulating {
            if (i % 2 == 0) {
              Raise.raise(i.toString)
            } else {
              i
            }
          }
        }
        result
      }
    }

    actualResult shouldBe List("2", "4")
  }

  "TraceWith" should "allow defining a strategy that trace the error and then raise it" in {
    val queue               = collection.mutable.ListBuffer.empty[String]
    given TraceWith[String] = trace => {
      queue += trace.original
      trace.printStackTrace()
    }

    val lambda: Raise[String] ?=> Int = traced {
      raise("Oops!")
    }

    val actual: String | Int = Raise.run(lambda)

    actual shouldBe "Oops!"
    queue should contain("Oops!")
  }

  it should "return the happy path value if no error is raised" in {
    val queue               = collection.mutable.ListBuffer.empty[String]
    given TraceWith[String] = trace => {
      queue += trace.original
      trace.printStackTrace()
    }

    val lambda: Raise[String] ?=> Int = traced {
      42
    }

    val actual: Int | String = Raise.run(lambda)

    actual shouldBe 42
    queue shouldBe empty
  }

  it should "use the default tracing strategy" in {
    import in.rcard.yaes.Raise.given

    val lambda: Raise[String] ?=> Int = traced {
      raise("Oops!")
    }

    val actual: String | Int = Raise.run(lambda)

    actual shouldBe "Oops!"
  }

  "infix type" should "allow tracking errors in a more concise way" in {

    val lambda: Int raises String = {
      Raise.raise("Oops!")
    }

    val actual: String | Int = Raise.run(lambda)

    actual shouldBe "Oops!"
  }

  "rethrowError" should "handle a program that does not raise an error" in {
    def program(using Raise[Throwable]): Int = {
      val x = 21
      val y = 21
      x + y
    }

    val result = program(using Raise.rethrowError)

    result shouldBe 42
  }

  it should "handle a program that raises an error by rethrowing it" in {
    def program(using Raise[Throwable]): Int = {
      Raise.raise(new RuntimeException("Something went wrong"))
      42
    }

    assertThrows[RuntimeException] {
      program(using Raise.rethrowError)
    }
  }

  it should "handle a program that raises an IOException by rethrowing it" in {
    def program(using Raise[Throwable]): String = {
      val data = "start"
      Raise.raise(new IOException("File not found"))
      data + " end"
    }

    val exception = intercept[IOException] {
      program(using Raise.rethrowError)
    }

    exception.getMessage shouldBe "File not found"
  }

  private def int(value: Int): Raise[String] ?=> Int = {
    if value >= 2 then Raise.raise(value.toString)
    else value
  }
}
