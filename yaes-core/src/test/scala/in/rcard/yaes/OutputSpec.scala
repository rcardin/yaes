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
}
