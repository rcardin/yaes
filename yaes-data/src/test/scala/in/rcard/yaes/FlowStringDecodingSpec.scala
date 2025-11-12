package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class FlowStringDecodingSpec extends AnyFlatSpec with Matchers {

  "asUtf8String" should "decode simple ASCII text" in {
    val data  = "Hello, World!".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be("Hello, World!")
  }

  it should "decode UTF-8 text with multi-byte characters" in {
    val text  = "Hello 世界! 😀"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "handle multi-byte character split across chunk boundaries" in {
    val text = "Hello 世界! 😀" // Contains 2-byte, 3-byte, and 4-byte UTF-8 sequences
    val data = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 5) // Small buffer to force splits
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "correctly decode when 2-byte UTF-8 character is split" in {
    // "café" where é is 2-byte UTF-8 sequence: 0xC3 0xA9
    val text  = "café"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 4) // Split right before é
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "correctly decode when 3-byte UTF-8 character is split" in {
    // "日本" - Japanese characters, each is 3 bytes in UTF-8
    val text  = "日本"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 2) // Force splits within characters
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "correctly decode when 4-byte UTF-8 character (emoji) is split" in {
    // "😀" is 4-byte UTF-8: 0xF0 0x9F 0x98 0x80
    val text  = "A😀B"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 2) // Force emoji to be split
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "handle multiple multi-byte characters split across multiple chunks" in {
    val text  = "こんにちは世界🌍🚀" // Mix of 3-byte and 4-byte characters
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 3) // Small buffer for many splits
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "handle empty input" in {
    val input = new ByteArrayInputStream(Array.empty[Byte])

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input)
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult should be(empty)
  }

  it should "handle text shorter than buffer size" in {
    val text  = "Hi"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.size should be(1)
    actualResult.head should be(text)
  }

  it should "work with other Flow operators" in {
    val text  = "line1\nline2\nline3"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 5)
      .asUtf8String()
      .take(2)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString.length should be <= text.length
  }

  it should "be composable with map operator" in {
    val text  = "hello"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 2)
      .asUtf8String()
      .map(_.toUpperCase)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be("HELLO")
  }

  it should "be composable with filter operator" in {
    val text  = "abc"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1)
      .asUtf8String()
      .filter(_.nonEmpty)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "handle only ASCII characters efficiently" in {
    val text  = "Simple ASCII text without special characters"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 10)
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "handle text with mixed 1-byte, 2-byte, 3-byte, and 4-byte characters" in {
    val text  = "A£€😀" // 1-byte, 2-byte, 3-byte, 4-byte UTF-8
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 2)
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  "asString" should "decode text with specified charset" in {
    val text  = "Hello, World!"
    val data  = text.getBytes(StandardCharsets.ISO_8859_1)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .asString(StandardCharsets.ISO_8859_1)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "decode UTF-16 text correctly" in {
    val text  = "Hello 世界!"
    val data  = text.getBytes(StandardCharsets.UTF_16)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .asString(StandardCharsets.UTF_16)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "handle UTF-16 multi-byte characters split across boundaries" in {
    val text  = "こんにちは"
    val data  = text.getBytes(StandardCharsets.UTF_16)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 5) // Force splits
      .asString(StandardCharsets.UTF_16)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "handle ASCII with ISO-8859-1 charset" in {
    val text  = "Simple text"
    val data  = text.getBytes(StandardCharsets.ISO_8859_1)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 4)
      .asString(StandardCharsets.ISO_8859_1)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "handle empty input with specified charset" in {
    val input = new ByteArrayInputStream(Array.empty[Byte])

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input)
      .asString(StandardCharsets.UTF_16)
      .collect { str =>
        actualResult += str
      }

    actualResult should be(empty)
  }

  it should "work with US_ASCII charset" in {
    val text  = "ASCII only text 123"
    val data  = text.getBytes(StandardCharsets.US_ASCII)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 5)
      .asString(StandardCharsets.US_ASCII)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "be composable with other Flow operators" in {
    val text  = "test data"
    val data  = text.getBytes(StandardCharsets.ISO_8859_1)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 3)
      .asString(StandardCharsets.ISO_8859_1)
      .map(_.toUpperCase)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be("TEST DATA")
  }

  it should "handle large text with specified charset" in {
    val text  = "A" * 10000
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .asString(StandardCharsets.UTF_8)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "handle character boundaries correctly with UTF-8 when using asString" in {
    val text  = "日本語テキスト" // Japanese text
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 2) // Force splits
      .asString(StandardCharsets.UTF_8)
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "work with fold to concatenate all strings" in {
    val text  = "Hello World"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val result = Flow
      .fromInputStream(input, bufferSize = 3)
      .asString(StandardCharsets.UTF_8)
      .fold("") { (acc, str) =>
        acc + str
      }

    result should be(text)
  }

  it should "handle realistic JSON parsing scenario" in {
    val json  = """{"name":"John","age":30,"city":"New York"}"""
    val data  = json.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 10) // Simulate network chunks
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(json)
  }

  it should "handle realistic XML parsing scenario" in {
    val xml   = """<?xml version="1.0"?><root><item>Test</item></root>"""
    val data  = xml.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 15) // Simulate network chunks
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(xml)
  }

  it should "handle text with newlines and special characters" in {
    val text  = "Line 1\nLine 2\r\nLine 3\tTabbed"
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 7)
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }

  it should "handle consecutive multi-byte characters at chunk boundaries" in {
    val text  = "😀😀😀😀😀" // Multiple 4-byte emojis
    val data  = text.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 3) // Force many splits
      .asUtf8String()
      .collect { str =>
        actualResult += str
      }

    actualResult.mkString should be(text)
  }
}
