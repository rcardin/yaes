package in.rcard.yaes

import in.rcard.yaes.System.Parser
import in.rcard.yaes.System.System

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.lang.System as JSystem
import org.scalatest.OptionValues

class SystemSpec extends AnyFlatSpec with OptionValues with Matchers {
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

  it should "be able to read a system property" in {
    JSystem.setProperty("test.property", "testValue")
    val actualResult: Option[String] = Raise.run {
      System.run {
        System.property("test.property")
      }
    }
    actualResult.value should be("testValue")
  }

  it should "return None for a non-existent system property" in {
    val actualResult: Option[String] = Raise.run {
      System.run {
        System.property("NON_EXISTENT_PROPERTY")
      }
    }
    actualResult shouldBe empty
  }

  it should "return a default value for a non-existent system property" in {
    val actualResult: String = Raise.run {
      System.run {
        System.property("NON_EXISTENT_PROPERTY", "default")
      }
    }
    actualResult shouldBe "default"
  }

  "The parser" should "read an int from an environment variable" in {
    JSystem.setProperty("test.int", "42")
    val actualResult: Option[Int] | NumberFormatException = Raise.run {
      System.run {
        System.property[Int]("test.int")
      }
    }
    actualResult match
        case _: NumberFormatException => fail("Expected an Int, but got a NumberFormatException")
        case maybePropertyValue : Option[Int] => maybePropertyValue.value shouldBe 42
  }

  it should "raise a NumberFormatException an invalid int" in {
    JSystem.setProperty("test.int", "invalid")
    val actualResult: Option[Int] | NumberFormatException = Raise.run {
      System.run {
        System.property[Int]("test.int")
      }
    }
    actualResult match
        case _: NumberFormatException => succeed
        case _ => fail("Expected a NumberFormatException, but got a valid Int")
  }
}
