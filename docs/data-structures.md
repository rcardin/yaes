# λÆS Data Structures

The `yaes-data` module provides a collection of functional data structures that complement the λÆS effects system. These data structures are designed to work seamlessly with λÆS effects and provide efficient, functional alternatives to traditional imperative data structures.

![Maven Central](https://img.shields.io/maven-central/v/in.rcard.yaes/yaes-data_3)
[![javadoc](https://javadoc.io/badge2/in.rcard.yaes/yaes-data_3/javadoc.svg)](https://javadoc.io/doc/in.rcard.yaes/yaes-data_3)

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-data" % "0.11.0"
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

// Copy file using toFile (recommended - automatic resource management)
Flow.fromFile(Paths.get("source.txt"))
  .toFile(Paths.get("copy.txt"))

// Or copy file using toOutputStream (manual resource management)
import java.nio.file.Files
import scala.util.Using

val destPath = Paths.get("copy2.txt")
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

```scala
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

```scala
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

```scala
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

```scala
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

```scala
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

```scala
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

```scala
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

## Writing to Files

Flow provides the `toFile` method to write byte arrays directly to files with automatic resource management, complementing the `fromFile` method.

### Basic Usage

Write byte arrays from a flow to a file:

```scala
import in.rcard.yaes.Flow
import java.nio.file.Paths

// Write binary data
val data = Array[Byte](1, 2, 3, 4, 5)
Flow(data).toFile(Paths.get("output.bin"))

// Write text data
val text = "Hello, World!"
Flow(text.getBytes("UTF-8"))
  .toFile(Paths.get("output.txt"))
```

### Writing Encoded Text

Combine string encoding with `toFile` for convenient text file writing:

```scala
import in.rcard.yaes.Flow
import java.nio.file.Paths

val lines = List(
  "First line",
  "Second line",
  "Third line with Unicode: 世界 😀"
)

lines.asFlow()
  .map(_ + "\n") // Add newlines
  .encodeToUtf8()
  .toFile(Paths.get("output.txt"))
```

### Key Characteristics

- **Terminal operator**: Returns `Unit` and processes all flow elements
- **Automatic resource management**: Opens and closes the OutputStream automatically
- **Creates directories**: Parent directories are created if they don't exist
- **Overwrites files**: Existing files are overwritten (use with caution)
- **Skips empty arrays**: Empty byte arrays are not written to the file
- **Exception handling**: Throws `IOException` with file path context on errors

## Processing Text Line by Line

Flow provides methods to split byte streams into lines, making it easy to process text files and data streams line by line. This is essential for working with structured text data like CSV files, log files, and configuration files.

### Reading Lines from Files

Use `linesInUtf8()` to read UTF-8 encoded text files line by line:

```scala
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

```scala
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

## Reactive Streams Integration

λÆS provides seamless integration with Java Reactive Streams through the `FlowPublisher` class. This powerful bridge allows you to convert YAES `Flow[A]` instances into standard `java.util.concurrent.Flow.Publisher[A]` instances that can be consumed by any Reactive Streams-compliant library.

### Introduction

**What is FlowPublisher?**

`FlowPublisher` is a bridge that converts YAES's push-based cold streams (`Flow`) into the pull-based, backpressure-enabled world of Reactive Streams (`Publisher`). This enables seamless interoperability with popular reactive libraries and frameworks.

**Use Cases:**

- **Framework Integration**: Integrate YAES flows with Akka Streams, Project Reactor, RxJava, and other reactive libraries
- **Backpressure Control**: Leverage Reactive Streams' demand-driven backpressure for fine-grained flow control
- **Standard Compliance**: Expose YAES flows through the standard `java.util.concurrent.Flow.Publisher` interface
- **Library Interop**: Make YAES flows consumable by any Reactive Streams-compliant consumer

**Key Benefit:**

The primary benefit is interoperability. Once a Flow is converted to a Publisher, it can participate in any reactive ecosystem that supports the Reactive Streams specification, enabling YAES to integrate with existing reactive codebases and libraries.

### Key Characteristics

FlowPublisher maintains several important characteristics that ensure correct behavior and performance:

#### Cold Execution

Like YAES Flows, FlowPublisher maintains cold execution semantics:

- Nothing happens until `subscribe()` is called
- When `subscribe()` is called, the Flow immediately starts executing in a collector fiber
- Each subscription triggers a fresh, independent execution of the Flow
- Multiple subscribers get independent executions with separate state
- The collector produces into a channel buffer right away (not waiting for `request()`)

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val flow = Flow.flow[Int] {
  println("Flow execution started")
  Flow.emit(1)
  Flow.emit(2)
  Flow.emit(3)
}

Async.run {
  val publisher = FlowPublisher.fromFlow(flow)

  // Nothing printed yet - no subscriptions

  publisher.subscribe(subscriber1)  // Prints "Flow execution started" immediately
  publisher.subscribe(subscriber2)  // Prints "Flow execution started" again (fresh execution)
}
```

#### Demand-Driven Backpressure

FlowPublisher respects the Reactive Streams backpressure protocol:

- Elements are only delivered when the subscriber requests them via `request(n)`
- The publisher tracks outstanding demand and only emits when demand is available
- This prevents overwhelming slow consumers with data they can't process

```scala 3
publisher.subscribe(new Subscriber[Int] {
  override def onSubscribe(s: Subscription): Unit = {
    s.request(5)  // Request exactly 5 elements
  }

  override def onNext(item: Int): Unit = {
    println(item)
    // No more elements until request() is called again
  }
  // ...
})
```

#### Reactive Streams Specification Compliance

FlowPublisher is fully compliant with the Reactive Streams specification, implementing all required rules:

- **Rule 1.1**: Total elements delivered ≤ total requested via `request(n)`
- **Rule 1.3**: `onNext`, `onError`, `onComplete` are called serially (never concurrently)
- **Rule 1.7**: No signals are sent after cancellation
- **Rule 1.9**: Subscriber must not be null (enforced with precondition check)
- **Rule 2.13**: No null elements (detected and reported as `NullPointerException`)
- **Rule 3.9**: `request(n)` must be positive (negative/zero values trigger `onError`)

These rules ensure that FlowPublisher can interoperate correctly with any Reactive Streams-compliant consumer.

#### Buffered Architecture

FlowPublisher uses a Channel internally to buffer elements between the Flow producer and the Subscriber consumer:

- Absorbs temporary mismatches between producer and consumer speeds
- Configurable buffer size and overflow strategies
- Default: Bounded buffer of 16 elements with SUSPEND strategy (backpressure)

```scala 3
// Default: Bounded(16, SUSPEND)
val publisher1 = flow.asPublisher()

// Custom: Large buffer for high-throughput scenarios
val publisher2 = flow.asPublisher(
  Channel.Type.Bounded(256, Channel.OverflowStrategy.SUSPEND)
)

// Unbounded buffer (use with caution - can grow without limit)
val publisher3 = flow.asPublisher(Channel.Type.Unbounded)
```

#### Cancellable

FlowPublisher supports proper cancellation with resource cleanup:

- Subscribers can cancel their subscription at any time
- Cancellation stops both producer and consumer fibers
- Channel is cancelled, preventing further emissions
- Idempotent: calling `cancel()` multiple times is safe
- No signals are sent to the subscriber after cancellation

```scala 3
publisher.subscribe(new Subscriber[Int] {
  override def onNext(item: Int): Unit = {
    if (shouldStop(item)) {
      subscription.cancel()  // Stop receiving elements
    }
  }
  // ...
})
```

### Creating Publishers

FlowPublisher provides multiple ways to create Publishers from Flows, with varying levels of customization.

#### Basic Creation with Factory Method

The most straightforward way to create a Publisher is using the `fromFlow` factory method:

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val flow = Flow(1, 2, 3, 4, 5)

Async.run {
  val publisher = FlowPublisher.fromFlow(flow)

  // Publisher ready to be subscribed to
  publisher.subscribe(subscriber)
}
```

This creates a Publisher with default settings:
- Buffer capacity: Bounded(16, SUSPEND)
- Cold execution: Flow starts on subscription
- Fully spec-compliant: Implements all Reactive Streams rules

#### Extension Method Syntax

For more concise code, use the `asPublisher()` extension method:

```scala 3
import in.rcard.yaes.FlowPublisher.asPublisher
import in.rcard.yaes.Async.*

val flow = Flow(1, 2, 3, 4, 5)

Async.run {
  val publisher = flow.asPublisher()  // Extension method
  publisher.subscribe(subscriber)
}
```

The extension method is syntactically cleaner and feels more natural when working with Flows.

#### With Custom Buffer Configuration

Both creation methods support custom buffer configuration:

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher, Channel}
import in.rcard.yaes.FlowPublisher.asPublisher

val flow = Flow(1 to 1000: _*)

// Using factory method
val publisher1 = FlowPublisher.fromFlow(
  flow,
  Channel.Type.Bounded(64, Channel.OverflowStrategy.SUSPEND)
)

// Using extension method
val publisher2 = flow.asPublisher(
  bufferCapacity = Channel.Type.Bounded(64, Channel.OverflowStrategy.SUSPEND)
)
```

Custom buffer configuration is useful for:
- High-throughput scenarios (larger buffers)
- Memory-constrained environments (smaller buffers)
- Different overflow strategies (DROP_OLDEST, DROP_LATEST)

#### Complete Example with All Options

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher, Channel}
import in.rcard.yaes.FlowPublisher.asPublisher
import in.rcard.yaes.Async.*
import java.util.concurrent.Flow.{Subscriber, Subscription}

val flow = Flow.flow[String] {
  (1 to 100).foreach { i =>
    Flow.emit(s"Message $i")
  }
}

Async.run {
  // Create publisher with custom configuration
  val publisher = flow.asPublisher(
    bufferCapacity = Channel.Type.Bounded(
      capacity = 32,
      overflowStrategy = Channel.OverflowStrategy.SUSPEND
    )
  )

  // Subscribe with a custom subscriber
  publisher.subscribe(new Subscriber[String] {
    var subscription: Subscription = _

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(10)  // Initial request
    }

    override def onNext(item: String): Unit = {
      println(item)
      subscription.request(1)  // Request next item
    }

    override def onError(t: Throwable): Unit = {
      println(s"Error: ${t.getMessage}")
    }

    override def onComplete(): Unit = {
      println("Stream completed")
    }
  })
}
```

### Buffer Configuration

The buffer configuration is a critical aspect of FlowPublisher's performance and behavior. Understanding the trade-offs helps you choose the right configuration for your use case.

#### Buffer Types

FlowPublisher supports all Channel buffer types:

**1. Unbounded**

```scala 3
val publisher = flow.asPublisher(Channel.Type.Unbounded)
```

- **Pros**: Producer never blocks, maximum throughput
- **Cons**: Can grow without limit, memory risk
- **Use when**: Producer must never wait, memory is abundant, short-lived streams

**2. Bounded with SUSPEND (Default)**

```scala 3
val publisher = flow.asPublisher(
  Channel.Type.Bounded(16, Channel.OverflowStrategy.SUSPEND)
)
```

- **Pros**: Bounded memory, true backpressure
- **Cons**: Producer may block when buffer is full
- **Use when**: Backpressure is required, memory is limited, consumer pace matters

**3. Bounded with DROP_OLDEST**

```scala 3
val publisher = flow.asPublisher(
  Channel.Type.Bounded(16, Channel.OverflowStrategy.DROP_OLDEST)
)
```

- **Pros**: Producer never blocks, bounded memory
- **Cons**: May lose oldest data when buffer overflows
- **Use when**: Latest data is most important, some loss is acceptable (e.g., live metrics)

**4. Bounded with DROP_LATEST**

```scala 3
val publisher = flow.asPublisher(
  Channel.Type.Bounded(16, Channel.OverflowStrategy.DROP_LATEST)
)
```

- **Pros**: Producer never blocks, bounded memory
- **Cons**: May lose newest data when buffer overflows
- **Use when**: Historical data is most important, some loss is acceptable

#### Default Configuration

The default configuration is designed for balanced performance:

```scala 3
Channel.Type.Bounded(16, Channel.OverflowStrategy.SUSPEND)
```

This provides:
- Moderate memory footprint: 16 elements × element size
- True backpressure: Producer suspends when full
- Reasonable throughput: Enough buffering to absorb short bursts

For most use cases, the default is a good starting point.

#### Choosing Buffer Size

Buffer size selection depends on several factors:

**Small Buffers (8-16 elements)**

```scala 3
val publisher = flow.asPublisher(Channel.Type.Bounded(8))
```

- **Pros**: Low memory usage, tight coordination
- **Cons**: More frequent backpressure events, potential throughput impact
- **Use when**: Memory is constrained, elements are large, tight control needed

**Medium Buffers (32-128 elements)**

```scala 3
val publisher = flow.asPublisher(Channel.Type.Bounded(64))
```

- **Pros**: Balanced memory and throughput, absorbs bursts well
- **Cons**: Moderate memory usage
- **Use when**: General-purpose streaming, balanced workloads

**Large Buffers (256+ elements)**

```scala 3
val publisher = flow.asPublisher(Channel.Type.Bounded(256))
```

- **Pros**: High throughput, fewer backpressure events
- **Cons**: Higher memory usage, longer latency for backpressure to take effect
- **Use when**: High-throughput scenarios, small elements, abundant memory

**Rule of Thumb:**

Start with the default (16) and adjust based on profiling:
- If producer frequently blocks → increase buffer size
- If memory is an issue → decrease buffer size
- If losing data is okay → use DROP strategies

#### Buffer Strategy Selection Guide

| Scenario | Recommended Strategy | Reasoning |
|----------|---------------------|-----------|
| Database query results | Bounded + SUSPEND | Backpressure prevents overwhelming consumer |
| Live sensor data | Bounded + DROP_OLDEST | Latest readings most valuable |
| Event replay | Bounded + SUSPEND | All events must be processed |
| Real-time metrics | Bounded + DROP_LATEST | Keep historical baseline |
| Batch processing | Large Bounded + SUSPEND | Maximize throughput with backpressure |
| Memory-constrained | Small Bounded + SUSPEND | Minimize memory footprint |

### Subscriber Implementation

Implementing a Subscriber correctly is crucial for proper interaction with FlowPublisher. This section covers common patterns and best practices.

#### Basic Subscriber Pattern

The most basic subscriber implements all four methods:

```scala 3
import java.util.concurrent.Flow.{Subscriber, Subscription}

publisher.subscribe(new Subscriber[Int] {
  var subscription: Subscription = _

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    s.request(Long.MaxValue)  // Request all elements
  }

  override def onNext(item: Int): Unit = {
    println(s"Received: $item")
  }

  override def onError(t: Throwable): Unit = {
    println(s"Error: ${t.getMessage}")
  }

  override def onComplete(): Unit = {
    println("Stream completed successfully")
  }
})
```

**Key Points:**
- Store the subscription for later `request()` and `cancel()` calls
- Must call `request(n)` at least once to receive elements
- `onError` and `onComplete` are mutually exclusive (exactly one will be called)

#### Demand Management Patterns

Different patterns for managing demand provide different trade-offs:

**Pattern 1: Request All Upfront**

```scala 3
override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  s.request(Long.MaxValue)  // Request unlimited elements
}

override def onNext(item: Int): Unit = {
  process(item)
  // No additional request() needed - already requested all
}
```

- **Pros**: Simple, maximum throughput
- **Cons**: No backpressure, can overwhelm slow consumers
- **Use when**: Fast consumer, small dataset, backpressure not needed

**Pattern 2: Request in Batches**

```scala 3
val BATCH_SIZE = 10
var received = 0

override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  s.request(BATCH_SIZE)  // Request first batch
}

override def onNext(item: Int): Unit = {
  process(item)
  received += 1

  if (received % BATCH_SIZE == 0) {
    subscription.request(BATCH_SIZE)  // Request next batch
  }
}
```

- **Pros**: Balanced control, good throughput, manageable backpressure
- **Cons**: Slightly more complex logic
- **Use when**: Moderate control needed, processing in chunks makes sense

**Pattern 3: Request One at a Time**

```scala 3
override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  s.request(1)  // Request first element
}

override def onNext(item: Int): Unit = {
  process(item)
  subscription.request(1)  // Request next element
}
```

- **Pros**: Maximum control, finest-grained backpressure
- **Cons**: More overhead, potential throughput impact
- **Use when**: Expensive processing, strict rate limiting needed

**Pattern 4: Adaptive Requesting**

```scala 3
var outstandingDemand = 0
val LOW_WATERMARK = 5
val HIGH_WATERMARK = 20

override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  outstandingDemand = HIGH_WATERMARK
  s.request(HIGH_WATERMARK)
}

override def onNext(item: Int): Unit = {
  process(item)
  outstandingDemand -= 1

  // Refill when hitting low watermark
  if (outstandingDemand <= LOW_WATERMARK) {
    val toRequest = HIGH_WATERMARK - outstandingDemand
    subscription.request(toRequest)
    outstandingDemand = HIGH_WATERMARK
  }
}
```

- **Pros**: Optimal balance of throughput and control
- **Cons**: Most complex pattern
- **Use when**: High-performance scenarios with variable processing times

### Backpressure Handling

Backpressure is a key feature of Reactive Streams, allowing slow consumers to control the rate at which they receive data. FlowPublisher implements comprehensive backpressure support.

#### How Backpressure Works

The backpressure mechanism in FlowPublisher follows this flow:

1. **Subscriber signals demand**: Calls `subscription.request(n)` to indicate it can handle `n` elements
2. **Publisher tracks demand**: Maintains an atomic counter of outstanding demand
3. **Emitter waits for demand**: If demand is zero, emitter fiber waits (using semaphore)
4. **Elements delivered on demand**: When demand > 0, elements are delivered and demand decremented
5. **Buffer absorbs mismatches**: Channel buffer handles temporary speed differences
6. **Producer suspends when full**: With SUSPEND strategy, producer waits when buffer is full

This creates a complete feedback loop from consumer → subscriber → publisher → producer.

#### Example: Slow Consumer with Backpressure

```scala 3
import in.rcard.yaes.{Flow, Channel}
import in.rcard.yaes.FlowPublisher.asPublisher
import in.rcard.yaes.Async.*
import scala.concurrent.duration.*

val fastFlow = Flow(1 to 1000: _*)  // Fast producer: 1000 elements

Async.run {
  val publisher = fastFlow.asPublisher(
    Channel.Type.Bounded(10, Channel.OverflowStrategy.SUSPEND)
  )

  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(1)  // Request one element at a time
    }

    override def onNext(item: Int): Unit = {
      // Simulate slow processing (100ms per item)
      Thread.sleep(100)
      println(s"Processed: $item")

      subscription.request(1)  // Request next element
    }

    override def onError(t: Throwable): Unit = {
      println(s"Error: ${t.getMessage}")
    }

    override def onComplete(): Unit = {
      println("Processing complete")
    }
  })
}
```

In this example:
- Producer can emit 1000 elements instantly
- Consumer processes at 10 elements/second (100ms each)
- Buffer size is only 10 elements
- **Result**: Producer is throttled by consumer's pace through backpressure
  - Buffer fills with 10 elements
  - Producer suspends (SUSPEND strategy)
  - Consumer drains buffer at its own pace
  - Producer resumes when space available

#### Backpressure with Bounded Buffer

The buffer size directly impacts backpressure behavior:

**Small Buffer (Tight Backpressure)**

```scala 3
val publisher = flow.asPublisher(
  Channel.Type.Bounded(5, Channel.OverflowStrategy.SUSPEND)
)
```

- Producer feels backpressure quickly
- Tight coupling between producer and consumer speeds
- More frequent suspension/resumption cycles
- Good for memory-constrained environments

**Large Buffer (Relaxed Backpressure)**

```scala 3
val publisher = flow.asPublisher(
  Channel.Type.Bounded(100, Channel.OverflowStrategy.SUSPEND)
)
```

- Producer can run ahead of consumer
- Absorbs longer bursts without blocking
- Fewer suspension/resumption cycles
- Better throughput, higher memory usage

#### Backpressure with DROP Strategies

DROP strategies provide lossy backpressure:

**DROP_OLDEST (Keep Latest Data)**

```scala 3
import scala.concurrent.duration.*

val sensorReadings = Flow.flow[Double] {
  (1 to 1000).foreach { i =>
    Flow.emit(i.toDouble)
    Async.delay(10.millis)  // 100 readings/second
  }
}

Async.run {
  val publisher = sensorReadings.asPublisher(
    Channel.Type.Bounded(10, Channel.OverflowStrategy.DROP_OLDEST)
  )

  publisher.subscribe(new Subscriber[Double] {
    override def onNext(reading: Double): Unit = {
      Thread.sleep(100)  // Slow consumer: 10 readings/second
      println(s"Current reading: $reading")
      subscription.request(1)
    }
    // ...
  })
}
```

- Producer emits 100 readings/second
- Consumer processes 10 readings/second
- **Result**: Consumer sees latest readings, oldest are dropped
- Use case: Real-time monitoring where current state matters most

**DROP_LATEST (Keep Historical Data)**

```scala 3
val publisher = events.asPublisher(
  Channel.Type.Bounded(10, Channel.OverflowStrategy.DROP_LATEST)
)
```

- New events are dropped when buffer is full
- Consumer sees oldest buffered events first
- Use case: Event replay where historical order matters

#### Monitoring Backpressure

You can instrument your subscriber to observe backpressure behavior:

```scala 3
publisher.subscribe(new Subscriber[Int] {
  var subscription: Subscription = _
  var totalReceived = 0
  var totalRequested = 0
  val startTime = System.currentTimeMillis()

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    totalRequested = 10
    s.request(10)
  }

  override def onNext(item: Int): Unit = {
    totalReceived += 1
    val elapsed = System.currentTimeMillis() - startTime
    val rate = totalReceived.toDouble / elapsed * 1000

    println(f"Received: $item, Rate: $rate%.2f items/sec, " +
            f"Outstanding demand: ${totalRequested - totalReceived}")

    process(item)

    // Request more when demand is low
    if (totalRequested - totalReceived < 5) {
      subscription.request(10)
      totalRequested += 10
    }
  }
  // ...
})
```

This pattern helps you understand:
- Current throughput rate
- Outstanding demand levels
- Whether backpressure is being applied

### Error Handling

FlowPublisher provides robust error handling that integrates with both YAES error management and Reactive Streams error protocols.

#### Flow Errors

Exceptions during Flow collection are propagated to the subscriber's `onError` callback:

```scala 3
import in.rcard.yaes.Flow
import in.rcard.yaes.FlowPublisher.asPublisher
import in.rcard.yaes.Async.*

val errorFlow = Flow.flow[Int] {
  Flow.emit(1)
  Flow.emit(2)
  throw new RuntimeException("Flow processing error")
  Flow.emit(3)  // Never emitted
}

Async.run {
  val publisher = errorFlow.asPublisher()

  publisher.subscribe(new Subscriber[Int] {
    override def onError(t: Throwable): Unit = {
      // Called with the RuntimeException
      println(s"Flow error: ${t.getMessage}")
      // t.getMessage == "Flow processing error"
    }
    // ...
  })
}
```

**Key Behaviors:**
- Errors propagate only if subscription is not cancelled
- Only one terminal event is sent (either `onError` or `onComplete`, never both)
- After `onError`, no more signals are sent
- The `terminated` flag prevents duplicate error callbacks

#### Subscriber Errors

Exceptions thrown from the subscriber's `onNext` method are caught and reported:

```scala 3
val flow = Flow(1, 2, 3, 4, 5)

Async.run {
  val publisher = flow.asPublisher()

  publisher.subscribe(new Subscriber[Int] {
    override def onNext(item: Int): Unit = {
      println(s"Processing: $item")

      if (item == 3) {
        throw new IllegalStateException("Cannot process 3")
      }
    }

    override def onError(t: Throwable): Unit = {
      // Called with IllegalStateException
      println(s"Subscriber error: ${t.getMessage}")
    }
    // ...
  })
}
```

**Behavior:**
- Elements 1 and 2 are processed normally
- Element 3 triggers exception
- `onError` is called with the exception
- Stream terminates (elements 4 and 5 are not delivered)

#### Null Element Handling

Reactive Streams specification prohibits null elements. FlowPublisher detects and rejects them:

```scala 3
val values: List[String] = List("valid", null, "unreachable")
val flow = Flow(values: _*)

Async.run {
  val publisher = FlowPublisher.fromFlow(flow)

  publisher.subscribe(new Subscriber[String] {
    val received = mutable.ArrayBuffer[String]()

    override def onNext(item: String): Unit = {
      received += item
      subscription.request(1)
    }

    override def onError(t: Throwable): Unit = {
      // Called with NullPointerException
      assert(t.isInstanceOf[NullPointerException])
      assert(t.getMessage == "Flow emitted null element")
      assert(received.toList == List("valid"))
    }
    // ...
  })
}
```

**Key Points:**
- Null detection happens before calling `onNext`
- Subscriber receives all elements up to the null
- `NullPointerException` is raised with descriptive message
- Stream terminates after null detection

#### Invalid Request Handling

Reactive Streams Rule 3.9 requires `request(n)` to have positive argument. FlowPublisher enforces this:

```scala 3
val flow = Flow(1, 2, 3)

Async.run {
  val publisher = flow.asPublisher()

  publisher.subscribe(new Subscriber[Int] {
    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(0)  // INVALID: must be > 0
    }

    override def onError(t: Throwable): Unit = {
      // Called with IllegalArgumentException
      assert(t.isInstanceOf[IllegalArgumentException])
      assert(t.getMessage.contains("Rule 3.9"))
      assert(t.getMessage.contains("must be > 0"))
    }
    // ...
  })
}
```

**Behavior:**
- Invalid `request(n)` triggers immediate error callback
- Subscription is automatically cancelled
- No elements are delivered

#### Error Handling Best Practices

**1. Always Implement onError**

```scala 3
// ✅ GOOD: Handle errors appropriately
override def onError(t: Throwable): Unit = {
  t match {
    case _: IOException =>
      logger.error("Network error, will retry", t)
      scheduleRetry()
    case _: NullPointerException =>
      logger.error("Received null element", t)
    case _ =>
      logger.error("Unexpected error", t)
  }
}

// ❌ BAD: Empty error handler
override def onError(t: Throwable): Unit = {}
```

**2. Don't Throw from Error Handlers**

```scala 3
// ✅ GOOD: Catch and log
override def onError(t: Throwable): Unit = {
  try {
    cleanup()
    notifyFailure(t)
  } catch {
    case e: Exception =>
      logger.error("Error during error handling", e)
  }
}

// ❌ BAD: Throwing from onError
override def onError(t: Throwable): Unit = {
  throw new RuntimeException("Error handler failed")
}
```

**3. Handle Terminal Events Appropriately**

```scala 3
override def onError(t: Throwable): Unit = {
  logger.error("Stream failed", t)
  cleanupResources()
  notifyFailure(t)
}

override def onComplete(): Unit = {
  logger.info("Stream completed successfully")
  cleanupResources()
  notifySuccess()
}
```

#### Error Recovery Patterns

**Pattern 1: Retry on Failure**

```scala 3
def subscribeWithRetry(publisher: Publisher[Int], maxRetries: Int): Unit = {
  var attempt = 0

  def subscribe(): Unit = {
    publisher.subscribe(new Subscriber[Int] {
      override def onError(t: Throwable): Unit = {
        attempt += 1
        if (attempt < maxRetries) {
          logger.warn(s"Attempt $attempt failed, retrying...", t)
          subscribe()  // Retry
        } else {
          logger.error(s"Failed after $maxRetries attempts", t)
        }
      }

      override def onComplete(): Unit = {
        logger.info("Stream completed successfully")
      }
      // ...
    })
  }

  subscribe()
}
```

**Pattern 2: Fallback to Alternative Source**

```scala 3
def subscribeWithFallback(
  primary: Publisher[Int],
  fallback: Publisher[Int]
): Unit = {
  primary.subscribe(new Subscriber[Int] {
    override def onError(t: Throwable): Unit = {
      logger.warn("Primary failed, switching to fallback", t)
      fallback.subscribe(actualSubscriber)
    }
    // ...
  })
}
```

### Cancellation

Cancellation is a critical feature for resource management and responsive systems. FlowPublisher implements comprehensive cancellation support.

#### Basic Cancellation

Cancel a subscription by calling `cancel()` on the Subscription:

```scala 3
import in.rcard.yaes.Flow
import in.rcard.yaes.FlowPublisher.asPublisher
import in.rcard.yaes.Async.*

val flow = Flow(1 to 100: _*)

Async.run {
  val publisher = flow.asPublisher()

  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _
    var count = 0

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(Long.MaxValue)
    }

    override def onNext(item: Int): Unit = {
      println(s"Received: $item")
      count += 1

      if (count >= 10) {
        subscription.cancel()  // Stop after 10 elements
      }
    }

    override def onComplete(): Unit = {
      // NOT called - subscription was cancelled
      println("Completed")
    }
    // ...
  })

  Async.delay(1.second)  // Wait for processing
}
```

**Behavior:**
- First 10 elements are received and processed
- `cancel()` is called after the 10th element
- No more elements are delivered
- `onComplete()` is NOT called (cancellation is not completion)
- Resources are cleaned up (fibers terminate, channel cancelled)

#### Cancellation Behavior

When `cancel()` is called, FlowPublisher performs several actions:

1. **Set cancelled flag**: `cancelled.compareAndSet(false, true)` (idempotent)
2. **Cancel channel**: `channel.cancel()` - clears buffer, prevents further sends
3. **Wake emitter**: `demandSignal.release()` - if emitter is waiting for demand
4. **Fibers terminate naturally**: Both collector and emitter check cancelled flag and exit
5. **No more signals**: No `onNext`, `onError`, or `onComplete` calls after cancellation

```scala 3
// Simplified FlowPublisher cancellation logic
override def cancel(): Unit = {
  if (cancelled.compareAndSet(false, true)) {  // Idempotent
    channel.cancel()          // Stop producer
    demandSignal.release()    // Wake emitter if waiting
    // Fibers check cancelled flag and exit naturally
  }
}
```

#### Idempotent Cancellation

`cancel()` can be called multiple times safely:

```scala 3
val subscription: Subscription = ???

subscription.cancel()  // First call: performs cancellation
subscription.cancel()  // Subsequent calls: no-op
subscription.cancel()  // Safe to call repeatedly
```

The `compareAndSet` ensures only the first call performs the actual cancellation.

#### Conditional Cancellation

Cancel based on element content or processing results:

```scala 3
publisher.subscribe(new Subscriber[String] {
  override def onNext(item: String): Unit = {
    if (item == "STOP") {
      subscription.cancel()  // Stop on sentinel value
    } else if (item.contains("ERROR")) {
      subscription.cancel()  // Stop on error indicator
    } else {
      process(item)
      subscription.request(1)
    }
  }
  // ...
})
```

#### Timeout-Based Cancellation

Cancel if processing takes too long:

```scala 3
import scala.concurrent.duration.*

Async.run {
  val publisher = flow.asPublisher()

  val timeoutFiber = Async.fork {
    Async.delay(5.seconds)
    subscription.cancel()  // Cancel after timeout
  }

  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(Long.MaxValue)
    }

    override def onNext(item: Int): Unit = {
      process(item)
    }

    override def onComplete(): Unit = {
      timeoutFiber.cancel()  // Cancel timeout if completed
    }

    override def onError(t: Throwable): Unit = {
      timeoutFiber.cancel()  // Cancel timeout on error
    }
  })
}
```

#### Resource Cleanup on Cancellation

Cancellation triggers proper resource cleanup:

```scala 3
val flow = Flow.flow[String] {
  val resource = acquireExpensiveResource()
  try {
    (1 to 1000).foreach { i =>
      Flow.emit(resource.read(i))
    }
  } finally {
    resource.close()  // Cleaned up even if cancelled
  }
}

Async.run {
  val publisher = flow.asPublisher()

  publisher.subscribe(subscriber)

  // Even if subscriber cancels early, resource is cleaned up
  // because channel.cancel() causes channel.send() to raise ChannelClosed,
  // which is caught and handled in the collector fiber
}
```

**Cleanup Mechanism:**
- Collector fiber has `finally` block that calls `channel.close()`
- Cancellation causes `channel.cancel()`, which clears the buffer
- Subsequent `channel.send()` operations raise `ChannelClosed`
- Collector fiber's `finally` block executes, cleaning up resources

#### Cancellation vs Completion

Important distinction between cancellation and normal completion:

| Aspect | Cancellation | Normal Completion |
|--------|-------------|-------------------|
| Trigger | Subscriber calls `cancel()` | Flow finishes naturally |
| Terminal Event | None | `onComplete()` called |
| Element Count | Partial (as requested) | All elements |
| Resource Cleanup | Immediate | After last element |
| Error Handling | No `onError` if cancelled | `onError` on failure |

```scala 3
// Cancellation - no terminal event
subscription.cancel()
// onComplete() NOT called
// onError() NOT called

// Normal completion - terminal event
// All elements delivered
// onComplete() IS called
```

### Reactive Streams Specification Compliance

FlowPublisher is designed to fully comply with the Reactive Streams specification. Understanding the rules helps ensure correct usage.

#### Key Specification Rules

The Reactive Streams specification defines rules for Publishers, Subscribers, and Subscriptions. FlowPublisher implements all Publisher rules:

**Rule 1.1: Total Demand Respect**

> The total number of `onNext` signals sent by a Publisher to a Subscriber MUST be less than or equal to the total number of elements requested by that Subscriber's Subscription.

```scala 3
// FlowPublisher implementation
def onNext(value: A): Unit = {
  if (waitForDemand()) {  // Waits until demand > 0
    subscriber.onNext(value)
    demand.decrementAndGet()  // Decrement demand counter
  }
}
```

**Rule 1.3: Serial Signal Delivery**

> `onNext`, `onError`, and `onComplete` signaled to a Subscriber MUST be signaled serially.

FlowPublisher ensures this through its single-threaded emitter fiber. All signals originate from one fiber, guaranteeing serial delivery.

**Rule 1.7: No Signals After Termination**

> Once a terminal state has been signaled (`onError`, `onComplete`) it is REQUIRED that no further signals occur.

```scala 3
// FlowPublisher uses atomic flag
def terminateWithError(error: Throwable): Unit = {
  if (terminated.compareAndSet(false, true)) {  // Only first call succeeds
    subscriber.onError(error)
  }
}

def complete(): Unit = {
  if (terminated.compareAndSet(false, true) && !cancelled.get()) {
    subscriber.onComplete()
  }
}
```

**Rule 1.9: Non-null Subscriber**

> `Publisher.subscribe` MUST throw a `java.lang.NullPointerException` if the Subscriber is null.

```scala 3
override def subscribe(subscriber: Subscriber[? >: A]): Unit = {
  require(subscriber != null, "Subscriber cannot be null")
  // ...
}
```

**Rule 2.13: No Null Elements**

> Calling `onNext` with a null element MUST cause a `NullPointerException` to be thrown.

```scala 3
// FlowPublisher checks for null
case Right(value) if value == null =>
  running = false
  terminateWithError(new NullPointerException("Flow emitted null element"))
```

**Rule 3.9: Positive Request**

> `Subscription.request` MUST signal `onError` with an `IllegalArgumentException` if the argument is <= 0.

```scala 3
override def request(n: Long): Unit = {
  if (n <= 0) {
    val ex = new IllegalArgumentException(s"Rule 3.9: request($n) must be > 0")
    if (terminated.compareAndSet(false, true)) {
      subscriber.onError(ex)
    }
    cancel()
  } else {
    demand.addAndGet(n)
    demandSignal.release()
  }
}
```

#### Verification Through Tests

FlowPublisher includes 22 comprehensive tests that verify specification compliance:

**Phase 1: Basic Publisher Contract**
- Single element emission
- Multiple elements in order
- Empty flow completion

**Phase 2: Demand Tracking and Backpressure**
- Respecting `request(n)` demand
- Rejecting invalid requests
- Backpressure with bounded channels
- Concurrent `request()` calls

**Phase 3: Cancellation**
- Stopping emission on cancellation
- Resource cleanup
- Idempotent `cancel()`
- No signals after cancellation

**Phase 4: Error Handling**
- Flow exception propagation
- Channel closure handling
- Subscriber exception handling
- Single terminal event guarantee

**Phase 5: Specification Compliance**
- Null subscriber rejection
- Null element detection
- No signals after completion

**Phase 6: Performance and Edge Cases**
- Large flow handling (10,000 elements)
- Multiple independent subscribers

**Phase 7: API and Extensions**
- Extension method syntax
- Custom buffer configuration

All tests pass, demonstrating full compliance with the specification.

#### Common Specification Violations to Avoid

When implementing Subscribers, avoid these common mistakes:

**Violation 1: Not requesting elements**

```scala 3
// ❌ BAD: Never requests elements
override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  // Missing: s.request(n)
}
```

**Violation 2: Calling subscription methods after cancellation**

```scala 3
// ❌ BAD: Using subscription after cancel
override def onNext(item: Int): Unit = {
  if (shouldStop(item)) {
    subscription.cancel()
    subscription.request(10)  // WRONG: already cancelled
  }
}
```

**Violation 3: Throwing from onError**

```scala 3
// ❌ BAD: Throwing exception from error handler
override def onError(t: Throwable): Unit = {
  throw new RuntimeException("Error handler failed")
}
```

**Violation 4: Calling onNext recursively**

```scala 3
// ❌ BAD: Recursive signal delivery
override def onNext(item: Int): Unit = {
  process(item)
  subscriber.onNext(nextItem)  // WRONG: violates serial delivery
}
```

### Integration Examples

FlowPublisher's real power comes from enabling YAES flows to integrate with existing reactive ecosystems.

#### Integration with Akka Streams

Akka Streams supports Java Flow API through `JavaFlowSupport`:

```scala 3
import akka.stream.scaladsl.{Source, Sink, JavaFlowSupport}
import akka.actor.ActorSystem
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.FlowPublisher.asPublisher
import in.rcard.yaes.Async.*

given actorSystem: ActorSystem = ActorSystem("reactive-system")

val yaesFlow = Flow(1, 2, 3, 4, 5)

Async.run {
  val publisher = yaesFlow.asPublisher()

  // Convert to Akka Source
  val akkaSource = JavaFlowSupport.Source.fromPublisher(publisher)

  // Use Akka operators
  val result = akkaSource
    .map(_ * 2)
    .filter(_ > 5)
    .runWith(Sink.seq)

  // result: Future[Seq[Int]] = Future(Success(Seq(6, 8, 10)))
}
```

This enables YAES flows to participate in Akka Streams graphs, using Akka's rich operator library while maintaining YAES effect management upstream.

#### Integration with Project Reactor

Project Reactor's `Flux` can consume Java Flow Publishers:

```scala 3
import reactor.core.publisher.Flux
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val yaesFlow = Flow("a", "b", "c", "d", "e")

Async.run {
  val publisher = FlowPublisher.fromFlow(yaesFlow)

  // Convert to Reactor Flux
  val flux = Flux.from(publisher)

  // Use Reactor operators
  flux
    .map(_.toUpperCase)
    .filter(_.length > 0)
    .subscribe(value => println(s"Received: $value"))
}
```

Reactor's operators (map, filter, flatMap, etc.) can now process elements from YAES flows.

#### Integration with RxJava

RxJava 3 includes Flow interop through `FlowAdapter`:

```scala 3
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.processors.FlowableProcessor
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val yaesFlow = Flow(1 to 100: _*)

Async.run {
  val publisher = yaesFlow.asPublisher()

  // Convert to RxJava Flowable
  val flowable = Flowable.fromPublisher(publisher)

  // Use RxJava operators
  flowable
    .buffer(10)  // Batch into groups of 10
    .map(_.sum)  // Sum each batch
    .subscribe(sum => println(s"Batch sum: $sum"))
}
```

#### Multiple Subscribers Pattern

Each subscription to a FlowPublisher creates an independent execution:

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*
import java.util.concurrent.Flow.{Subscriber, Subscription}
import scala.collection.mutable

val flow = Flow.flow[Int] {
  println("Flow execution started")
  (1 to 3).foreach { i =>
    println(s"Emitting: $i")
    Flow.emit(i)
  }
}

Async.run {
  val publisher = FlowPublisher.fromFlow(flow)

  val results1 = mutable.ArrayBuffer[Int]()
  val results2 = mutable.ArrayBuffer[Int]()

  // First subscriber
  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(Long.MaxValue)
    }

    override def onNext(item: Int): Unit = {
      results1 += item
    }

    override def onError(t: Throwable): Unit = {
      println(s"Error in subscriber 1: ${t.getMessage}")
    }

    override def onComplete(): Unit = {
      println("Subscriber 1 completed")
    }
  })
  // Prints:
  //   Flow execution started
  //   Emitting: 1
  //   Emitting: 2
  //   Emitting: 3
  //   Subscriber 1 completed

  // Second subscriber (independent execution)
  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(Long.MaxValue)
    }

    override def onNext(item: Int): Unit = {
      results2 += item
    }

    override def onError(t: Throwable): Unit = {
      println(s"Error in subscriber 2: ${t.getMessage}")
    }

    override def onComplete(): Unit = {
      println("Subscriber 2 completed")
    }
  })
  // Prints again:
  //   Flow execution started
  //   Emitting: 1
  //   Emitting: 2
  //   Emitting: 3
  //   Subscriber 2 completed

  // Wait for async completion
  Async.delay(100.millis)

  // results1 == ArrayBuffer(1, 2, 3)
  // results2 == ArrayBuffer(1, 2, 3)
  // Each subscriber got independent execution
}
```

**Key Points:**
- Each `subscribe()` creates fresh fibers and channel
- Flow executes independently for each subscriber
- Side effects in Flow occur multiple times (once per subscriber)
- Subscribers don't share state or affect each other

#### Fan-Out Pattern with Multiple Subscribers

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*
import java.util.concurrent.ConcurrentLinkedQueue

val events = Flow(1 to 100: _*)
val results = new ConcurrentLinkedQueue[String]()

Async.run {
  val publisher = events.asPublisher()

  // Subscriber 1: Filters evens
  publisher.subscribe(new Subscriber[Int] {
    override def onNext(item: Int): Unit = {
      if (item % 2 == 0) {
        results.add(s"Even: $item")
      }
      subscription.request(1)
    }
    // ...
  })

  // Subscriber 2: Filters odds
  publisher.subscribe(new Subscriber[Int] {
    override def onNext(item: Int): Unit = {
      if (item % 2 != 0) {
        results.add(s"Odd: $item")
      }
      subscription.request(1)
    }
    // ...
  })

  Async.delay(1.second)  // Wait for processing

  // results contains both "Even: ..." and "Odd: ..." entries
  // Each subscriber processed all 100 elements independently
}
```

### Performance Considerations

Understanding FlowPublisher's performance characteristics helps you optimize for your specific use cases.

#### Memory Usage

FlowPublisher allocates several data structures per subscription:

**Per-Subscription Overhead:**
- 2 fibers (collector and emitter)
- 1 channel (buffer) with configurable capacity
- 1 AtomicLong (demand counter)
- 2 AtomicBoolean (cancelled and terminated flags)
- 1 Semaphore (demand signaling)
- 1 Subscription object

**Memory Calculation:**
```
Memory per subscription ≈
  Fiber overhead (2 × ~1KB) +
  Channel buffer (capacity × element_size) +
  Atomic variables (~100 bytes) +
  Subscription object (~100 bytes)

Example with Bounded(16) buffer of Int:
  ≈ 2KB (fibers) + 64 bytes (16 × 4) + 200 bytes (atomics + subscription)
  ≈ 2.3KB + your element size
```

**Multiple Subscribers:**

Each subscriber creates independent resources:

```scala 3
val publisher = flow.asPublisher()

// 3 subscribers = 3 × resources
publisher.subscribe(subscriber1)  // +2.3KB + elements
publisher.subscribe(subscriber2)  // +2.3KB + elements
publisher.subscribe(subscriber3)  // +2.3KB + elements
```

**Buffer Size Impact:**

```scala 3
// Small buffer: 16 × 4 bytes = 64 bytes
val publisher1 = flow.asPublisher(Channel.Type.Bounded(16))

// Large buffer: 1024 × 4 bytes = 4KB
val publisher2 = flow.asPublisher(Channel.Type.Bounded(1024))

// Unbounded: Grows without limit (dangerous!)
val publisher3 = flow.asPublisher(Channel.Type.Unbounded)
```

#### Throughput Optimization

**1. Buffer Size Tuning**

Larger buffers reduce coordination overhead:

```scala 3
// Low throughput: Frequent coordination
val publisher1 = flow.asPublisher(Channel.Type.Bounded(8))
// Producer and consumer frequently wait for each other

// High throughput: Less coordination
val publisher2 = flow.asPublisher(Channel.Type.Bounded(256))
// Producer can run ahead, consumer rarely blocks
```

**Benchmark example** (conceptual):

| Buffer Size | Throughput | Memory |
|-------------|------------|--------|
| 8 | 100K items/sec | Low |
| 16 (default) | 150K items/sec | Low |
| 64 | 250K items/sec | Medium |
| 256 | 300K items/sec | Medium |
| 1024 | 320K items/sec | High |

Diminishing returns after 256 for most workloads.

**2. Request Strategy**

Request more elements at once for better throughput:

```scala 3
// Low throughput: One at a time
override def onNext(item: Int): Unit = {
  process(item)
  subscription.request(1)  // Frequent coordination
}

// Better throughput: Batch requests
override def onNext(item: Int): Unit = {
  process(item)
  if (received % 100 == 0) {
    subscription.request(100)  // Less frequent coordination
  }
}
```

**3. Minimize onNext Overhead**

Keep `onNext` processing fast:

```scala 3
// ✅ GOOD: Fast onNext
override def onNext(item: Int): Unit = {
  buffer.add(item)  // O(1) operation
  subscription.request(1)
}

// ❌ BAD: Slow onNext
override def onNext(item: Int): Unit = {
  database.saveBlocking(item)  // Blocks, limits throughput
  subscription.request(1)
}

// ✅ BETTER: Async processing
override def onNext(item: Int): Unit = {
  queue.offer(item)  // Non-blocking
  subscription.request(1)
}
// Separate worker processes queue asynchronously
```

#### Latency Considerations

**Buffer Size vs Latency:**

Larger buffers increase latency:

```scala 3
// Low latency: Small buffer
val publisher1 = flow.asPublisher(Channel.Type.Bounded(2))
// Elements reach consumer quickly but less throughput

// Higher latency: Large buffer
val publisher2 = flow.asPublisher(Channel.Type.Bounded(256))
// Elements may wait in buffer, more throughput
```

**Backpressure Latency:**

Time for backpressure to take effect depends on buffer size:

```
Backpressure latency ≈ buffer_size / consumer_rate

Example:
  Buffer = 100 elements
  Consumer = 10 items/sec
  Backpressure latency ≈ 10 seconds
```

Smaller buffers provide faster feedback.

#### Scalability

**Number of Subscribers:**

Linear scaling with subscriber count:

```scala 3
val publisher = flow.asPublisher()

// 1 subscriber: 1× resource usage
publisher.subscribe(sub1)

// 10 subscribers: 10× resource usage
(1 to 10).foreach { _ =>
  publisher.subscribe(createSubscriber())
}
```

Each subscriber is independent, so resources scale linearly.

**Long-Running Subscriptions:**

FlowPublisher is designed for long-running subscriptions:

```scala 3
// Long-running: Efficient resource usage
Async.run {
  val publisher = eventStream.asPublisher()

  publisher.subscribe(new Subscriber[Event] {
    // Can run for hours/days with stable memory
    override def onNext(event: Event): Unit = {
      process(event)
      subscription.request(1)
    }
    // ...
  })
}
```

No memory leaks, stable resource usage over time.

#### Performance Best Practices

1. **Right-Size Buffers**: Start with default (16), increase if producer/consumer speeds differ significantly

2. **Batch Requests**: Request multiple elements at once (10-100) for better throughput

3. **Fast onNext**: Keep `onNext` processing minimal, delegate heavy work to background workers

4. **Monitor Demand**: Track outstanding demand to tune request batch sizes

5. **Profile Your Workload**: Different workloads have different optimal configurations

6. **Avoid Unbounded**: Unbounded buffers can cause out-of-memory errors

7. **Consider DROP Strategies**: For lossy scenarios, DROP strategies prevent backpressure overhead

### Common Patterns

Several patterns emerge as best practices when working with FlowPublisher.

#### Pattern 1: Batch Processing

Process elements in batches for efficiency:

```scala 3
import scala.collection.mutable
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val events = Flow(1 to 1000: _*)

Async.run {
  val publisher = events.asPublisher()

  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _
    val batch = mutable.ArrayBuffer[Int]()
    val BATCH_SIZE = 10

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(BATCH_SIZE)  // Request first batch
    }

    override def onNext(item: Int): Unit = {
      batch += item

      if (batch.size >= BATCH_SIZE) {
        // Process full batch
        processBatch(batch.toList)
        batch.clear()

        // Request next batch
        subscription.request(BATCH_SIZE)
      }
    }

    override def onComplete(): Unit = {
      // Process remaining items
      if (batch.nonEmpty) {
        processBatch(batch.toList)
      }
    }

    override def onError(t: Throwable): Unit = {
      println(s"Error: ${t.getMessage}")
    }
  })
}

def processBatch(items: List[Int]): Unit = {
  println(s"Processing batch of ${items.size} items")
  // Efficient batch processing (e.g., bulk database insert)
}
```

**Benefits:**
- Amortizes processing overhead across multiple elements
- Better performance for operations with fixed costs (database writes, network calls)
- Natural backpressure (request in batches)

#### Pattern 2: Rate Limiting

Limit processing rate to avoid overwhelming downstream systems:

```scala 3
import scala.concurrent.duration.*
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val requests = Flow(1 to 1000: _*)

Async.run {
  val publisher = requests.asPublisher()

  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _
    val RATE_LIMIT_MS = 100  // 10 requests/second

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(1)  // Request one at a time for rate limiting
    }

    override def onNext(item: Int): Unit = {
      // Process item
      makeApiRequest(item)

      // Rate limit: Wait before requesting next
      Async.delay(RATE_LIMIT_MS.millis)
      subscription.request(1)
    }

    override def onComplete(): Unit = {
      println("All requests completed")
    }

    override def onError(t: Throwable): Unit = {
      println(s"Error: ${t.getMessage}")
    }
  })
}

def makeApiRequest(id: Int): Unit = {
  println(s"API request $id at ${System.currentTimeMillis()}")
  // Makes actual API call
}
```

**Benefits:**
- Prevents overwhelming external APIs
- Respects rate limits
- Natural backpressure mechanism

#### Pattern 3: Conditional Processing

Process or skip elements based on conditions:

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

case class Event(id: Int, priority: Int, data: String)

val events = Flow(
  Event(1, 1, "low"),
  Event(2, 5, "high"),
  Event(3, 2, "low"),
  Event(4, 8, "critical")
)

Async.run {
  val publisher = events.asPublisher()

  publisher.subscribe(new Subscriber[Event] {
    var subscription: Subscription = _

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(Long.MaxValue)
    }

    override def onNext(event: Event): Unit = {
      // Only process high-priority events
      if (event.priority >= 5) {
        processHighPriority(event)
      } else {
        // Skip low-priority events
        println(s"Skipping low-priority event ${event.id}")
      }
    }

    override def onComplete(): Unit = {
      println("Event processing complete")
    }

    override def onError(t: Throwable): Unit = {
      println(s"Error: ${t.getMessage}")
    }
  })
}

def processHighPriority(event: Event): Unit = {
  println(s"Processing high-priority event ${event.id}: ${event.data}")
}
```

#### Pattern 4: Accumulating Results

Accumulate results across all elements:

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val numbers = Flow(1 to 100: _*)

Async.run {
  val publisher = numbers.asPublisher()

  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _
    var sum = 0
    var count = 0
    var min = Int.MaxValue
    var max = Int.MinValue

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(Long.MaxValue)
    }

    override def onNext(item: Int): Unit = {
      sum += item
      count += 1
      min = math.min(min, item)
      max = math.max(max, item)
    }

    override def onComplete(): Unit = {
      val average = sum.toDouble / count
      println(s"Statistics:")
      println(s"  Count: $count")
      println(s"  Sum: $sum")
      println(s"  Average: $average")
      println(s"  Min: $min")
      println(s"  Max: $max")
    }

    override def onError(t: Throwable): Unit = {
      println(s"Error during accumulation: ${t.getMessage}")
    }
  })
}
```

#### Pattern 5: Error Recovery with Retry

Retry failed element processing:

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*
import scala.util.{Try, Success, Failure}

val data = Flow(1 to 100: _*)

Async.run {
  val publisher = data.asPublisher()

  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _
    val MAX_RETRIES = 3

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(1)
    }

    override def onNext(item: Int): Unit = {
      processWithRetry(item, MAX_RETRIES) match {
        case Success(_) =>
          subscription.request(1)  // Continue to next
        case Failure(e) =>
          // Failed after retries
          println(s"Item $item failed after $MAX_RETRIES retries")
          subscription.request(1)  // Skip and continue
      }
    }

    override def onComplete(): Unit = {
      println("Processing complete")
    }

    override def onError(t: Throwable): Unit = {
      println(s"Fatal error: ${t.getMessage}")
    }
  })
}

def processWithRetry(item: Int, retriesLeft: Int): Try[Unit] = {
  Try {
    riskyOperation(item)
  } match {
    case Success(result) => Success(())
    case Failure(e) if retriesLeft > 0 =>
      println(s"Retry $item (${retriesLeft} left)")
      Thread.sleep(100)  // Backoff
      processWithRetry(item, retriesLeft - 1)
    case Failure(e) => Failure(e)
  }
}

def riskyOperation(item: Int): Unit = {
  if (scala.util.Random.nextDouble() < 0.1) {
    throw new RuntimeException(s"Simulated failure for $item")
  }
  println(s"Successfully processed $item")
}
```

#### Pattern 6: Adaptive Demand Management

Adjust request batch size based on processing performance:

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val stream = Flow(1 to 10000: _*)

Async.run {
  val publisher = stream.asPublisher()

  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _
    var outstandingDemand = 0
    var processingTimeMs = 0L
    val LOW_WATERMARK = 5
    val HIGH_WATERMARK = 20

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      outstandingDemand = HIGH_WATERMARK
      s.request(HIGH_WATERMARK)
    }

    override def onNext(item: Int): Unit = {
      val startTime = System.currentTimeMillis()

      processItem(item)

      processingTimeMs = System.currentTimeMillis() - startTime
      outstandingDemand -= 1

      // Refill when hitting low watermark
      if (outstandingDemand <= LOW_WATERMARK) {
        val toRequest = if (processingTimeMs > 50) {
          // Slow processing: Request smaller batch
          10
        } else {
          // Fast processing: Request larger batch
          HIGH_WATERMARK - outstandingDemand
        }

        subscription.request(toRequest)
        outstandingDemand += toRequest
      }
    }

    override def onComplete(): Unit = {
      println("Adaptive processing complete")
    }

    override def onError(t: Throwable): Unit = {
      println(s"Error: ${t.getMessage}")
    }
  })
}

def processItem(item: Int): Unit = {
  // Variable processing time
  Thread.sleep((10 + scala.util.Random.nextInt(90)).toLong)
  println(s"Processed $item")
}
```

### Best Practices

Following these best practices ensures correct, efficient, and maintainable use of FlowPublisher.

#### Do's

**✅ Always call request(n)**

Subscribers must request elements to receive them:

```scala 3
override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  s.request(10)  // MUST call request
}
```

Without `request`, no elements will be delivered.

**✅ Handle onError appropriately**

Always implement error handling:

```scala 3
override def onError(t: Throwable): Unit = {
  logger.error("Stream error", t)
  cleanup()
  notifyFailure(t)
}
```

**✅ Cancel subscriptions when done early**

If you don't need more elements, cancel:

```scala 3
override def onNext(item: Int): Unit = {
  if (foundTarget(item)) {
    processTarget(item)
    subscription.cancel()  // Done, stop stream
  }
}
```

**✅ Use SUSPEND strategy for backpressure**

For true backpressure, use SUSPEND:

```scala 3
val publisher = flow.asPublisher(
  Channel.Type.Bounded(16, Channel.OverflowStrategy.SUSPEND)
)
```

**✅ Choose buffer size based on workload**

Profile and tune for your specific case:

```scala 3
// High throughput scenario
val publisher = flow.asPublisher(Channel.Type.Bounded(256))

// Memory constrained scenario
val publisher = flow.asPublisher(Channel.Type.Bounded(8))
```

**✅ Test with various demand patterns**

Test your subscriber with different request patterns:

```scala 3
// Test with single requests
s.request(1)

// Test with batch requests
s.request(100)

// Test with unlimited requests
s.request(Long.MaxValue)
```

#### Don'ts

**❌ Don't call subscription methods from multiple threads concurrently**

Subscription methods are not thread-safe:

```scala 3
// ❌ BAD: Concurrent calls
override def onNext(item: Int): Unit = {
  executor.submit(() => subscription.request(1))  // WRONG
  executor.submit(() => subscription.request(1))  // WRONG
}

// ✅ GOOD: Sequential calls from same thread
override def onNext(item: Int): Unit = {
  process(item)
  subscription.request(1)  // OK
}
```

**❌ Don't emit null elements from Flow**

Null elements violate Reactive Streams spec:

```scala 3
// ❌ BAD: Null elements
val flow = Flow[String](List("a", null, "c"): _*)

// ✅ GOOD: Use Option for nullable values
val flow = Flow[Option[String]](List(Some("a"), None, Some("c")): _*)
```

**❌ Don't call request(0) or negative values**

Only positive values are allowed:

```scala 3
// ❌ BAD
subscription.request(0)   // Triggers onError
subscription.request(-1)  // Triggers onError

// ✅ GOOD
subscription.request(1)   // OK
subscription.request(10)  // OK
```

**❌ Don't block in onNext for extended periods**

Blocking limits throughput:

```scala 3
// ❌ BAD: Long blocking operation
override def onNext(item: Int): Unit = {
  Thread.sleep(5000)  // Blocks for 5 seconds
  process(item)
  subscription.request(1)
}

// ✅ GOOD: Async processing
override def onNext(item: Int): Unit = {
  queue.offer(item)  // Non-blocking
  subscription.request(1)
}
// Background worker processes queue
```

**❌ Don't ignore errors in onError**

Always handle errors properly:

```scala 3
// ❌ BAD: Ignoring errors
override def onError(t: Throwable): Unit = {
  // Empty - error ignored
}

// ✅ GOOD: Proper error handling
override def onError(t: Throwable): Unit = {
  logger.error("Stream failed", t)
  cleanup()
  notifyMonitoring(t)
}
```

**❌ Don't use Unbounded without good reason**

Unbounded buffers can cause OOM:

```scala 3
// ❌ RISKY: Unbounded buffer
val publisher = flow.asPublisher(Channel.Type.Unbounded)
// Can grow without limit if consumer is slow

// ✅ BETTER: Bounded buffer
val publisher = flow.asPublisher(
  Channel.Type.Bounded(64, Channel.OverflowStrategy.SUSPEND)
)
```

#### Summary of Best Practices

| Category | Best Practice | Rationale |
|----------|--------------|-----------|
| Demand | Always call `request(n)` | Without request, no elements delivered |
| Demand | Use batch requests for throughput | Reduces coordination overhead |
| Demand | Use single requests for control | Maximum backpressure control |
| Errors | Always implement `onError` | Prevents silent failures |
| Errors | Log errors with context | Aids debugging |
| Cancellation | Cancel when done early | Frees resources promptly |
| Cancellation | Handle cancellation in `onError`/`onComplete` | Cleanup resources |
| Buffers | Start with default (16) | Balanced for most cases |
| Buffers | Increase for throughput | If producer/consumer speeds differ |
| Buffers | Decrease for memory | If memory is constrained |
| Buffers | Use SUSPEND for backpressure | True backpressure control |
| Buffers | Use DROP for lossy scenarios | When loss is acceptable |
| Processing | Keep `onNext` fast | Maximize throughput |
| Processing | Delegate heavy work | Use background workers |
| Testing | Test various request patterns | Ensures robustness |
| Testing | Test error scenarios | Ensures proper error handling |

### Troubleshooting

Common issues and their solutions when working with FlowPublisher.

#### Issue: Publisher never completes

**Symptoms:**
- `onComplete()` is never called
- Subscription hangs indefinitely
- Program doesn't terminate

**Possible Causes:**

**Cause 1: Infinite Flow**

```scala 3
// Infinite flow never completes
val infiniteFlow = Flow.flow[Int] {
  var i = 0
  while (true) {  // Infinite loop
    Flow.emit(i)
    i += 1
  }
}
```

**Solution:** Ensure Flow has finite elements, or use cancellation:

```scala 3
override def onNext(item: Int): Unit = {
  if (shouldStop(item)) {
    subscription.cancel()  // Stop explicitly
  }
}
```

**Cause 2: Subscriber never requests elements**

```scala 3
override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  // Missing: s.request(n)
}
```

**Solution:** Always call `request()`:

```scala 3
override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  s.request(Long.MaxValue)  // Request elements
}
```

**Cause 3: Flow blocks indefinitely**

```scala 3
val blockedFlow = Flow.flow[Int] {
  Flow.emit(1)
  Thread.sleep(Long.MaxValue)  // Blocks forever
  Flow.emit(2)  // Never reached
}
```

**Solution:** Ensure Flow completes in finite time:

```scala 3
val fixedFlow = Flow.flow[Int] {
  Flow.emit(1)
  Async.delay(1.second)  // Finite delay
  Flow.emit(2)
}
```

#### Issue: Elements dropped

**Symptoms:**
- Subscriber receives fewer elements than Flow emits
- Some elements are missing
- No error is raised

**Possible Causes:**

**Cause 1: Using DROP overflow strategy**

```scala 3
val publisher = flow.asPublisher(
  Channel.Type.Bounded(10, Channel.OverflowStrategy.DROP_OLDEST)
)
```

**Solution:** If all elements are needed, use SUSPEND:

```scala 3
val publisher = flow.asPublisher(
  Channel.Type.Bounded(10, Channel.OverflowStrategy.SUSPEND)
)
```

**Cause 2: Buffer too small for production rate**

```scala 3
// Fast producer, small buffer, slow consumer
val fastFlow = Flow(1 to 10000: _*)
val publisher = fastFlow.asPublisher(Channel.Type.Bounded(5))

publisher.subscribe(new Subscriber[Int] {
  override def onNext(item: Int): Unit = {
    Thread.sleep(100)  // Slow consumer
    // Elements may be dropped with DROP strategy
  }
})
```

**Solution:** Increase buffer size or slow down producer:

```scala 3
val publisher = fastFlow.asPublisher(
  Channel.Type.Bounded(100, Channel.OverflowStrategy.SUSPEND)
)
```

**Cause 3: Cancellation**

If subscription is cancelled, remaining elements are dropped:

```scala 3
override def onNext(item: Int): Unit = {
  if (item == 10) {
    subscription.cancel()  // Remaining elements dropped
  }
}
```

**Solution:** Don't cancel if all elements are needed.

#### Issue: Memory growth

**Symptoms:**
- Memory usage increases over time
- OutOfMemoryError occurs
- Garbage collection pressure

**Possible Causes:**

**Cause 1: Unbounded channel**

```scala 3
val publisher = flow.asPublisher(Channel.Type.Unbounded)
// Buffer grows without limit if consumer is slow
```

**Solution:** Use bounded buffer:

```scala 3
val publisher = flow.asPublisher(
  Channel.Type.Bounded(64, Channel.OverflowStrategy.SUSPEND)
)
```

**Cause 2: Subscriber not requesting fast enough**

```scala 3
override def onNext(item: Int): Unit = {
  process(item)
  // Taking too long between requests
  Thread.sleep(1000)
  subscription.request(1)
}
```

**Solution:** Request in larger batches or process faster:

```scala 3
override def onNext(item: Int): Unit = {
  processAsync(item)  // Non-blocking processing
  if (received % 10 == 0) {
    subscription.request(10)  // Batch requests
  }
}
```

**Cause 3: Large buffer size**

```scala 3
val publisher = flow.asPublisher(Channel.Type.Bounded(100000))
// Large buffer consumes significant memory
```

**Solution:** Reduce buffer size:

```scala 3
val publisher = flow.asPublisher(Channel.Type.Bounded(64))
```

#### Issue: Deadlock

**Symptoms:**
- Program hangs
- No progress is made
- Threads are blocked

**Possible Causes:**

**Cause 1: Circular dependency between publishers**

```scala 3
// Publisher A waits for B, B waits for A
val publisherA = flowA.asPublisher()
val publisherB = flowB.asPublisher()

publisherA.subscribe(new Subscriber[Int] {
  override def onNext(item: Int): Unit = {
    // Blocks waiting for publisherB
    waitForB(item)
  }
})
```

**Solution:** Avoid circular dependencies, use proper fiber coordination.

**Cause 2: Blocking operations in onNext**

```scala 3
override def onNext(item: Int): Unit = {
  // Blocks entire processing
  val result = blockingOperation(item)
  subscription.request(1)
}
```

**Solution:** Use non-blocking operations:

```scala 3
override def onNext(item: Int): Unit = {
  asyncOperation(item).onComplete { result =>
    subscription.request(1)
  }
}
```

**Cause 3: Insufficient parallelism**

If processing requires multiple threads but only one is available:

```scala 3
// Single-threaded executor, but operations block
val executor = Executors.newSingleThreadExecutor()
```

**Solution:** Use sufficient parallelism:

```scala 3
val executor = Executors.newVirtualThreadPerTaskExecutor()
// Or use Async.fork for concurrent operations
```

#### Issue: Slow performance

**Symptoms:**
- Low throughput
- High latency
- Processing takes longer than expected

**Possible Causes:**

**Cause 1: Small buffer size**

```scala 3
val publisher = flow.asPublisher(Channel.Type.Bounded(2))
// Frequent coordination between producer and consumer
```

**Solution:** Increase buffer size:

```scala 3
val publisher = flow.asPublisher(Channel.Type.Bounded(64))
```

**Cause 2: Request one at a time**

```scala 3
override def onNext(item: Int): Unit = {
  process(item)
  subscription.request(1)  // Frequent requests
}
```

**Solution:** Request in batches:

```scala 3
override def onNext(item: Int): Unit = {
  process(item)
  if (received % 10 == 0) {
    subscription.request(10)  // Less frequent requests
  }
}
```

**Cause 3: Blocking operations in onNext**

```scala 3
override def onNext(item: Int): Unit = {
  Thread.sleep(100)  // Blocks processing
  subscription.request(1)
}
```

**Solution:** Use async processing:

```scala 3
override def onNext(item: Int): Unit = {
  queue.offer(item)  // Non-blocking
  subscription.request(1)
}
// Background worker processes queue
```

#### Debugging Tips

**1. Add logging**

```scala 3
override def onSubscribe(s: Subscription): Unit = {
  logger.info("Subscribed")
  subscription = s
  s.request(10)
  logger.info("Requested 10 elements")
}

override def onNext(item: Int): Unit = {
  logger.debug(s"Received: $item")
  process(item)
  subscription.request(1)
}

override def onComplete(): Unit = {
  logger.info("Stream completed")
}

override def onError(t: Throwable): Unit = {
  logger.error("Stream error", t)
}
```

**2. Monitor metrics**

```scala 3
var totalReceived = 0
var totalRequested = 0
val startTime = System.currentTimeMillis()

override def onNext(item: Int): Unit = {
  totalReceived += 1
  val elapsed = System.currentTimeMillis() - startTime
  val rate = totalReceived.toDouble / elapsed * 1000

  if (totalReceived % 100 == 0) {
    logger.info(f"Received: $totalReceived, Rate: $rate%.2f items/sec")
  }
}
```

**3. Use test subscribers**

Create simple test subscribers to isolate issues:

```scala 3
import java.util.concurrent.Flow.{Subscriber, Subscription}

class DebugSubscriber[A] extends Subscriber[A] {
  var subscription: Subscription = _

  override def onSubscribe(s: Subscription): Unit = {
    println("onSubscribe called")
    subscription = s
    s.request(Long.MaxValue)
  }

  override def onNext(item: A): Unit = {
    println(s"onNext: $item")
  }

  override def onError(t: Throwable): Unit = {
    println(s"onError: ${t.getMessage}")
    t.printStackTrace()
  }

  override def onComplete(): Unit = {
    println("onComplete called")
  }
}
```

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
