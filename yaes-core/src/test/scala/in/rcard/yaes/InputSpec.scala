package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.ByteArrayInputStream
import java.io.Reader
import java.io.BufferedReader
import java.io.StringReader
import java.io.IOException

class InputSpec extends AnyFlatSpec with Matchers {

  "The Input effect" should "read a line from the console" in {
    val in = new ByteArrayInputStream(("42").getBytes)
    Console.withIn(in) {
      val actualResult = Raise.run {
        Input.run {
          Input.readLn()
        }
      }

      actualResult should be("42")
    }
  }

  it should "raise an exception if the input stream is closed" in {
    val in = new BufferedReader(new StringReader("42"))
    in.close()
    Console.withIn(in) {
      val actualResult = Raise.run {
        Input.run {
          Input.readLn()
        }
      }

      actualResult shouldBe a[IOException]
    }
  }
}
