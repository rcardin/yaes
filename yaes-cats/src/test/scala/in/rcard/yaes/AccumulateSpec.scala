package in.rcard.yaes

import cats.Semigroup
import cats.data.NonEmptyList
import in.rcard.yaes.CatsAccumulate.combineErrorsS
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AccumulateSpec extends AnyFlatSpec with Matchers {
  case class MyError2(errors: List[String])

  "mapOrAccumulateS on Iterable with Semigroup[Error]" should "map all the elements of the iterable" in {
    given Semigroup[MyError2] with {
      def combine(error1: MyError2, error2: MyError2): MyError2 =
        MyError2(error1.errors ++ error2.errors)
    }
    val block: List[Int] raises MyError2 =
      CatsAccumulate.mapOrAccumulateS(List(1, 2, 3, 4, 5)) { value1 =>
        value1 + 1
      }

    val actual = Raise.fold[MyError2, List[Int], List[Int]](block) { error =>
      fail(s"An error occurred: $error")
    } { identity }

    actual shouldBe List(2, 3, 4, 5, 6)
  }

  it should "accumulate all the errors using the combine function" in {
    given Semigroup[MyError2] with {
      def combine(error1: MyError2, error2: MyError2): MyError2 =
        MyError2(error1.errors ++ error2.errors)
    }

    val block: List[Int] raises MyError2 =
      CatsAccumulate.mapOrAccumulateS(List(1, 2, 3, 4, 5)) { value =>
        if (value % 2 == 0) {
          Raise.raise(MyError2(List(value.toString)))
        } else {
          value
        }
      }

    val actual = Raise.fold[MyError2, List[Int], MyError2 | List[Int]](block)(identity)(identity)

    actual shouldBe MyError2(List("2", "4"))
  }

  "mapOrAccumulateS on NonEmptyList with Semigroup[Error]" should "map all the elements of the NonEmptyList" in {
    given Semigroup[MyError2] with {
      def combine(error1: MyError2, error2: MyError2): MyError2 =
        MyError2(error1.errors ++ error2.errors)
    }

    val block: NonEmptyList[Int] raises MyError2 =
      CatsAccumulate.mapOrAccumulateS(NonEmptyList.of(1, 2, 3, 4, 5)) { value1 =>
        value1 + 1
      }

    val actual: NonEmptyList[Int] = Raise.fold[MyError2, NonEmptyList[Int], NonEmptyList[Int]](block) { (error: MyError2) =>
      fail(s"An error occurred: $error")
    } { identity }

    actual shouldBe NonEmptyList.of(2, 3, 4, 5, 6)
  }

  it should "accumulate all the errors using the combine function" in {
    given Semigroup[MyError2] with {
      def combine(error1: MyError2, error2: MyError2): MyError2 =
        MyError2(error1.errors ++ error2.errors)
    }

    val block: NonEmptyList[Int] raises MyError2 =
      CatsAccumulate.mapOrAccumulateS(NonEmptyList.of(1, 2, 3, 4, 5)) { value =>
        if (value % 2 == 0) {
          Raise.raise(MyError2(List(value.toString)))
        } else {
          value
        }
      }

    val actual = Raise.fold[MyError2, NonEmptyList[Int], MyError2 | NonEmptyList[Int]](block)(identity)(identity)

    actual shouldBe MyError2(List("2", "4"))
  }

  "combineErrorsS on Iterable" should "map all the elements of the iterable" in {
    given Semigroup[String] = Semigroup.instance(_ + _)

    val iterableWithInnerRaise: List[Int raises String] =
      List(1, 2, 3, 4, 5).map { value1 =>
        value1 + 1
      }

    val iterableWithOuterRaise: List[Int] raises String = iterableWithInnerRaise.combineErrorsS

    val actual: List[Int] = Raise.fold[String, List[Int], List[Int]](iterableWithOuterRaise) { (error: String) =>
      fail(s"An error occurred: $error")
    } { identity }

    actual shouldBe List(2, 3, 4, 5, 6)
  }

  it should "accumulate all the errors using the combine function" in {
    given Semigroup[String] = Semigroup.instance(_ + _)

    val iterableWithInnerRaise: List[Int raises String] =
      List(1, 2, 3, 4, 5).map { value =>
        if (value % 2 == 0) {
          Raise.raise(value.toString)
        } else {
          value
        }
      }

    val iterableWithOuterRaise: List[Int] raises String = iterableWithInnerRaise.combineErrorsS

    val actual = Raise.fold[String, List[Int], String | List[Int]](iterableWithOuterRaise)(identity)(identity)

    actual shouldBe "24"
  }

  "combineErrorsS on NonEmptyList" should "map all the elements of the NonEmptyList" in {
    given Semigroup[String] = Semigroup.instance(_ + _)

    val iterableWithInnerRaise: NonEmptyList[Int raises String] =
      NonEmptyList.of(1, 2, 3, 4, 5).map { value1 =>
        value1 + 1
      }

    val iterableWithOuterRaise: NonEmptyList[Int] raises String = iterableWithInnerRaise.combineErrorsS

    val actual: NonEmptyList[Int] = Raise.fold[String, NonEmptyList[Int], NonEmptyList[Int]](iterableWithOuterRaise) { (error: String) =>
      fail(s"An error occurred: $error")
    } { identity }

    actual shouldBe NonEmptyList.of(2, 3, 4, 5, 6)
  }

  it should "accumulate all the errors using the combine function" in {
    given Semigroup[String] = Semigroup.instance(_ + _)

    val iterableWithInnerRaise: NonEmptyList[Int raises String] =
      NonEmptyList.of(1, 2, 3, 4, 5).map { value =>
        if (value % 2 == 0) {
          Raise.raise(value.toString)
        } else {
          value
        }
      }

    val iterableWithOuterRaise: NonEmptyList[Int] raises String = iterableWithInnerRaise.combineErrorsS

    val actual = Raise.fold[String, NonEmptyList[Int], String | NonEmptyList[Int]](iterableWithOuterRaise)(identity)(identity)

    actual shouldBe "24"
  }
}
