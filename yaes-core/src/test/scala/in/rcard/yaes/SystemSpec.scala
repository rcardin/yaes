package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SystemSpec extends AnyFlatSpec with Matchers {
  "The System effect" should "be able to read an environment variable" in {
    val actualResult: Option[String] = Raise.run {
      System.run {
        System.env("PATH")
      }
    }
    actualResult should not be empty
  }

  it should "return None for a non-existent environment variable" in {
    val actualResult: Option[String] = Raise.run {
      System.run {
        System.env("NON_EXISTENT_VAR")
      }
    }
    actualResult shouldBe empty
  }

  it should "return a default value for a non-existent environment variable" in {
    val actualResult: String = Raise.run {
      System.run {
        System.env("NON_EXISTENT_VAR", "default")
      }
    }
    actualResult shouldBe "default"
  }
}
