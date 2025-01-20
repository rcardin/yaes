package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OutputSpec extends AnyFlatSpec with Matchers {
  "Output" should "printLn the text" in {
    val actualResult = new java.io.ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Output.run {
        Output.printLn("Hello, World!")
      }
    }

    actualResult.toString() should be("Hello, World!\n")
  }

  it should "print the text" in {
    val actualResult = new java.io.ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Output.run {
        Output.print("Hello, World!")
      }
    }

    actualResult.toString() should be("Hello, World!")
  }

  it should "printLn the text on the error stream" in {
    val actualResult = new java.io.ByteArrayOutputStream()
    Console.withErr(actualResult) {
      Output.run {
        Output.printErrLn("Hello, World!")
      }
    }

    actualResult.toString() should be("Hello, World!\n")
  }

  it should "print the text on the error stream" in {
    val actualResult = new java.io.ByteArrayOutputStream()
    Console.withErr(actualResult) {
      Output.run {
        Output.printErr("Hello, World!")
      }
    }

    actualResult.toString() should be("Hello, World!")
  }
}
