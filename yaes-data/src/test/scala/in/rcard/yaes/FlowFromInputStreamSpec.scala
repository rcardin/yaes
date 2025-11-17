package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream

class FlowFromInputStreamSpec extends AnyFlatSpec with Matchers {

  "fromInputStream" should "emit byte arrays from an InputStream" in {
    val data  = "Hello, World!".getBytes()
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input).collect { chunk =>
      actualResult += chunk
    }

    val result = actualResult.flatten.toArray
    result should be(data)
  }

  it should "handle empty InputStream" in {
    val input = new ByteArrayInputStream(Array.empty[Byte])

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input).collect { chunk =>
      actualResult += chunk
    }

    actualResult should be(empty)
  }

  it should "handle InputStream with data smaller than buffer size" in {
    val data  = "Hi".getBytes()
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input, bufferSize = 1024).collect { chunk =>
      actualResult += chunk
    }

    actualResult should have size 1
    actualResult.head should be(data)
  }

  it should "handle InputStream with data larger than buffer size" in {
    val data  = "A" * 10000 // 10KB of data
    val bytes = data.getBytes()
    val input = new ByteArrayInputStream(bytes)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input, bufferSize = 1024).collect { chunk =>
      actualResult += chunk
    }

    actualResult.size should be > 1
    val result = actualResult.flatten.toArray
    result should be(bytes)
  }

  it should "respect the buffer size parameter" in {
    val data  = "A" * 100
    val bytes = data.getBytes()
    val input = new ByteArrayInputStream(bytes)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input, bufferSize = 10).collect { chunk =>
      actualResult += chunk
    }

    actualResult.size should be(10)
    actualResult.foreach { chunk =>
      chunk.length should be(10)
    }
  }

  it should "emit correctly sized chunks when data is not evenly divisible by buffer size" in {
    val data  = "A" * 25 // 25 bytes
    val bytes = data.getBytes()
    val input = new ByteArrayInputStream(bytes)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input, bufferSize = 10).collect { chunk =>
      actualResult += chunk
    }

    actualResult.size should be(3)
    actualResult(0).length should be(10)
    actualResult(1).length should be(10)
    actualResult(2).length should be(5) // Last chunk is smaller
  }

  it should "work with binary data" in {
    val data: Array[Byte] = Array(0, 1, 2, 3, 4, 5, 255.toByte, 254.toByte, 253.toByte)
    val input             = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input, bufferSize = 4).collect { chunk =>
      actualResult += chunk
    }

    val result = actualResult.flatten.toArray
    result should be(data)
  }

  it should "be composable with other Flow operators" in {
    val data  = "Hello, World!".getBytes()
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow
      .fromInputStream(input, bufferSize = 5)
      .take(2)
      .collect { chunk =>
        actualResult += chunk
      }

    actualResult.size should be(2)
    val result = actualResult.flatten.toArray
    result should be("Hello, Wor".getBytes())
  }

  it should "work with map operator to process chunks" in {
    val data  = "hello".getBytes()
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[Int]()
    Flow
      .fromInputStream(input, bufferSize = 2)
      .map(_.length)
      .collect { chunkSize =>
        actualResult += chunkSize
      }

    actualResult should contain theSameElementsInOrderAs Seq(2, 2, 1)
  }

  it should "work with filter operator" in {
    val data  = "abcdefghij".getBytes()
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow
      .fromInputStream(input, bufferSize = 3)
      .filter(_.length == 3)
      .collect { chunk =>
        actualResult += chunk
      }

    actualResult.size should be(3)
    val result = actualResult.flatten.toArray
    result should be("abcdefghi".getBytes())
  }

  it should "work with fold operator to count total bytes" in {
    val data  = "Hello, World!".getBytes()
    val input = new ByteArrayInputStream(data)

    val totalBytes = Flow
      .fromInputStream(input, bufferSize = 5)
      .fold(0) { (acc, chunk) =>
        acc + chunk.length
      }

    totalBytes should be(data.length)
  }

  it should "allow multiple collections creating independent streams" in {
    val data = "Test".getBytes()

    // First collection
    val input1        = new ByteArrayInputStream(data)
    val actualResult1 = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input1).collect { chunk =>
      actualResult1 += chunk
    }

    // Second collection
    val input2        = new ByteArrayInputStream(data)
    val actualResult2 = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input2).collect { chunk =>
      actualResult2 += chunk
    }

    val result1 = actualResult1.flatten.toArray
    val result2 = actualResult2.flatten.toArray

    result1 should be(data)
    result2 should be(data)
  }

  it should "use default buffer size of 8192 when not specified" in {
    val data  = "A" * 20000 // 20KB of data
    val bytes = data.getBytes()
    val input = new ByteArrayInputStream(bytes)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input).collect { chunk =>
      actualResult += chunk
    }

    // With 20KB and 8KB buffer, we should get 3 chunks: 8KB, 8KB, 4KB
    actualResult.size should be(3)
    actualResult(0).length should be(8192)
    actualResult(1).length should be(8192)
    actualResult(2).length should be(20000 - 16384)
  }

  it should "handle single byte reads correctly" in {
    val data  = "X".getBytes()
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input, bufferSize = 1).collect { chunk =>
      actualResult += chunk
    }

    actualResult.size should be(1)
    actualResult.head should be(data)
  }

  it should "work with zipWithIndex operator" in {
    val data  = "abcdefghij".getBytes()
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[(Array[Byte], Long)]()
    Flow
      .fromInputStream(input, bufferSize = 3)
      .zipWithIndex()
      .collect { value =>
        actualResult += value
      }

    actualResult.size should be(4)
    actualResult(0)._2 should be(0L)
    actualResult(1)._2 should be(1L)
    actualResult(2)._2 should be(2L)
    actualResult(3)._2 should be(3L)
  }

  it should "stop reading when flow is terminated early with take" in {
    val data  = "A" * 1000
    val bytes = data.getBytes()
    val input = new ByteArrayInputStream(bytes)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow
      .fromInputStream(input, bufferSize = 100)
      .take(3)
      .collect { chunk =>
        actualResult += chunk
      }

    actualResult.size should be(3)
    val result = actualResult.flatten.toArray
    result.length should be(300) // 3 chunks of 100 bytes each
  }

  it should "handle buffer size of 1 correctly for precise byte-by-byte reading" in {
    val data  = Array[Byte](1, 2, 3, 4, 5)
    val input = new ByteArrayInputStream(data)

    val actualResult = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    Flow.fromInputStream(input, bufferSize = 1).collect { chunk =>
      actualResult += chunk
    }

    actualResult.size should be(5)
    actualResult.map(_.head) should contain theSameElementsInOrderAs Seq(1, 2, 3, 4, 5)
  }

  it should "throw IllegalArgumentException for buffer size less than 1" in {
    val input = new ByteArrayInputStream("test".getBytes())

    val exception = intercept[IllegalArgumentException] {
      Flow.fromInputStream(input, bufferSize = 0).collect(_ => ())
    }

    exception.getMessage should include("bufferSize must be greater than 0")
  }

  it should "throw IllegalArgumentException for negative buffer size" in {
    val input = new ByteArrayInputStream("test".getBytes())

    val exception = intercept[IllegalArgumentException] {
      Flow.fromInputStream(input, bufferSize = -1).collect(_ => ())
    }

    exception.getMessage should include("bufferSize must be greater than 0")
  }
}
