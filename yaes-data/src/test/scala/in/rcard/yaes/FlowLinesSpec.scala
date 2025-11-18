package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class FlowLinesSpec extends AnyFlatSpec with Matchers {

  "linesInUtf8" should "split simple single line with LF" in {
    val data  = "Hello\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Hello")
  }

  it should "split simple single line with CRLF" in {
    val data  = "Hello\r\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Hello")
  }

  it should "split simple single line with CR" in {
    val data  = "Hello\r".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Hello")
  }

  it should "split multiple lines with LF" in {
    val data  = "Line1\nLine2\nLine3\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Line1", "Line2", "Line3")
  }

  it should "split multiple lines with CRLF" in {
    val data  = "Line1\r\nLine2\r\nLine3\r\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Line1", "Line2", "Line3")
  }

  it should "split multiple lines with CR" in {
    val data  = "Line1\rLine2\rLine3\r".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Line1", "Line2", "Line3")
  }

  it should "split lines with mixed line separators" in {
    val data  = "Line1\nLine2\r\nLine3\rLine4\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Line1", "Line2", "Line3", "Line4")
  }

  it should "emit last line without trailing separator" in {
    val data  = "Line1\nLine2".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Line1", "Line2")
  }

  it should "preserve empty lines" in {
    val data  = "Line1\n\nLine3\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Line1", "", "Line3")
  }

  it should "handle only empty lines" in {
    val data  = "\n\n\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("", "", "")
  }

  it should "handle empty string" in {
    val data  = "".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult shouldBe empty
  }

  it should "handle only line separator" in {
    val data  = "\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("")
  }

  it should "handle multiple consecutive CRLF" in {
    val data  = "Line1\r\n\r\nLine3\r\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Line1", "", "Line3")
  }

  it should "handle UTF-8 multi-byte characters within lines" in {
    val data  = "Hello 世界\nCafé ☕\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Hello 世界", "Café ☕")
  }

  it should "handle UTF-8 emoji in lines" in {
    val data  = "Line1 😀\nLine2 🎉\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Line1 😀", "Line2 🎉")
  }

  it should "handle multi-byte character split across chunks before newline" in {
    val data  = "Hello 世界\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 10)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Hello 世界")
  }

  it should "handle multi-byte characters spanning multiple lines with small buffer" in {
    val data  = "日本\n語\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 3)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("日本", "語")
  }

  it should "handle CRLF split across chunks" in {
    val data  = "Line1\r\nLine2\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 6)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Line1", "Line2")
  }

  it should "handle multiple lines with separators split" in {
    val data  = "A\r\nB\r\nC".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 3)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("A", "B", "C")
  }

  it should "handle long lines exceeding buffer size" in {
    val longLine = "a" * 10000
    val data     = s"$longLine\n".getBytes(StandardCharsets.UTF_8)
    val input    = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should have size 1
    actualResult.head should have length 10000
    actualResult.head shouldBe longLine
  }

  it should "handle many short lines" in {
    val lines = (1 to 1000).map(i => s"test$i").mkString("\n") + "\n"
    val data  = lines.getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .collect { line =>
        actualResult += line
      }

    actualResult should have size 1000
    actualResult.head shouldBe "test1"
    actualResult.last shouldBe "test1000"
  }

  "linesIn" should "decode ISO-8859-1 encoding" in {
    val data  = "café\nlatte\n".getBytes(StandardCharsets.ISO_8859_1)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesIn(StandardCharsets.ISO_8859_1)
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("café", "latte")
  }

  it should "decode UTF-16 with BOM" in {
    val data  = "Hello\nWorld\n".getBytes(StandardCharsets.UTF_16)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesIn(StandardCharsets.UTF_16)
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("Hello", "World")
  }

  it should "decode UTF-16 with multi-byte characters" in {
    val data  = "世界\n日本\n".getBytes(StandardCharsets.UTF_16)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesIn(StandardCharsets.UTF_16)
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("世界", "日本")
  }

  it should "throw exception on malformed UTF-8 sequence" in {
    val data  = Array[Byte](0x48, 0x65, 0x6C, 0x6C, 0x6F, 0xFF.toByte, 0x0A)
    val input = new ByteArrayInputStream(data)

    an[java.nio.charset.MalformedInputException] should be thrownBy {
      Flow
        .fromInputStream(input, bufferSize = 1024)
        .linesInUtf8()
        .collect { line =>
          // Should throw before completing
        }
    }
  }

  it should "chain with map to convert strings to integers" in {
    val data  = "1\n2\n3\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val sum = Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .map(_.toInt)
      .fold(0)(_ + _)

    sum shouldBe 6
  }

  it should "chain with filter" in {
    val data  = "keep\nskip\nkeep\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .filter(_ == "keep")
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("keep", "keep")
  }

  it should "chain with take" in {
    val data  = "1\n2\n3\n4\n5\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .take(3)
      .collect { line =>
        actualResult += line
      }

    actualResult should contain theSameElementsInOrderAs Seq("1", "2", "3")
  }

  it should "count lines correctly" in {
    val data  = "Line1\nLine2\nLine3\nLine4\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val count = Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .count()

    count shouldBe 4
  }

  it should "work with zipWithIndex" in {
    val data  = "A\nB\nC\n".getBytes(StandardCharsets.UTF_8)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[(String, Long)]()
    Flow
      .fromInputStream(input, bufferSize = 1024)
      .linesInUtf8()
      .zipWithIndex()
      .collect { pair =>
        actualResult += pair
      }

    actualResult should contain theSameElementsInOrderAs Seq(("A", 0L), ("B", 1L), ("C", 2L))
  }

}
