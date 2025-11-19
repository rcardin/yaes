package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.IOException
import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable.ArrayBuffer
import scala.util.Using

class FlowFromFileSpec extends AnyFlatSpec with Matchers {

  "fromFile" should "read a simple text file" in {
    val tempFile = Files.createTempFile("test", ".txt")
    try {
      val content = "Hello, World!"
      Files.write(tempFile, content.getBytes())

      val actualResult = ArrayBuffer[Array[Byte]]()
      Flow.fromFile(tempFile).collect { chunk =>
        actualResult += chunk
      }

      val result = actualResult.flatten.toArray
      new String(result) should be(content)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "read an empty file" in {
    val tempFile = Files.createTempFile("test-empty", ".txt")
    try {
      val actualResult = ArrayBuffer[Array[Byte]]()
      Flow.fromFile(tempFile).collect { chunk =>
        actualResult += chunk
      }

      actualResult should be(empty)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "read a file with data smaller than buffer size" in {
    val tempFile = Files.createTempFile("test-small", ".txt")
    try {
      val content = "Hi"
      Files.write(tempFile, content.getBytes())

      val actualResult = ArrayBuffer[Array[Byte]]()
      Flow.fromFile(tempFile, bufferSize = 1024).collect { chunk =>
        actualResult += chunk
      }

      actualResult should have size 1
      new String(actualResult.head) should be(content)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "read a file with data larger than buffer size" in {
    val tempFile = Files.createTempFile("test-large", ".txt")
    try {
      val content = "A" * 10000 // 10KB of data
      Files.write(tempFile, content.getBytes())

      val actualResult = ArrayBuffer[Array[Byte]]()
      Flow.fromFile(tempFile, bufferSize = 1024).collect { chunk =>
        actualResult += chunk
      }

      actualResult.size should be > 1
      val result = actualResult.flatten.toArray
      new String(result) should be(content)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "respect the buffer size parameter" in {
    val tempFile = Files.createTempFile("test-buffer", ".txt")
    try {
      val content = "A" * 100
      Files.write(tempFile, content.getBytes())

      val actualResult = ArrayBuffer[Array[Byte]]()
      Flow.fromFile(tempFile, bufferSize = 10).collect { chunk =>
        actualResult += chunk
      }

      actualResult.size should be(10)
      actualResult.foreach { chunk =>
        chunk.length should be(10)
      }
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "read binary data correctly" in {
    val tempFile = Files.createTempFile("test-binary", ".bin")
    try {
      val data: Array[Byte] = Array(0, 1, 2, 3, 4, 5, 255.toByte, 254.toByte, 253.toByte)
      Files.write(tempFile, data)

      val actualResult = ArrayBuffer[Array[Byte]]()
      Flow.fromFile(tempFile, bufferSize = 4).collect { chunk =>
        actualResult += chunk
      }

      val result = actualResult.flatten.toArray
      result should be(data)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "throw IOException with context when file does not exist" in {
    val nonExistentPath = Paths.get("/non/existent/path/file.txt")

    val exception = intercept[IOException] {
      Flow.fromFile(nonExistentPath).collect { _ => }
    }

    exception.getMessage should include(nonExistentPath.toString)
  }

  it should "throw IOException with context when path is a directory" in {
    val tempDir = Files.createTempDirectory("test-dir")
    try {
      val exception = intercept[IOException] {
        Flow.fromFile(tempDir).collect { _ => }
      }

      exception.getMessage should include(tempDir.toString)
    } finally {
      Files.deleteIfExists(tempDir)
    }
  }

  it should "close the input stream after successful collection" in {
    val tempFile = Files.createTempFile("test-close", ".txt")
    try {
      Files.write(tempFile, "test data".getBytes())

      Flow.fromFile(tempFile).collect { _ => }

      // If we can delete the file, it means the stream was properly closed
      // (on Windows, open file handles prevent deletion)
      Files.deleteIfExists(tempFile) should be(true)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "close the input stream even when an exception occurs during collection" in {
    val tempFile = Files.createTempFile("test-exception", ".txt")
    try {
      Files.write(tempFile, "test data".getBytes())

      intercept[RuntimeException] {
        Flow.fromFile(tempFile).collect { _ =>
          throw new RuntimeException("Test exception")
        }
      }

      // Stream should be closed even though an exception was thrown
      Files.deleteIfExists(tempFile) should be(true)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work with asUtf8String() to read text files" in {
    val tempFile = Files.createTempFile("test-utf8", ".txt")
    try {
      val content = "Hello 世界! 😀"
      Files.write(tempFile, content.getBytes("UTF-8"))

      val actualResult = ArrayBuffer[String]()
      Flow.fromFile(tempFile, bufferSize = 5)
        .asUtf8String()
        .collect { str =>
          actualResult += str
        }

      actualResult.mkString should be(content)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work with linesInUtf8() to read lines from a file" in {
    val tempFile = Files.createTempFile("test-lines", ".txt")
    try {
      val lines = List("Line 1", "Line 2", "Line 3")
      Files.write(tempFile, lines.mkString("\n").getBytes("UTF-8"))

      val actualResult = ArrayBuffer[String]()
      Flow.fromFile(tempFile)
        .linesInUtf8()
        .collect { line =>
          actualResult += line
        }

      actualResult should contain theSameElementsInOrderAs lines
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work with map and filter operations" in {
    val tempFile = Files.createTempFile("test-operations", ".txt")
    try {
      val content = "1\n2\n3\n4\n5\n"
      Files.write(tempFile, content.getBytes())

      val actualResult = ArrayBuffer[Int]()
      Flow.fromFile(tempFile)
        .linesInUtf8()
        .filter(_.nonEmpty)
        .map(_.toInt)
        .filter(_ % 2 == 0)
        .collect { value =>
          actualResult += value
        }

      actualResult should contain theSameElementsInOrderAs Seq(2, 4)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work with fold to process file contents" in {
    val tempFile = Files.createTempFile("test-fold", ".txt")
    try {
      val content = "1\n2\n3\n4\n5"
      Files.write(tempFile, content.getBytes())

      val sum = Flow.fromFile(tempFile)
        .linesInUtf8()
        .filter(_.nonEmpty)
        .map(_.toInt)
        .fold(0)(_ + _)

      sum should be(15)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "throw IllegalArgumentException when bufferSize is less than or equal to 0" in {
    val tempFile = Files.createTempFile("test", ".txt")
    try {
      Files.write(tempFile, "test".getBytes())

      intercept[IllegalArgumentException] {
        Flow.fromFile(tempFile, bufferSize = 0).collect { _ => }
      }

      intercept[IllegalArgumentException] {
        Flow.fromFile(tempFile, bufferSize = -1).collect { _ => }
      }
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "handle UTF-8 files with BOM correctly" in {
    val tempFile = Files.createTempFile("test-bom", ".txt")
    try {
      val bom     = Array(0xEF.toByte, 0xBB.toByte, 0xBF.toByte)
      val content = "Hello, World!"
      val data    = bom ++ content.getBytes("UTF-8")
      Files.write(tempFile, data)

      val result = Flow.fromFile(tempFile)
        .asUtf8String()
        .fold("")(_ + _)

      // The BOM should be included in the result
      result should startWith("\uFEFF")
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "handle chunked reading with complex UTF-8 characters" in {
    val tempFile = Files.createTempFile("test-complex-utf8", ".txt")
    try {
      val content = "日本語テスト😀🎉🌟"
      Files.write(tempFile, content.getBytes("UTF-8"))

      val result = Flow.fromFile(tempFile, bufferSize = 3)
        .asUtf8String()
        .fold("")(_ + _)

      result should be(content)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work with toOutputStream to copy files" in {
    val sourceFile = Files.createTempFile("source", ".txt")
    val destFile   = Files.createTempFile("dest", ".txt")
    try {
      val content = "Copy this content"
      Files.write(sourceFile, content.getBytes())

      Using(Files.newOutputStream(destFile)) { outputStream =>
        Flow.fromFile(sourceFile).toOutputStream(outputStream)
      }

      val result = Files.readAllBytes(destFile)
      new String(result) should be(content)
    } finally {
      Files.deleteIfExists(sourceFile)
      Files.deleteIfExists(destFile)
    }
  }
}
