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
      case maybePropertyValue: Option[Int] => maybePropertyValue.value shouldBe 42
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
      case _                        => fail("Expected a NumberFormatException, but got a valid Int")
  }

  it should "read a boolean from an environment variable" in {
    JSystem.setProperty("test.boolean", "true")
    val actualResult: Option[Boolean] | IllegalArgumentException = Raise.run {
      System.run {
        System.property[Boolean]("test.boolean")
      }
    }
    actualResult match
      case _: IllegalArgumentException =>
        fail("Expected a Boolean, but got a IllegalArgumentException")
      case maybePropertyValue: Option[Boolean] => maybePropertyValue.value shouldBe true
  }

  it should "raise an IllegalArgumentException for an invalid boolean" in {
    JSystem.setProperty("test.boolean", "invalid")
    val actualResult: Option[Boolean] | IllegalArgumentException = Raise.run {
      System.run {
        System.property[Boolean]("test.boolean")
      }
    }
    actualResult match
      case _: IllegalArgumentException => succeed
      case _ => fail("Expected an IllegalArgumentException, but got a valid Boolean")
  }

  it should "read a long from an environment variable" in {
    JSystem.setProperty("test.long", "1234567890123456789")
    val actualResult: Option[Long] | NumberFormatException = Raise.run {
      System.run {
        System.property[Long]("test.long")
      }
    }
    actualResult match
      case _: NumberFormatException => fail("Expected a Long, but got a NumberFormatException")
      case maybePropertyValue: Option[Long] =>
        maybePropertyValue.value shouldBe 1234567890123456789L
  }

  it should "raise a NumberFormatException for an invalid long" in {
    JSystem.setProperty("test.long", "invalid")
    val actualResult: Option[Long] | NumberFormatException = Raise.run {
      System.run {
        System.property[Long]("test.long")
      }
    }
    actualResult match
      case _: NumberFormatException => succeed
      case _ => fail("Expected a NumberFormatException, but got a valid Long")
  }

  it should "read a double from an environment variable" in {
    JSystem.setProperty("test.double", "123.456")
    val actualResult: Option[Double] | NumberFormatException = Raise.run {
      System.run {
        System.property[Double]("test.double")
      }
    }
    actualResult match
      case _: NumberFormatException => fail("Expected a Double, but got a NumberFormatException")
      case maybePropertyValue: Option[Double] =>
        maybePropertyValue.value shouldBe 123.456
  }

  it should "raise a NumberFormatException for an invalid double" in {
    JSystem.setProperty("test.double", "invalid")
    val actualResult: Option[Double] | NumberFormatException = Raise.run {
      System.run {
        System.property[Double]("test.double")
      }
    }
    actualResult match
      case _: NumberFormatException => succeed
      case _ => fail("Expected a NumberFormatException, but got a valid Double")
  }

  it should "read a float from an environment variable" in {
    JSystem.setProperty("test.float", "123.456")
    val actualResult: Option[Float] | NumberFormatException = Raise.run {
      System.run {
        System.property[Float]("test.float")
      }
    }
    actualResult match
      case _: NumberFormatException => fail("Expected a Float, but got a NumberFormatException")
      case maybePropertyValue: Option[Float] =>
        maybePropertyValue.value shouldBe 123.456f
  }

  it should "raise a NumberFormatException for an invalid float" in {
    JSystem.setProperty("test.float", "invalid")
    val actualResult: Option[Float] | NumberFormatException = Raise.run {
      System.run {
        System.property[Float]("test.float")
      }
    }
    actualResult match
      case _: NumberFormatException => succeed
      case _ => fail("Expected a NumberFormatException, but got a valid Float")
  }

  it should "read a short from an environment variable" in {
    JSystem.setProperty("test.short", "12345")
    val actualResult: Option[Short] | NumberFormatException = Raise.run {
      System.run {
        System.property[Short]("test.short")
      }
    }
    actualResult match
      case _: NumberFormatException => fail("Expected a Short, but got a NumberFormatException")
      case maybePropertyValue: Option[Short] =>
        maybePropertyValue.value shouldBe 12345.toShort
  }

  it should "raise a NumberFormatException for an invalid short" in {
    JSystem.setProperty("test.short", "invalid")
    val actualResult: Option[Short] | NumberFormatException = Raise.run {
      System.run {
        System.property[Short]("test.short")
      }
    }
    actualResult match
      case _: NumberFormatException => succeed
      case _ => fail("Expected a NumberFormatException, but got a valid Short")
  }

  it should "read a byte from an environment variable" in {
    JSystem.setProperty("test.byte", "123")
    val actualResult: Option[Byte] | NumberFormatException = Raise.run {
      System.run {
        System.property[Byte]("test.byte")
      }
    }
    actualResult match
      case _: NumberFormatException => fail("Expected a Byte, but got a NumberFormatException")
      case maybePropertyValue: Option[Byte] =>
        maybePropertyValue.value shouldBe 123.toByte
  }

  it should "raise a NumberFormatException for an invalid byte" in {
    JSystem.setProperty("test.byte", "invalid")
    val actualResult: Option[Byte] | NumberFormatException = Raise.run {
      System.run {
        System.property[Byte]("test.byte")
      }
    }
    actualResult match
      case _: NumberFormatException => succeed
      case _ => fail("Expected a NumberFormatException, but got a valid Byte")
  }
}
