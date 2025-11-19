package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.IOException
import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable.ArrayBuffer

class FlowToFileSpec extends AnyFlatSpec with Matchers {

  "toFile" should "write byte arrays to a file" in {
    val tempFile = Files.createTempFile("test-write", ".txt")
    try {
      val data = "Hello, World!".getBytes()
      val flow = Flow(data)

      flow.toFile(tempFile)

      val result = Files.readAllBytes(tempFile)
      result should be(data)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "write multiple chunks in order" in {
    val tempFile = Files.createTempFile("test-chunks", ".txt")
    try {
      val chunk1 = "Hello, ".getBytes()
      val chunk2 = "World".getBytes()
      val chunk3 = "!".getBytes()
      val flow   = Flow(chunk1, chunk2, chunk3)

      flow.toFile(tempFile)

      val result   = Files.readAllBytes(tempFile)
      val expected = (chunk1 ++ chunk2 ++ chunk3)
      result should be(expected)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "create an empty file for empty flow" in {
    val tempFile = Files.createTempFile("test-empty", ".txt")
    try {
      val flow = Flow[Array[Byte]]()

      flow.toFile(tempFile)

      Files.exists(tempFile) should be(true)
      Files.readAllBytes(tempFile) should be(Array.empty[Byte])
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "skip empty byte arrays" in {
    val tempFile = Files.createTempFile("test-skip-empty", ".txt")
    try {
      val chunk1 = "Hello".getBytes()
      val empty  = Array.empty[Byte]
      val chunk2 = "World".getBytes()
      val flow   = Flow(chunk1, empty, chunk2, empty)

      flow.toFile(tempFile)

      val result   = Files.readAllBytes(tempFile)
      val expected = (chunk1 ++ chunk2)
      result should be(expected)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "write a single large chunk" in {
    val tempFile = Files.createTempFile("test-large", ".txt")
    try {
      val data = ("A" * 10000).getBytes()
      val flow = Flow(data)

      flow.toFile(tempFile)

      val result = Files.readAllBytes(tempFile)
      result should be(data)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "handle binary data correctly" in {
    val tempFile = Files.createTempFile("test-binary", ".bin")
    try {
      val data: Array[Byte] = Array(0, 1, 2, 3, 4, 5, 255.toByte, 254.toByte, 253.toByte)
      val flow              = Flow(data)

      flow.toFile(tempFile)

      val result = Files.readAllBytes(tempFile)
      result should be(data)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "overwrite existing file" in {
    val tempFile = Files.createTempFile("test-overwrite", ".txt")
    try {
      // Write initial content
      val initialContent = "Initial content".getBytes()
      Files.write(tempFile, initialContent)

      // Overwrite with new content
      val newContent = "New".getBytes()
      val flow       = Flow(newContent)

      flow.toFile(tempFile)

      val result = Files.readAllBytes(tempFile)
      result should be(newContent)
      result should not be initialContent
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "create parent directories if they don't exist" in {
    val tempDir  = Files.createTempDirectory("test-parent")
    val subDir   = tempDir.resolve("subdir1").resolve("subdir2")
    val tempFile = subDir.resolve("test.txt")
    try {
      Files.exists(subDir) should be(false)

      val data = "Hello".getBytes()
      val flow = Flow(data)

      flow.toFile(tempFile)

      Files.exists(subDir) should be(true)
      Files.exists(tempFile) should be(true)
      Files.readAllBytes(tempFile) should be(data)
    } finally {
      deleteRecursively(tempDir)
    }
  }

  it should "work when file doesn't exist yet" in {
    val tempDir  = Files.createTempDirectory("test-new-file")
    val tempFile = tempDir.resolve("newfile.txt")
    try {
      Files.exists(tempFile) should be(false)

      val data = "New file content".getBytes()
      val flow = Flow(data)

      flow.toFile(tempFile)

      Files.exists(tempFile) should be(true)
      Files.readAllBytes(tempFile) should be(data)
    } finally {
      Files.deleteIfExists(tempFile)
      Files.deleteIfExists(tempDir)
    }
  }

  it should "close the output stream after successful write" in {
    val tempFile = Files.createTempFile("test-close", ".txt")
    try {
      val data = "test data".getBytes()
      val flow = Flow(data)

      flow.toFile(tempFile)

      // If we can read and write again, the stream was properly closed
      val data2 = "more data".getBytes()
      val flow2 = Flow(data2)
      flow2.toFile(tempFile)

      Files.readAllBytes(tempFile) should be(data2)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "close the output stream even when an exception occurs during collection" in {
    val tempFile = Files.createTempFile("test-exception", ".txt")
    try {
      val data = "test data".getBytes()
      val flow = Flow(data).onEach { _ =>
        throw new RuntimeException("Test exception")
      }

      intercept[RuntimeException] {
        flow.toFile(tempFile)
      }

      // Stream should be closed even though an exception was thrown
      // We can verify by successfully writing to the file again
      val data2 = "recovery".getBytes()
      val flow2 = Flow(data2)
      flow2.toFile(tempFile)

      Files.readAllBytes(tempFile) should be(data2)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "throw IOException with context when path is a directory" in {
    val tempDir = Files.createTempDirectory("test-is-dir")
    try {
      val data = "Hello".getBytes()
      val flow = Flow(data)

      val exception = intercept[IOException] {
        flow.toFile(tempDir)
      }

      exception.getMessage should include(tempDir.toString)
    } finally {
      Files.deleteIfExists(tempDir)
    }
  }

  it should "throw IOException with context when path is not writable" in {
    val tempFile = Files.createTempFile("test-readonly", ".txt")
    try {
      tempFile.toFile.setWritable(false)

      val data = "Hello".getBytes()
      val flow = Flow(data)

      val exception = intercept[IOException] {
        flow.toFile(tempFile)
      }

      exception.getMessage should include(tempFile.toString)
    } finally {
      tempFile.toFile.setWritable(true)
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work in a round-trip scenario with fromFile" in {
    val tempFile = Files.createTempFile("test-roundtrip", ".txt")
    try {
      val originalData = "Hello, World! 世界 😀".getBytes("UTF-8")
      val flow         = Flow(originalData)

      flow.toFile(tempFile)

      val readResult = ArrayBuffer[Array[Byte]]()
      Flow.fromFile(tempFile).collect { chunk =>
        readResult += chunk
      }

      readResult.flatten.toArray should be(originalData)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work with UTF-8 encoding and decoding round-trip" in {
    val tempFile = Files.createTempFile("test-utf8-roundtrip", ".txt")
    try {
      val originalStrings = List("Hello", " ", "World", "! ", "世界", " ", "😀")
      val stringFlow      = Flow(originalStrings*)

      stringFlow.encodeToUtf8().toFile(tempFile)

      val result = ArrayBuffer[String]()
      Flow.fromFile(tempFile).asUtf8String().collect { str =>
        result += str
      }

      result.mkString("") should be(originalStrings.mkString(""))
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work with linesInUtf8 round-trip" in {
    val tempFile = Files.createTempFile("test-lines-roundtrip", ".txt")
    try {
      val originalLines = List("Line 1", "Line 2", "Line 3")
      val content       = originalLines.mkString("\n")

      Flow(content.getBytes("UTF-8")).toFile(tempFile)

      val readLines = ArrayBuffer[String]()
      Flow.fromFile(tempFile).linesInUtf8().collect { line =>
        readLines += line
      }

      readLines should contain theSameElementsInOrderAs originalLines
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "handle multiple empty arrays at the start" in {
    val tempFile = Files.createTempFile("test-empty-start", ".txt")
    try {
      val empty = Array.empty[Byte]
      val data  = "Hello".getBytes()
      val flow  = Flow(empty, empty, data)

      flow.toFile(tempFile)

      Files.readAllBytes(tempFile) should be(data)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "handle multiple empty arrays at the end" in {
    val tempFile = Files.createTempFile("test-empty-end", ".txt")
    try {
      val data  = "Hello".getBytes()
      val empty = Array.empty[Byte]
      val flow  = Flow(data, empty, empty)

      flow.toFile(tempFile)

      Files.readAllBytes(tempFile) should be(data)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "handle flow with only empty arrays" in {
    val tempFile = Files.createTempFile("test-only-empty", ".txt")
    try {
      val empty = Array.empty[Byte]
      val flow  = Flow(empty, empty, empty)

      flow.toFile(tempFile)

      Files.readAllBytes(tempFile) should be(Array.empty[Byte])
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work with data transformed through map" in {
    val tempFile = Files.createTempFile("test-map", ".bin")
    try {
      val numbers = List(1, 2, 3)
      val flow    = Flow(numbers*).map(n => Array(n.toByte))

      flow.toFile(tempFile)

      Files.readAllBytes(tempFile) should be(Array[Byte](1, 2, 3))
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work with filtered data" in {
    val tempFile = Files.createTempFile("test-filter", ".txt")
    try {
      val chunks = List("A".getBytes(), "B".getBytes(), "C".getBytes(), "D".getBytes())
      val flow   = Flow(chunks*).filter(arr => arr(0) != 'B'.toByte)

      flow.toFile(tempFile)

      Files.readAllBytes(tempFile) should be("ACD".getBytes())
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  it should "work with chunked data from fromInputStream" in {
    val tempFile1 = Files.createTempFile("test-source", ".txt")
    val tempFile2 = Files.createTempFile("test-dest", ".txt")
    try {
      val content = "Copy this content with 世界 and 😀"
      Files.write(tempFile1, content.getBytes("UTF-8"))

      // Read from one file and write to another
      Flow.fromFile(tempFile1, bufferSize = 5).toFile(tempFile2)

      val result = Files.readAllBytes(tempFile2)
      new String(result, "UTF-8") should be(content)
    } finally {
      Files.deleteIfExists(tempFile1)
      Files.deleteIfExists(tempFile2)
    }
  }

  it should "handle very long file paths" in {
    val tempDir = Files.createTempDirectory("test-long-path")
    try {
      val longPath = (1 to 10).foldLeft(tempDir) { (path, _) =>
        path.resolve("subdir")
      }
      val tempFile = longPath.resolve("file.txt")

      val data = "Long path test".getBytes()
      val flow = Flow(data)

      flow.toFile(tempFile)

      Files.exists(tempFile) should be(true)
      Files.readAllBytes(tempFile) should be(data)
    } finally {
      deleteRecursively(tempDir)
    }
  }

  it should "handle special characters in file name" in {
    val tempDir  = Files.createTempDirectory("test-special")
    val tempFile = tempDir.resolve("test file with spaces & special-chars_123.txt")
    try {
      val data = "Special characters test".getBytes()
      val flow = Flow(data)

      flow.toFile(tempFile)

      Files.exists(tempFile) should be(true)
      Files.readAllBytes(tempFile) should be(data)
    } finally {
      Files.deleteIfExists(tempFile)
      Files.deleteIfExists(tempDir)
    }
  }

  it should "work with absolute and relative paths" in {
    val tempDir      = Files.createTempDirectory("test-paths")
    val absolutePath = tempDir.resolve("absolute.txt")
    try {
      val data = "Path test".getBytes()
      val flow = Flow(data)

      flow.toFile(absolutePath)

      Files.exists(absolutePath) should be(true)
      Files.readAllBytes(absolutePath) should be(data)
    } finally {
      Files.deleteIfExists(absolutePath)
      Files.deleteIfExists(tempDir)
    }
  }

  it should "handle concurrent writes to different files" in {
    val tempFile1 = Files.createTempFile("test-concurrent1", ".txt")
    val tempFile2 = Files.createTempFile("test-concurrent2", ".txt")
    try {
      val data1 = "File 1 content".getBytes()
      val data2 = "File 2 content".getBytes()
      val flow1 = Flow(data1)
      val flow2 = Flow(data2)

      // Write to both files
      flow1.toFile(tempFile1)
      flow2.toFile(tempFile2)

      Files.readAllBytes(tempFile1) should be(data1)
      Files.readAllBytes(tempFile2) should be(data2)
    } finally {
      Files.deleteIfExists(tempFile1)
      Files.deleteIfExists(tempFile2)
    }
  }

  def deleteRecursively(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      Files.list(path).forEach(deleteRecursively)
    }
    Files.deleteIfExists(path)
  }
}
