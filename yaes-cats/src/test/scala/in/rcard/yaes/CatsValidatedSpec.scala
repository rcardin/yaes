package in.rcard.yaes

import _root_.cats.data.*
import _root_.cats.data.Validated.{Invalid, Valid}
import in.rcard.yaes.cats.validated
import in.rcard.yaes.syntax.validated.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ValidatedSpec extends AnyFlatSpec with Matchers {

  "The 'validated' builder" should "create a Valid instance" in {
    val result = validated.validated {
      42
    }

    result should be(Validated.valid(42))
  }

  it should "create an Invalid instance" in {
    val result = validated.validated {
      Raise.raise("error")
    }

    result should be(Validated.invalid("error"))
  }

  "The 'validatedNec' builder" should "create a Valid instance" in {
    val result = validated.validatedNec {
      42
    }

    result should be(Validated.valid(42))
  }

  it should "create an Invalid instance" in {
    val result = validated.validatedNec {
      Raise.raise("error")
    }

    result should be(Validated.invalid(NonEmptyChain.one("error")))
  }

  "The 'validatedNel' builder" should "create a Valid instance" in {
    val result = validated.validatedNel {
      42
    }

    result should be(Validated.valid(42))
  }

  it should "create an Invalid instance" in {
    val result = validated.validatedNel {
      Raise.raise("error")
    }

    result should be(Validated.invalid(NonEmptyList.one("error")))
  }

  "Validated.value extension" should "return the value if it's Valid" in {
    val one: Validated[String, Int] = Validated.Valid(1)

    val actual: Int = Raise.recover(one.value) { _ => 2 }

    actual should be(1)
  }

  it should "raise an error if it's Invalid" in {
    val invalid: Validated[String, Int] = Validated.Invalid("error")

    val actual: Int = Raise.recover(invalid.value) { err =>
      if (err == "error") 2 else 3
    }

    actual should be(2)
  }

  it should "work with invalid ValidatedNel" in {
    val invalid: ValidatedNel[String, Int] = Validated.invalid(NonEmptyList.one("error"))

    val actual: Int = Raise.recover(invalid.value) { err =>
      if (err.head == "error") 2 else 3
    }

    actual should be(2)
  }

  it should "work with invalid ValidatedNec" in {
    val invalid: ValidatedNec[String, Int] = Validated.invalid(NonEmptyChain.one("error"))

    val actual: Int = Raise.recover(invalid.value) { err =>
      if (err.head == "error") 2 else 3
    }

    actual should be(2)
  }
}
