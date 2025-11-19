package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayOutputStream, IOException, OutputStream}

class FlowToOutputStreamSpec extends AnyFlatSpec with Matchers {

  "toOutputStream" should "write byte arrays to an OutputStream" in {
    val data   = "Hello, World!".getBytes()
    val flow   = Flow(data)
    val output = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    output.toByteArray should be(data)
  }

  it should "write multiple chunks in order" in {
    val chunk1 = "Hello, ".getBytes()
    val chunk2 = "World".getBytes()
    val chunk3 = "!".getBytes()
    val flow   = Flow(chunk1, chunk2, chunk3)
    val output = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    val expected = (chunk1 ++ chunk2 ++ chunk3)
    output.toByteArray should be(expected)
  }

  it should "handle empty flow" in {
    val flow   = Flow[Array[Byte]]()
    val output = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    output.toByteArray should be(Array.empty[Byte])
  }

  it should "skip empty byte arrays" in {
    val chunk1 = "Hello".getBytes()
    val empty  = Array.empty[Byte]
    val chunk2 = "World".getBytes()
    val flow   = Flow(chunk1, empty, chunk2, empty)
    val output = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    val expected = (chunk1 ++ chunk2)
    output.toByteArray should be(expected)
  }

  it should "write a single large chunk" in {
    val data   = ("A" * 10000).getBytes()
    val flow   = Flow(data)
    val output = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    output.toByteArray should be(data)
  }

  it should "handle binary data correctly" in {
    val data: Array[Byte] = Array(0, 1, 2, 3, 4, 5, 255.toByte, 254.toByte, 253.toByte)
    val flow              = Flow(data)
    val output            = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    output.toByteArray should be(data)
  }

  it should "flush the stream at the end" in {
    class FlushTrackingOutputStream extends ByteArrayOutputStream {
      var flushCount = 0

      override def flush(): Unit = {
        flushCount += 1
        super.flush()
      }
    }

    val chunk1 = "Hello".getBytes()
    val chunk2 = "World".getBytes()
    val flow   = Flow(chunk1, chunk2)
    val output = new FlushTrackingOutputStream()

    flow.toOutputStream(output)

    output.flushCount should be(1)
    output.toByteArray should be(chunk1 ++ chunk2)
  }

  it should "propagate IOException during write" in {
    class FailingOutputStream extends OutputStream {
      override def write(b: Int): Unit = throw new IOException("Write failed")
      override def write(b: Array[Byte]): Unit = throw new IOException("Write failed")
    }

    val data   = "Hello".getBytes()
    val flow   = Flow(data)
    val output = new FailingOutputStream()

    an[IOException] should be thrownBy {
      flow.toOutputStream(output)
    }
  }

  it should "propagate IOException during flush" in {
    class FlushFailingOutputStream extends ByteArrayOutputStream {
      override def flush(): Unit = throw new IOException("Flush failed")
    }

    val data   = "Hello".getBytes()
    val flow   = Flow(data)
    val output = new FlushFailingOutputStream()

    an[IOException] should be thrownBy {
      flow.toOutputStream(output)
    }
  }

  it should "not close the stream" in {
    class CloseTrackingOutputStream extends ByteArrayOutputStream {
      var closed = false

      override def close(): Unit = {
        closed = true
        super.close()
      }
    }

    val data   = "Hello".getBytes()
    val flow   = Flow(data)
    val output = new CloseTrackingOutputStream()

    flow.toOutputStream(output)

    output.closed should be(false)
    output.toByteArray should be(data)
  }

  it should "allow writing to the stream after toOutputStream completes" in {
    val data   = "Hello".getBytes()
    val flow   = Flow(data)
    val output = new ByteArrayOutputStream()

    flow.toOutputStream(output)
    output.write(" World".getBytes())

    output.toByteArray should be("Hello World".getBytes())
  }

  it should "work in a round-trip scenario with fromInputStream" in {
    val originalData = "Hello, World! 世界 😀".getBytes("UTF-8")
    val flow         = Flow(originalData)
    val output       = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    val input      = new java.io.ByteArrayInputStream(output.toByteArray)
    val readResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input).collect { chunk =>
      readResult += chunk
    }

    readResult.flatten.toArray should be(originalData)
  }

  it should "work with UTF-8 encoding round-trip" in {
    val originalStrings = List("Hello", " ", "World", "! ", "世界", " ", "😀")
    val stringFlow      = Flow(originalStrings*)
    val output          = new ByteArrayOutputStream()

    stringFlow.encodeToUtf8().toOutputStream(output)

    val input = new java.io.ByteArrayInputStream(output.toByteArray)
    val result = scala.collection.mutable.ArrayBuffer[String]()
    Flow.fromInputStream(input).asUtf8String().collect { str =>
      result += str
    }

    result.mkString("") should be(originalStrings.mkString(""))
  }

  it should "handle multiple empty arrays at the start" in {
    val empty  = Array.empty[Byte]
    val data   = "Hello".getBytes()
    val flow   = Flow(empty, empty, data)
    val output = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    output.toByteArray should be(data)
  }

  it should "handle multiple empty arrays at the end" in {
    val data   = "Hello".getBytes()
    val empty  = Array.empty[Byte]
    val flow   = Flow(data, empty, empty)
    val output = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    output.toByteArray should be(data)
  }

  it should "handle flow with only empty arrays" in {
    val empty  = Array.empty[Byte]
    val flow   = Flow(empty, empty, empty)
    val output = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    output.toByteArray should be(Array.empty[Byte])
  }

  it should "work with data transformed through map" in {
    val numbers = List(1, 2, 3)
    val flow    = Flow(numbers*).map(n => Array(n.toByte))
    val output  = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    output.toByteArray should be(Array[Byte](1, 2, 3))
  }

  it should "work with filtered data" in {
    val chunks = List("A".getBytes(), "B".getBytes(), "C".getBytes(), "D".getBytes())
    val flow   = Flow(chunks*).filter(arr => arr(0) != 'B'.toByte)
    val output = new ByteArrayOutputStream()

    flow.toOutputStream(output)

    output.toByteArray should be("ACD".getBytes())
  }
}
