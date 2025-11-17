package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets

class FlowStringEncodingSpec extends AnyFlatSpec with Matchers {

  "encodeToUtf8" should "encode simple ASCII text" in {
    val text = "Hello, World!"
    val flow = Flow(text)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeToUtf8()
      .collect { bytes =>
        actualResult += bytes
      }

    val encoded = actualResult.flatten.toArray
    new String(encoded, StandardCharsets.UTF_8) should be(text)
  }

  it should "encode UTF-8 text with multi-byte characters" in {
    val text = "Hello 世界! 😀"
    val flow = Flow(text)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeToUtf8()
      .collect { bytes =>
        actualResult += bytes
      }

    val encoded = actualResult.flatten.toArray
    new String(encoded, StandardCharsets.UTF_8) should be(text)
  }

  it should "encode multiple strings separately" in {
    val strings = Seq("Hello", " ", "世界", "! ", "😀")
    val flow    = Flow(strings*)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeToUtf8()
      .collect { bytes =>
        actualResult += bytes
      }

    actualResult should have size strings.length
    val decoded = actualResult.map(bytes => new String(bytes, StandardCharsets.UTF_8))
    decoded should contain theSameElementsInOrderAs strings
  }

  it should "encode empty string" in {
    val text = ""
    val flow = Flow(text)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeToUtf8()
      .collect { bytes =>
        actualResult += bytes
      }

    actualResult should have size 1
    actualResult.head should be(Array.empty[Byte])
  }

  it should "encode strings with various UTF-8 character lengths" in {
    val flow = Flow(
      "a",
      "é",
      "世",
      "😀"
    )

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeToUtf8()
      .collect { bytes =>
        actualResult += bytes
      }

    actualResult(0) should have length 1
    actualResult(1) should have length 2
    actualResult(2) should have length 3
    actualResult(3) should have length 4
  }

  it should "encode text containing line breaks and special characters" in {
    val text = "Line 1\nLine 2\tTabbed\rCarriage Return"
    val flow = Flow(text)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeToUtf8()
      .collect { bytes =>
        actualResult += bytes
      }

    val encoded = actualResult.flatten.toArray
    new String(encoded, StandardCharsets.UTF_8) should be(text)
  }

  "encodeTo" should "encode text using UTF-8 charset" in {
    val text = "Hello 世界! 😀"
    val flow = Flow(text)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeTo(StandardCharsets.UTF_8)
      .collect { bytes =>
        actualResult += bytes
      }

    val encoded = actualResult.flatten.toArray
    new String(encoded, StandardCharsets.UTF_8) should be(text)
  }

  it should "encode text using ISO-8859-1 charset" in {
    val text = "café"
    val flow = Flow(text)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeTo(StandardCharsets.ISO_8859_1)
      .collect { bytes =>
        actualResult += bytes
      }

    val encoded = actualResult.flatten.toArray
    new String(encoded, StandardCharsets.ISO_8859_1) should be(text)
  }

  it should "encode text using UTF-16 charset" in {
    val text = "Hello 世界"
    val flow = Flow(text)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeTo(StandardCharsets.UTF_16)
      .collect { bytes =>
        actualResult += bytes
      }

    val encoded = actualResult.flatten.toArray
    new String(encoded, StandardCharsets.UTF_16) should be(text)
  }

  it should "encode text using US-ASCII charset" in {
    val text = "Hello World"
    val flow = Flow(text)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeTo(StandardCharsets.US_ASCII)
      .collect { bytes =>
        actualResult += bytes
      }

    val encoded = actualResult.flatten.toArray
    new String(encoded, StandardCharsets.US_ASCII) should be(text)
  }

  it should "throw exception when encoding unmappable character" in {
    val text = "世界"
    val flow = Flow(text)

    an[java.nio.charset.UnmappableCharacterException] should be thrownBy {
      flow
        .encodeTo(StandardCharsets.US_ASCII)
        .collect { _ => }
    }
  }

  it should "encode multiple strings with different charsets correctly" in {
    val strings = Seq("Hello", "World", "!")
    val flow    = Flow(strings*)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeTo(StandardCharsets.UTF_16)
      .collect { bytes =>
        actualResult += bytes
      }

    actualResult should have size strings.length
    val decoded = actualResult.map(bytes => new String(bytes, StandardCharsets.UTF_16))
    decoded should contain theSameElementsInOrderAs strings
  }

  it should "encode empty string with any charset" in {
    val text = ""
    val flow = Flow(text)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeTo(StandardCharsets.UTF_16)
      .collect { bytes =>
        actualResult += bytes
      }

    actualResult should have size 1
    val decoded = new String(actualResult.head, StandardCharsets.UTF_16)
    decoded should be("")
  }

  it should "encode empty string with UTF-16 charset without BufferOverflowException" in {
    val text = ""
    val flow = Flow(text)

    noException should be thrownBy {
      flow
        .encodeTo(StandardCharsets.UTF_16)
        .collect { _ => }
    }

    val result = flow
      .encodeTo(StandardCharsets.UTF_16)
      .fold(Array.empty[Byte])(_ ++ _)

    val decoded = new String(result, StandardCharsets.UTF_16)
    decoded should be("")
  }

  it should "handle round-trip encoding and decoding" in {
    val originalText = "Hello 世界! 😀 with special chars: \n\t\r"
    val flow         = Flow(originalText)

    val encoded = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeToUtf8()
      .collect { bytes =>
        encoded += bytes
      }

    val byteFlow = Flow(encoded.toSeq*)
    val decoded  = scala.collection.mutable.ArrayBuffer[String]()
    byteFlow
      .asUtf8String()
      .collect { str =>
        decoded += str
      }

    decoded.mkString should be(originalText)
  }

  it should "handle round-trip with chunked strings" in {
    val strings = Seq("Hello", " ", "世界", "! ", "😀")
    val flow    = Flow(strings*)

    val encoded = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .encodeToUtf8()
      .collect { bytes =>
        encoded += bytes
      }

    val concatenatedBytes = encoded.flatten.toArray
    val byteFlow          = Flow(concatenatedBytes)
    val decoded           = byteFlow.asUtf8String().fold("")(_ + _)

    decoded should be(strings.mkString)
  }

  "encodeToUtf8 and encodeTo" should "work with flow operations" in {
    val flow = Flow("hello", "world", "test")

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    flow
      .map(_.toUpperCase)
      .filter(_.length > 4)
      .encodeToUtf8()
      .collect { bytes =>
        actualResult += bytes
      }

    actualResult should have size 2
    val decoded = actualResult.map(bytes => new String(bytes, StandardCharsets.UTF_8))
    decoded should contain theSameElementsInOrderAs Seq("HELLO", "WORLD")
  }
}
