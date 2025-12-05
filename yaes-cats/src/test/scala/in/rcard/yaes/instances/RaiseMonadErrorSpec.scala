package in.rcard.yaes.instances

import in.rcard.yaes.Raise
import in.rcard.yaes.instances.raise.RaiseMonadError
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RaiseMonadErrorSpec extends AnyFlatSpec with Matchers {

  "raiseError" should "raise an error" in {
    val raiseMonadError          = RaiseMonadError[String]()
    val actual: String | Nothing = Raise.run { raiseMonadError.raiseError("error") }
    actual shouldBe "error"
  }

  "pure" should "lift a value into the Raise context" in {
    val raiseMonadError      = RaiseMonadError[String]()
    val actual: String | Int = Raise.run { raiseMonadError.pure(42) }
    actual shouldBe 42
  }

  "flatMap" should "chain successful Raise computations" in {
    val raiseMonadError                    = RaiseMonadError[String]()
    val firstLambda: Raise[String] ?=> Int = raiseMonadError.pure(42)
    val actual: String | Int = Raise.run {
      raiseMonadError.flatMap(firstLambda) {
        _ + raiseMonadError.pure(1)
      }
    }
    actual shouldBe 43
  }

  it should "short-circuit the computation when the first lambda raises an error" in {
    val queue           = new scala.collection.mutable.Queue[String]
    val raiseMonadError = RaiseMonadError[String]()
    val firstLambda: Raise[String] ?=> Int = {
      queue.enqueue("first")
      raiseMonadError.raiseError("error")
    }
    val actual: String | Int = Raise.run {
      raiseMonadError.flatMap(firstLambda) { firstValue =>
        queue.enqueue("second")
        firstValue + raiseMonadError.pure(1)
      }
    }
    actual shouldBe "error"
    queue.toList shouldBe List("first")
  }

  it should "short-circuit the computation when the second lambda raises an error" in {
    val queue           = new scala.collection.mutable.Queue[String]
    val raiseMonadError = RaiseMonadError[String]()
    val firstLambda: Raise[String] ?=> Int = {
      queue.enqueue("first")
      raiseMonadError.pure(42)
    }
    val actual: String | Int = Raise.run {
      raiseMonadError.flatMap(firstLambda) { firstValue =>
        queue.enqueue("second")
        val secondValue: Int = raiseMonadError.raiseError("error")
        firstValue + secondValue
      }
    }
    actual shouldBe "error"
    queue.toList shouldBe List("first", "second")
  }

  "ap" should "apply a function in the Raise context to a value in the Raise context" in {
    val raiseMonadError                        = RaiseMonadError[String]()
    val function: Raise[String] ?=> Int => Int = raiseMonadError.pure(_ + 1)
    val value: Raise[String] ?=> Int           = raiseMonadError.pure(42)
    val actual: String | Int = Raise.run {
      raiseMonadError.ap(function)(value)
    }
    actual shouldBe 43
  }

  it should "short-circuit the computation when the function raises an error" in {
    val raiseMonadError = RaiseMonadError[String]()
    val function: Raise[String] ?=> Int => Int = {
      raiseMonadError.raiseError("error")
    }
    val value: Raise[String] ?=> Int = raiseMonadError.pure(42)
    val actual: String | Int = Raise.run {
      raiseMonadError.ap(function)(value)
    }
    actual shouldBe "error"
  }

  it should "short-circuit the computation when the value raises an error" in {
    val raiseMonadError                        = RaiseMonadError[String]()
    val function: Raise[String] ?=> Int => Int = raiseMonadError.pure(_ + 1)
    val value: Raise[String] ?=> Int           = raiseMonadError.raiseError("error")
    val actual: String | Int = Raise.run {
      raiseMonadError.ap(function)(value)
    }
    actual shouldBe "error"
  }

  "tailRecM" should "chain Raise computations" in {
    val raiseMonadError = RaiseMonadError[String]()
    val actual: String | Int = Raise.run {
      raiseMonadError.tailRecM(1) { value =>
        if value < 42 then raiseMonadError.pure(Left(value + 1))
        else raiseMonadError.pure(Right(value))
      }
    }
    actual shouldBe 42
  }

  it should "short-circuit the computation when the lambda raises an error" in {
    val raiseMonadError = RaiseMonadError[String]()
    val actual: String | Int = Raise.run {
      raiseMonadError.tailRecM(1) { _ =>
        raiseMonadError.raiseError("error")
      }
    }
    actual shouldBe "error"
  }

  "handleErrorWith" should "recover from an error" in {
    val raiseMonadError = RaiseMonadError[String]()
    val queue           = new scala.collection.mutable.Queue[String]
    val actual: String | Int = Raise.run {
      raiseMonadError.handleErrorWith(
        raiseMonadError.raiseError("error")
      ) { error =>
        queue.enqueue(error)
        raiseMonadError.pure(42)
      }
    }
    actual shouldBe 42
    queue.toList shouldBe List("error")
  }

  it should "do nothing when the computation is successful" in {
    val raiseMonadError = RaiseMonadError[String]()
    val queue           = new scala.collection.mutable.Queue[String]
    val actual: String | Int = Raise.run {
      raiseMonadError.handleErrorWith(
        raiseMonadError.pure(42)
      ) { error =>
        queue.enqueue(error)
        raiseMonadError.pure(43)
      }
    }
    actual shouldBe 42
    queue.toList shouldBe List.empty
  }
}
