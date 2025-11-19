---
layout: default
title: "λÆS Data Structures"
---

# λÆS Data Structures

The `yaes-data` module provides a collection of functional data structures that complement the λÆS effects system. These data structures are designed to work seamlessly with λÆS effects and provide efficient, functional alternatives to traditional imperative data structures.

![Maven Central](https://img.shields.io/maven-central/v/in.rcard.yaes/yaes-data_3)
[![javadoc](https://javadoc.io/badge2/in.rcard.yaes/yaes-data_3/javadoc.svg)](https://javadoc.io/doc/in.rcard.yaes/yaes-data_3)

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-data" % "0.8.0"
```

## Available Data Structures

### [Flow](#flow) 
A cold asynchronous data stream that sequentially emits values, similar to reactive streams but designed for functional programming.

---

## Flow

A `Flow` is a cold asynchronous data stream that sequentially emits values and completes normally or with an exception. It's conceptually similar to Iterators from the Collections framework but designed for asynchronous operation.

### Key Characteristics

- **Cold**: Flows don't produce values until collected
- **Asynchronous**: Values can be emitted asynchronously
- **Sequential**: Values are emitted in order
- **Composable**: Rich set of operators for transformation
- **Functional**: Immutable and side-effect free

### Key Differences from Iterator

| Feature | Iterator | Flow |
|---------|----------|------|
| Execution | Synchronous | Asynchronous |
| Transformation | Limited | Rich operator set |
| Observation | Direct access | Collect method |
| Composition | Basic | Highly composable |

## Creating Flows

### From Explicit Emissions

```scala
import in.rcard.yaes.Flow

val numbersFlow: Flow[Int] = Flow.flow[Int] {
  Flow.emit(1)
  Flow.emit(2)
  Flow.emit(3)
}
```

### From Collections

```scala
import in.rcard.yaes.Flow

val listFlow: Flow[Int] = List(1, 2, 3).asFlow()
val setFlow: Flow[String] = Set("a", "b", "c").asFlow()
```

### From Varargs

```scala
import in.rcard.yaes.Flow

val flow: Flow[String] = Flow("hello", "world", "!")
```

### From InputStream

Create a flow that reads data from an InputStream as byte chunks:

```scala
import in.rcard.yaes.Flow
import java.io.FileInputStream

val inputStream = new FileInputStream("data.bin")
val byteFlow: Flow[Array[Byte]] = Flow.fromInputStream(inputStream, bufferSize = 8192)
```

Note: The `fromInputStream` method does NOT automatically close the InputStream. Use resource management patterns like `Using` to ensure proper cleanup.

### From File

Create a flow that reads data from a file with automatic resource management:

```scala
import in.rcard.yaes.Flow
import java.nio.file.Paths

val fileFlow: Flow[Array[Byte]] = Flow.fromFile(Paths.get("data.txt"), bufferSize = 8192)
```

The `fromFile` method automatically manages the file's InputStream lifecycle - it opens the stream when collection starts and closes it when collection completes (either successfully or due to an exception). This makes it more convenient than `fromInputStream` for file operations:

```scala
import in.rcard.yaes.Flow
import java.nio.file.Paths

// Read entire file as UTF-8 string (stream automatically closed)
val content = Flow.fromFile(Paths.get("data.txt"))
  .asUtf8String()
  .fold("")(_ + _)

// Process file line by line
Flow.fromFile(Paths.get("data.txt"))
  .linesInUtf8()
  .filter(_.nonEmpty)
  .collect { line => println(line) }

// Copy file
import java.nio.file.Files
import scala.util.Using

val destPath = Paths.get("copy.txt")
Using(Files.newOutputStream(destPath)) { outputStream =>
  Flow.fromFile(Paths.get("source.txt")).toOutputStream(outputStream)
}
```

Key differences from `fromInputStream`:
- **Automatic cleanup**: Stream is closed automatically after collection
- **Error context**: `IOException` includes the file path for better debugging
- **File-focused**: Designed specifically for file I/O operations

## Collecting Flow Values

Use the `collect` method to observe and consume flow values:

```scala
import in.rcard.yaes.Flow
import scala.collection.mutable.ArrayBuffer

val result = ArrayBuffer[Int]()
Flow(1, 2, 3).collect { value =>
  result += value
}
// result now contains: [1, 2, 3]
```

## Transformation Operators

### map

Transform each emitted value:

```scala
import in.rcard.yaes.Flow

val stringFlow = Flow(1, 2, 3)
  .map(_.toString)
  .map("Number: " + _)

// Emits: "Number: 1", "Number: 2", "Number: 3"
```

### filter

Keep only values matching a predicate:

```scala
import in.rcard.yaes.Flow

val evenNumbers = Flow(1, 2, 3, 4, 5, 6)
  .filter(_ % 2 == 0)

// Emits: 2, 4, 6
```

### transform

Apply complex transformations with full control:

```scala
import in.rcard.yaes.Flow

val expandedFlow = Flow(1, 2, 3)
  .transform { value =>
    Flow.emit(value)
    Flow.emit(value * 10)
  }

// Emits: 1, 10, 2, 20, 3, 30
```

### take

Limit the number of emitted values:

```scala
import in.rcard.yaes.Flow

val limitedFlow = Flow(1, 2, 3, 4, 5)
  .take(3)

// Emits: 1, 2, 3
```

### drop

Skip the first n values:

```scala
import in.rcard.yaes.Flow

val skippedFlow = Flow(1, 2, 3, 4, 5)
  .drop(2)

// Emits: 3, 4, 5
```

### onEach

Perform side effects without changing the flow:

```scala
import in.rcard.yaes.Flow
import scala.collection.mutable.ArrayBuffer

val sideEffects = ArrayBuffer[String]()

val monitoredFlow = Flow(1, 2, 3)
  .onEach { value =>
    sideEffects += s"Processing: $value"
  }
  .map(_ * 2)

// Side effects: ["Processing: 1", "Processing: 2", "Processing: 3"]
// Flow emits: 2, 4, 6
```

### onStart

Execute an action before flow collection starts:

```scala
import in.rcard.yaes.Flow

val initFlow = Flow(1, 2, 3)
  .onStart {
    Flow.emit(0) // Prepend 0 to the flow
  }

// Emits: 0, 1, 2, 3
```

## Terminal Operators

### fold

Accumulate values to a single result:

```scala
import in.rcard.yaes.Flow

val sum = Flow(1, 2, 3, 4, 5)
  .fold(0) { (acc, value) => acc + value }

// Result: 15

val product = Flow(1, 2, 3, 4)
  .fold(1) { (acc, value) => acc * value }

// Result: 24
```

### count

Count the number of emitted values:

```scala
import in.rcard.yaes.Flow

val count = Flow("a", "b", "c", "d")
  .filter(_.length > 0)
  .count()

// Result: 4
```

## Working with Binary Data and Text

Flow provides powerful capabilities for working with InputStreams and decoding binary data into text.

### Reading from InputStream

Use `fromInputStream` to create a flow from any InputStream:

```scala
import in.rcard.yaes.Flow
import java.io.FileInputStream
import scala.util.Using

Using(new FileInputStream("data.txt")) { inputStream =>
  val chunks = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
  
  Flow.fromInputStream(inputStream, bufferSize = 1024)
    .collect { chunk =>
      chunks += chunk
    }
}
```

The `bufferSize` parameter controls how much data is read at once. Larger buffers can improve performance for large files, while smaller buffers use less memory.

### Decoding UTF-8 Text

The `asUtf8String()` method correctly handles multi-byte UTF-8 character sequences that may be split across chunk boundaries:

```scala
import in.rcard.yaes.Flow
import java.io.FileInputStream
import scala.util.Using

Using(new FileInputStream("text.txt")) { inputStream =>
  val result = scala.collection.mutable.ArrayBuffer[String]()
  
  Flow.fromInputStream(inputStream, bufferSize = 1024)
    .asUtf8String()
    .collect { str =>
      result += str
    }
}
```

This is especially important when processing files that contain:
- Emoji characters (e.g., 😀, 🌍)
- Non-Latin scripts (e.g., 世界, العالم, Мир)
- Special symbols and mathematical notation

### Decoding with Custom Charsets

Use `asString()` to decode text with a specific charset:

```scala
import in.rcard.yaes.Flow
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

// Reading ISO-8859-1 encoded data
val data = "café".getBytes(StandardCharsets.ISO_8859_1)
val input = new ByteArrayInputStream(data)

val result = Flow.fromInputStream(input, bufferSize = 2)
  .asString(StandardCharsets.ISO_8859_1)
  .fold("")(_ + _)

// result contains: "café"
```

### Processing Large Text Files

Combine Flow operators to efficiently process large text files:

```scala
import in.rcard.yaes.Flow
import java.io.FileInputStream
import scala.util.Using

Using(new FileInputStream("large-file.txt")) { inputStream =>
  val lineCount = Flow.fromInputStream(inputStream, bufferSize = 8192)
    .asUtf8String()
    .fold(0) { (count, str) =>
      count + str.count(_ == '\n')
    }
  
  println(s"File contains $lineCount lines")
}
```

### Processing JSON from Network

```scala
import in.rcard.yaes.Flow
import java.net.URL
import scala.util.Using

val url = new URL("https://api.example.com/data.json")

Using(url.openStream()) { inputStream =>
  val json = Flow.fromInputStream(inputStream, bufferSize = 4096)
    .asUtf8String()
    .fold("")(_ + _)
  
  // Parse JSON string
  println(s"Received JSON: $json")
}
```

### Reading Binary Files with Text Processing

```scala
import in.rcard.yaes.Flow
import java.io.FileInputStream
import scala.util.Using

case class FileMetadata(totalBytes: Int, textContent: String)

Using(new FileInputStream("mixed-data.txt")) { inputStream =>
  var totalBytes = 0
  val textParts = scala.collection.mutable.ArrayBuffer[String]()
  
  Flow.fromInputStream(inputStream, bufferSize = 512)
    .onEach { chunk =>
      totalBytes += chunk.length
    }
    .asUtf8String()
    .collect { text =>
      textParts += text
    }
  
  FileMetadata(totalBytes, textParts.mkString)
}
```

## Encoding Strings to Bytes

Flow provides methods to encode strings into byte arrays with various character encodings.

### Encoding to UTF-8

Use `encodeToUtf8()` to convert strings to UTF-8 byte arrays:

```scala 3
import in.rcard.yaes.Flow
import java.nio.charset.StandardCharsets

val flow = Flow("Hello", "World", "!")

val result = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
flow
  .encodeToUtf8()
  .collect { bytes =>
    result += bytes
  }

// Each string is encoded separately as a byte array
// result.length == 3
```

This is particularly useful when you need to:
- Write text data to files or network streams
- Prepare data for HTTP requests or responses
- Serialize text data for storage or transmission
- Convert strings for binary protocols

### Encoding with Custom Charsets

Use `encodeTo()` to encode strings with a specific charset:

```scala 3
import in.rcard.yaes.Flow
import java.nio.charset.StandardCharsets

// Encoding with UTF-16
val flow = Flow("Hello", "世界")

val encoded = flow
  .encodeTo(StandardCharsets.UTF_16BE)
  .fold(Array.empty[Byte])(_ ++ _)

val decoded = new String(encoded, StandardCharsets.UTF_16BE)
// decoded == "Hello世界"
```

Supported charsets include:
- `StandardCharsets.UTF_8` - UTF-8 encoding (most common)
- `StandardCharsets.UTF_16` - UTF-16 encoding
- `StandardCharsets.UTF_16BE` - UTF-16 Big Endian
- `StandardCharsets.UTF_16LE` - UTF-16 Little Endian
- `StandardCharsets.ISO_8859_1` - ISO Latin-1
- `StandardCharsets.US_ASCII` - US ASCII

### Error Handling with Unmappable Characters

The encoder throws an `UnmappableCharacterException` if a character cannot be represented in the target charset:

```scala 3
import in.rcard.yaes.Flow
import java.nio.charset.StandardCharsets

// This will throw an exception because Chinese characters
// cannot be represented in US-ASCII
try {
  Flow("世界")
    .encodeTo(StandardCharsets.US_ASCII)
    .collect { _ => }
} catch {
  case e: java.nio.charset.UnmappableCharacterException =>
    println(s"Cannot encode: ${e.getMessage}")
}
```

This strict error handling ensures data integrity and prevents silent data corruption.

## Writing to OutputStreams

Flow provides the `toOutputStream` method to write byte arrays directly to an `OutputStream`, completing the I/O workflow alongside `fromInputStream`.

### Basic Usage

Write byte arrays from a flow to any `OutputStream`:

```scala 3
import in.rcard.yaes.Flow
import java.io.FileOutputStream
import scala.util.Using

// Write binary data to a file
val data = Array[Byte](1, 2, 3, 4, 5)
Using(new FileOutputStream("output.bin")) { outputStream =>
  Flow(data).toOutputStream(outputStream)
}
```

### Writing Encoded Text

Combine string encoding with `toOutputStream` to write text files:

```scala 3
import in.rcard.yaes.Flow
import java.io.FileOutputStream
import scala.util.Using

val lines = List(
  "First line",
  "Second line",
  "Third line with Unicode: 世界 😀"
)

Using(new FileOutputStream("output.txt")) { outputStream =>
  lines.asFlow()
    .map(_ + "\n") // Add newlines
    .encodeToUtf8()
    .toOutputStream(outputStream)
}
```

### Key Characteristics

- **Terminal operator**: Returns `Unit` and processes all flow elements
- **Skips empty arrays**: Empty byte arrays are not written to the stream
- **Single flush**: Flushes the stream once after all data is written
- **No auto-close**: Caller is responsible for closing the stream (consistent with `fromInputStream`)
- **Exception propagation**: Any `IOException` from write or flush operations is propagated to the caller

### Round-trip Encoding and Decoding

Combine encoding, writing, reading, and decoding for complete round-trip operations:

```scala 3
import in.rcard.yaes.Flow
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets

val originalText = "Hello 世界! 😀"

// Encode and write to output stream
val output = new ByteArrayOutputStream()
Flow(originalText)
  .encodeToUtf8()
  .toOutputStream(output)

// Read back and decode
val input = new ByteArrayInputStream(output.toByteArray)
val decoded = Flow.fromInputStream(input)
  .asUtf8String()
  .fold("")(_ + _)

// Verify round-trip
assert(decoded == originalText)
```

### Practical Example: Log File Writer

```scala 3
import in.rcard.yaes.Flow
import java.io.{FileOutputStream, BufferedOutputStream}
import scala.util.Using
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

case class LogEntry(timestamp: LocalDateTime, level: String, message: String)

def writeLogFile(entries: List[LogEntry], filename: String): Unit = {
  val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
  
  Using(new BufferedOutputStream(new FileOutputStream(filename))) { output =>
    entries.asFlow()
      .map { entry =>
        val timestamp = entry.timestamp.format(formatter)
        s"[$timestamp] ${entry.level}: ${entry.message}\n"
      }
      .encodeToUtf8()
      .toOutputStream(output)
  }
}

// Usage
val logs = List(
  LogEntry(LocalDateTime.now(), "INFO", "Application started"),
  LogEntry(LocalDateTime.now(), "DEBUG", "Processing data"),
  LogEntry(LocalDateTime.now(), "ERROR", "Connection failed")
)

writeLogFile(logs, "app.log")
```

## Processing Text Line by Line

Flow provides methods to split byte streams into lines, making it easy to process text files and data streams line by line. This is essential for working with structured text data like CSV files, log files, and configuration files.

### Reading Lines from Files

Use `linesInUtf8()` to read UTF-8 encoded text files line by line:

```scala 3
import in.rcard.yaes.Flow
import java.io.FileInputStream
import scala.util.Using

Using(new FileInputStream("data.txt")) { inputStream =>
  val lines = scala.collection.mutable.ArrayBuffer[String]()
  
  Flow.fromInputStream(inputStream, bufferSize = 1024)
    .linesInUtf8()
    .collect { line =>
      lines += line
    }
  // lines contains all lines from the file
}
```

### Reading Lines with Custom Encoding

Use `linesIn()` to handle files with different character encodings:

```scala 3
import in.rcard.yaes.Flow
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import scala.util.Using

// Read ISO-8859-1 encoded file
Using(new FileInputStream("legacy-data.txt")) { inputStream =>
  Flow.fromInputStream(inputStream)
    .linesIn(StandardCharsets.ISO_8859_1)
    .collect { line =>
      println(line)
    }
}

// Read UTF-16 encoded file
Using(new FileInputStream("utf16-data.txt")) { inputStream =>
  Flow.fromInputStream(inputStream)
    .linesIn(StandardCharsets.UTF_16)
    .collect { line =>
      println(line)
    }
}
```

### Key Characteristics

- **Universal line separator support**: Recognizes `\n` (LF), `\r\n` (CRLF), and `\r` (CR)
- **Clean output**: Line separators are removed from emitted strings
- **Empty line preservation**: Empty lines are maintained in the output
- **Last line handling**: Emits the last line even without a trailing separator
- **Chunk boundary safety**: Correctly handles multi-byte characters and CRLF sequences split across chunk boundaries
- **Strict error handling**: Throws `java.nio.charset.MalformedInputException` or `java.nio.charset.UnmappableCharacterException` on invalid input

## Practical Examples

### Data Processing Pipeline

```scala
import in.rcard.yaes.Flow
import scala.collection.mutable.ArrayBuffer

case class User(id: Int, name: String, age: Int)

val users = List(
  User(1, "Alice", 25),
  User(2, "Bob", 17),
  User(3, "Charlie", 30),
  User(4, "Diana", 16)
)

val results = ArrayBuffer[String]()

users.asFlow()
  .filter(_.age >= 18) // Only adults
  .map(user => s"${user.name} (${user.age})")
  .onEach(user => println(s"Processing: $user"))
  .collect { userInfo =>
    results += userInfo
  }

// Results: ["Alice (25)", "Charlie (30)"]
```

### Async Data Transformation

```scala
import in.rcard.yaes.Flow
import in.rcard.yaes.Async.*

def processDataAsync(data: List[Int])(using Async): List[String] = {
  val results = scala.collection.mutable.ArrayBuffer[String]()
  
  data.asFlow()
    .map { value =>
      // Simulate async processing
      Async.delay(100.millis)
      s"Processed: $value"
    }
    .collect { result =>
      results += result
    }
  
  results.toList
}

val result = Async.run {
  processDataAsync(List(1, 2, 3, 4, 5))
}
```

### Event Stream Processing

```scala
import in.rcard.yaes.Flow
import in.rcard.yaes.Log.*

sealed trait Event
case class UserLogin(userId: Int, timestamp: Long) extends Event
case class UserLogout(userId: Int, timestamp: Long) extends Event
case class PageView(userId: Int, page: String) extends Event

def processEvents(events: List[Event])(using Log): Map[Int, Int] = {
  val logger = Log.getLogger("EventProcessor")
  val userPageViews = scala.collection.mutable.Map[Int, Int]()
  
  events.asFlow()
    .onStart {
      logger.info("Starting event processing")
    }
    .filter {
      case _: PageView => true
      case _ => false
    }
    .map(_.asInstanceOf[PageView])
    .onEach { pageView =>
      logger.debug(s"Processing page view: ${pageView.page} for user ${pageView.userId}")
    }
    .collect { pageView =>
      userPageViews.updateWith(pageView.userId) {
        case Some(count) => Some(count + 1)
        case None => Some(1)
      }
    }
  
  logger.info(s"Processed ${userPageViews.values.sum} page views")
  userPageViews.toMap
}
```

### Complex Data Pipeline

```scala
import in.rcard.yaes.Flow
import in.rcard.yaes.Raise.*

case class RawData(value: String)
case class ParsedData(number: Int)
case class ProcessedData(result: String)

def dataProcessingPipeline(
  rawData: List[RawData]
)(using Raise[String]): List[ProcessedData] = {
  val results = scala.collection.mutable.ArrayBuffer[ProcessedData]()
  
  rawData.asFlow()
    .transform { raw =>
      // Parse with error handling
      try {
        val number = raw.value.toInt
        Flow.emit(ParsedData(number))
      } catch {
        case _: NumberFormatException =>
          Raise.raise(s"Invalid number format: ${raw.value}")
      }
    }
    .filter(_.number > 0) // Only positive numbers
    .map { parsed =>
      ProcessedData(s"Result: ${parsed.number * 2}")
    }
    .take(10) // Limit output
    .collect { processed =>
      results += processed
    }
  
  results.toList
}

// Usage
val input = List(RawData("1"), RawData("2"), RawData("invalid"), RawData("3"))

val result = Raise.either {
  dataProcessingPipeline(input)
}

result match {
  case Right(data) => println(s"Processed: $data")
  case Left(error) => println(s"Error: $error")
}
```

## Integration with λÆS Effects

Flow works seamlessly with λÆS effects:

```scala
import in.rcard.yaes.Flow
import in.rcard.yaes.Random.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Log.*

def randomDataProcessor(using Random, Output, Log): List[Int] = {
  val logger = Log.getLogger("RandomProcessor")
  val results = scala.collection.mutable.ArrayBuffer[Int]()
  
  // Generate random data flow
  val randomFlow = Flow.flow[Int] {
    for (i <- 1 to 10) {
      val randomValue = Random.nextInt(100)
      Flow.emit(randomValue)
    }
  }
  
  randomFlow
    .onStart {
      logger.info("Starting random data processing")
      Output.printLn("Processing random data...")
    }
    .filter(_ > 50) // Only values > 50
    .onEach { value =>
      Output.printLn(s"Processing value: $value")
    }
    .map(_ * 2)
    .collect { result =>
      results += result
    }
  
  logger.info(s"Processed ${results.length} values")
  results.toList
}

// Run with effects
val result = Log.run {
  Output.run {
    Random.run {
      randomDataProcessor
    }
  }
}
```

## Best Practices

### 1. Use Appropriate Operators

Choose the right operator for your use case:
- Use `map` for simple 1:1 transformations
- Use `transform` for complex or 1:many transformations
- Use `filter` for selective processing
- Use `fold` for aggregation

### 2. Handle Side Effects Properly

Use `onEach` for side effects that don't change the flow:

```scala
import in.rcard.yaes.Flow

Flow(1, 2, 3)
  .onEach(value => println(s"Debug: $value")) // Side effect
  .map(_ * 2) // Transformation
```

### 3. Combine with Error Handling

Use Flow with the `Raise` effect for robust error handling:

```scala
import in.rcard.yaes.Flow
import in.rcard.yaes.Raise.*

def safeProcessing(data: List[String])(using Raise[String]): List[Int] = {
  val results = scala.collection.mutable.ArrayBuffer[Int]()
  
  data.asFlow()
    .transform { str =>
      try {
        Flow.emit(str.toInt)
      } catch {
        case _: NumberFormatException =>
          Raise.raise(s"Invalid number: $str")
      }
    }
    .collect { number =>
      results += number
    }
  
  results.toList
}
```

### 4. Performance Considerations

- Flows are cold - they don't do work until collected
- Chain operators efficiently - they're composed, not executed immediately
- Use `take` to limit processing when appropriate
- Consider memory usage when collecting large flows

## Future Enhancements

The `yaes-data` module is actively developed. Future versions may include:

- More data structures (immutable collections, persistent data structures)
- Parallel flow processing
- Integration with more λÆS effects
- Performance optimizations
- Additional operators and combinators

## Contributing

Contributions to the λÆS Data module are welcome! Areas where help is needed:

- Additional data structures
- Performance improvements
- More operators and combinators
- Documentation and examples
- Test coverage

See the [contributing guide](../contributing.html) for more information.
