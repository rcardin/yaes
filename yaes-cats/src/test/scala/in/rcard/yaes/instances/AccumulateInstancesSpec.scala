package in.rcard.yaes.instances

import cats.data.{NonEmptyChain, NonEmptyList}
import cats.implicits.*
import in.rcard.yaes.Raise
import in.rcard.yaes.Raise.accumulating
import in.rcard.yaes.instances.accumulate.given
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AccumulateInstancesSpec extends AnyFlatSpec with Matchers {

  "Polymorphic accumulate with NonEmptyList" should "create a Right instance when no errors occur" in {
    val result: Either[NonEmptyList[String], Int] = Raise.either {
      Raise.accumulate[NonEmptyList, String, Int] {
        1
      }
    }

    result should be(Right(1))
  }

  it should "create a Left with NonEmptyList when one error occurs" in {
    val result: Either[NonEmptyList[String], Int] = Raise.either {
      Raise.accumulate[NonEmptyList, String, Int] {
        val a = accumulating { Raise.raise("error1") }
        a
      }
    }

    result should be(Left(NonEmptyList.one("error1")))
  }

  it should "accumulate multiple errors in NonEmptyList" in {
    val result: Either[NonEmptyList[String], List[Int]] = Raise.either {
      Raise.accumulate[NonEmptyList, String, List[Int]] {
        val a = accumulating { Raise.raise("error1") }
        val b = accumulating { Raise.raise("error2") }
        List(a, b)
      }
    }

    result.left.map(_.toList.toSet) should be(Left(Set("error1", "error2")))
  }

  it should "work correctly even when all values raise errors" in {
    // This test demonstrates polymorphic accumulate with NonEmptyList
    val result: Either[NonEmptyList[String], List[Int]] = Raise.either {
      Raise.accumulate[NonEmptyList, String, List[Int]] {
        val a = accumulating { Raise.raise("error1") }
        val b = accumulating { Raise.raise("error2") }
        List(a, b)
      }
    }

    result.left.map(_.toList.toSet) should be(Left(Set("error1", "error2")))
  }

  "Polymorphic accumulate with NonEmptyChain" should "create a Right instance when no errors occur" in {
    val result: Either[NonEmptyChain[String], Int] = Raise.either {
      Raise.accumulate[NonEmptyChain, String, Int] {
        1
      }
    }

    result should be(Right(1))
  }

  it should "create a Left with NonEmptyChain when one error occurs" in {
    val result: Either[NonEmptyChain[String], Int] = Raise.either {
      Raise.accumulate[NonEmptyChain, String, Int] {
        val a = accumulating { Raise.raise("error1") }
        a
      }
    }

    result should be(Left(NonEmptyChain.one("error1")))
  }

  it should "accumulate multiple errors in NonEmptyChain" in {
    val result: Either[NonEmptyChain[String], List[Int]] = Raise.either {
      Raise.accumulate[NonEmptyChain, String, List[Int]] {
        val a = accumulating { Raise.raise("error1") }
        val b = accumulating { Raise.raise("error2") }
        List(a, b)
      }
    }

    result.left.map(_.toList.toSet) should be(Left(Set("error1", "error2")))
  }

  it should "accumulate errors from multiple sources" in {
    val result: Either[NonEmptyChain[String], List[Int]] = Raise.either {
      Raise.accumulate[NonEmptyChain, String, List[Int]] {
        val a = accumulating { Raise.raise("error2") }
        val b = accumulating { Raise.raise("error4") }
        List(a, b)
      }
    }

    result.left.map(_.toList.toSet) should be(Left(Set("error2", "error4")))
  }
}
