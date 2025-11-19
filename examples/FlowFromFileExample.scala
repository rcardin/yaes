package in.rcard.yaes

import java.nio.file.{Files, Paths}
import scala.util.Using

object FlowFromFileExample extends App {

  // Create a temporary file with some sample content
  val tempFile = Files.createTempFile("example", ".txt")
  val content =
    """Line 1: Hello, World!
      |Line 2: This is a test file
      |Line 3: 世界 (Japanese for "world")
      |Line 4: 😀 (Emoji)
      |Line 5: End of file""".stripMargin

  Files.write(tempFile, content.getBytes("UTF-8"))

  println(s"Created temporary file: $tempFile")
  println("-" * 50)

  // Example 1: Read entire file as a string
  println("Example 1: Read entire file as UTF-8 string")
  val fullContent = Flow
    .fromFile(tempFile)
    .asUtf8String()
    .fold("")(_ + _)
  println(fullContent)
  println("-" * 50)

  // Example 2: Read file line by line
  println("\nExample 2: Read file line by line")
  Flow
    .fromFile(tempFile)
    .linesInUtf8()
    .zipWithIndex()
    .collect { case (line, index) =>
      println(s"Line ${index + 1}: $line")
    }
  println("-" * 50)

  // Example 3: Filter and process lines
  println("\nExample 3: Filter lines containing 'world' (case-insensitive)")
  Flow
    .fromFile(tempFile)
    .linesInUtf8()
    .filter(_.toLowerCase.contains("world"))
    .collect { line =>
      println(s"  - $line")
    }
  println("-" * 50)

  // Example 4: Count lines
  println("\nExample 4: Count lines in file")
  val lineCount = Flow
    .fromFile(tempFile)
    .linesInUtf8()
    .count()
  println(s"Total lines: $lineCount")
  println("-" * 50)

  // Example 5: Copy file with small buffer size (demonstrating chunking)
  println("\nExample 5: Copy file to new location")
  val destFile = Files.createTempFile("copy", ".txt")
  Using(Files.newOutputStream(destFile)) { outputStream =>
    Flow.fromFile(tempFile, bufferSize = 32).toOutputStream(outputStream)
  }
  val copiedContent = new String(Files.readAllBytes(destFile))
  println(s"Copied to: $destFile")
  println(s"Content matches: ${copiedContent == fullContent}")
  println("-" * 50)

  // Example 6: Process file with transformations
  println("\nExample 6: Extract and uppercase words")
  Flow
    .fromFile(tempFile)
    .linesInUtf8()
    .map(_.split("\\s+").toSeq)
    .transform { words =>
      words.foreach(word => Flow.emit(word))
    }
    .filter(_.matches("[a-zA-Z]+"))
    .map(_.toUpperCase)
    .take(10)
    .collect { word =>
      println(s"  - $word")
    }
  println("-" * 50)

  // Cleanup
  Files.deleteIfExists(tempFile)
  Files.deleteIfExists(destFile)
  println("\nTemporary files cleaned up")
}
