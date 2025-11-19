import in.rcard.yaes.Flow
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

object LinesExample extends App {

  // Example 1: Reading lines from UTF-8 text
  println("=== Example 1: Reading lines from UTF-8 text ===")
  val text = """Line 1
Line 2
Line 3"""
  val input1 = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))
  
  Flow.fromInputStream(input1)
    .linesInUtf8()
    .zipWithIndex()
    .collect { case (line, index) =>
      println(s"Line ${index + 1}: $line")
    }

  // Example 2: Processing CSV data line by line
  println("\n=== Example 2: Processing CSV data ===")
  val csvData = """name,age,city
Alice,30,New York
Bob,25,San Francisco
Charlie,35,Boston"""
  val input2 = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8))
  
  Flow.fromInputStream(input2)
    .linesInUtf8()
    .drop(1) // Skip header
    .map(_.split(","))
    .collect { fields =>
      println(s"${fields(0)} is ${fields(1)} years old and lives in ${fields(2)}")
    }

  // Example 3: Filtering lines
  println("\n=== Example 3: Filtering lines ===")
  val logData = """INFO: Application started
ERROR: Connection failed
INFO: Retrying connection
ERROR: Maximum retries exceeded
INFO: Application stopped"""
  val input3 = new ByteArrayInputStream(logData.getBytes(StandardCharsets.UTF_8))
  
  Flow.fromInputStream(input3)
    .linesInUtf8()
    .filter(_.startsWith("ERROR:"))
    .collect { errorLine =>
      println(s"Found error: $errorLine")
    }

  // Example 4: Counting lines
  println("\n=== Example 4: Counting lines ===")
  val multilineText = """First line
Second line
Third line
Fourth line
Fifth line"""
  val input4 = new ByteArrayInputStream(multilineText.getBytes(StandardCharsets.UTF_8))
  
  val lineCount = Flow.fromInputStream(input4)
    .linesInUtf8()
    .count()
  println(s"Total lines: $lineCount")

  // Example 5: Handling different encodings
  println("\n=== Example 5: Different encodings (ISO-8859-1) ===")
  val isoText = "café\nlatte\nmocha"
  val input5 = new ByteArrayInputStream(isoText.getBytes(StandardCharsets.ISO_8859_1))
  
  Flow.fromInputStream(input5)
    .linesIn(StandardCharsets.ISO_8859_1)
    .collect { line =>
      println(s"Beverage: $line")
    }

  // Example 6: Handling multi-byte UTF-8 characters
  println("\n=== Example 6: Multi-byte UTF-8 characters ===")
  val unicodeText = """Hello 世界! 😀
Café ☕
日本語"""
  val input6 = new ByteArrayInputStream(unicodeText.getBytes(StandardCharsets.UTF_8))
  
  Flow.fromInputStream(input6, bufferSize = 5) // Small buffer to test chunking
    .linesInUtf8()
    .collect { line =>
      println(line)
    }

  // Example 7: Taking first N lines
  println("\n=== Example 7: Taking first 3 lines ===")
  val manyLines = (1 to 100).map(i => s"Line $i").mkString("\n")
  val input7 = new ByteArrayInputStream(manyLines.getBytes(StandardCharsets.UTF_8))
  
  Flow.fromInputStream(input7)
    .linesInUtf8()
    .take(3)
    .collect { line =>
      println(line)
    }
}
