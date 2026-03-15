---
title: Streams & Channels
description: Flow and Channels for reactive data processing in λÆS
sidebar:
  label: "7. Streams & Channels"
  order: 7
---

> **Step 7 of 8** — This is the most data-intensive step of the learning path. By the end, you'll understand reactive streams and channel-based communication, two powerful tools for building data pipelines.

The `yaes-data` module provides two complementary abstractions for working with data over time:

- **Flow** — a cold, asynchronous data stream for reactive processing and pipeline composition
- **Channel** — a communication primitive for passing data between concurrent fibers

Both are part of the `yaes-data` module and integrate seamlessly with λÆS effects.

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-data" % "0.15.0"
```

---

## Flow

A `Flow` is a cold asynchronous data stream that sequentially emits values and completes normally or with an exception. It's conceptually similar to Iterators from the Collections framework but designed for asynchronous operation.

### Key Characteristics

- **Cold**: Flows don't produce values until collected
- **Asynchronous**: Values can be emitted asynchronously
- **Sequential**: Values are emitted in order
- **Composable**: Rich set of operators for transformation
- **Functional**: Immutable and side-effect free

| Feature | Iterator | Flow |
|---------|----------|------|
| Execution | Synchronous | Asynchronous |
| Transformation | Limited | Rich operator set |
| Observation | Direct access | Collect method |
| Composition | Basic | Highly composable |

### Creating Flows

#### From Explicit Emissions

```scala
import in.rcard.yaes.Flow

val numbersFlow: Flow[Int] = Flow.flow[Int] {
  Flow.emit(1)
  Flow.emit(2)
  Flow.emit(3)
}
```

#### From Collections

```scala
import in.rcard.yaes.Flow

val listFlow: Flow[Int] = List(1, 2, 3).asFlow()
val setFlow: Flow[String] = Set("a", "b", "c").asFlow()
```

#### From Varargs

```scala
import in.rcard.yaes.Flow

val flow: Flow[String] = Flow("hello", "world", "!")
```

#### From InputStream

Create a flow that reads data from an InputStream as byte chunks:

```scala
import in.rcard.yaes.Flow
import java.io.FileInputStream

val inputStream = new FileInputStream("data.bin")
val byteFlow: Flow[Array[Byte]] = Flow.fromInputStream(inputStream, bufferSize = 8192)
```

Note: `fromInputStream` does NOT automatically close the InputStream. Use resource management patterns like `Using` to ensure proper cleanup.

#### From File

Create a flow that reads data from a file with automatic resource management:

```scala
import in.rcard.yaes.Flow
import java.nio.file.Paths

val fileFlow: Flow[Array[Byte]] = Flow.fromFile(Paths.get("data.txt"), bufferSize = 8192)
```

`fromFile` automatically manages the file's InputStream lifecycle — it opens the stream when collection starts and closes it when collection completes (either successfully or due to an exception):

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

// Copy file using toFile (automatic resource management)
Flow.fromFile(Paths.get("source.txt"))
  .toFile(Paths.get("copy.txt"))
```

### Collecting Flow Values

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

### Transformation Operators

#### map

Transform each emitted value:

```scala
import in.rcard.yaes.Flow

val stringFlow = Flow(1, 2, 3)
  .map(_.toString)
  .map("Number: " + _)

// Emits: "Number: 1", "Number: 2", "Number: 3"
```

#### filter

Keep only values matching a predicate:

```scala
import in.rcard.yaes.Flow

val evenNumbers = Flow(1, 2, 3, 4, 5, 6)
  .filter(_ % 2 == 0)

// Emits: 2, 4, 6
```

#### transform

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

#### take

Limit the number of emitted values:

```scala
import in.rcard.yaes.Flow

val limitedFlow = Flow(1, 2, 3, 4, 5)
  .take(3)

// Emits: 1, 2, 3
```

#### drop

Skip the first n values:

```scala
import in.rcard.yaes.Flow

val skippedFlow = Flow(1, 2, 3, 4, 5)
  .drop(2)

// Emits: 3, 4, 5
```

#### onEach

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

#### onStart

Execute an action before flow collection starts:

```scala
import in.rcard.yaes.Flow

val initFlow = Flow(1, 2, 3)
  .onStart {
    Flow.emit(0) // Prepend 0 to the flow
  }

// Emits: 0, 1, 2, 3
```

#### merge

Merge multiple flows into a single flow with non-deterministic interleaving:

```scala
import in.rcard.yaes.Flow

val flow1 = Flow(1, 2, 3)
val flow2 = Flow(4, 5, 6)
val merged = Flow.merge(flow1, flow2)

val result = scala.collection.mutable.ArrayBuffer[Int]()
merged.collect { value => result += value }
// result contains all elements from both flows (order depends on timing)
```

Concurrency is handled internally — no `Async` context is required from the caller. Each source flow is collected concurrently in its own fiber. The relative order of elements within each source is preserved, but the interleaving between sources is non-deterministic. The merged flow completes when all sources complete. If any source throws, the error propagates and remaining sources are cancelled.

Edge cases:
- `Flow.merge()` with no arguments produces an empty flow
- `Flow.merge(flow)` with a single argument returns that flow unchanged

### Terminal Operators

#### fold

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

#### count

Count the number of emitted values:

```scala
import in.rcard.yaes.Flow

val count = Flow("a", "b", "c", "d")
  .filter(_.length > 0)
  .count()

// Result: 4
```

### Working with Binary Data and Text

Flow provides powerful capabilities for working with InputStreams and decoding binary data into text.

#### Reading from InputStream

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

#### Decoding UTF-8 Text

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

This is especially important when processing files that contain emoji, non-Latin scripts, or special symbols.

#### Decoding with Custom Charsets

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

#### Processing Large Text Files

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

### Encoding Strings to Bytes

#### Encoding to UTF-8

Use `encodeToUtf8()` to convert strings to UTF-8 byte arrays:

```scala
import in.rcard.yaes.Flow

val flow = Flow("Hello", "World", "!")

val result = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
flow
  .encodeToUtf8()
  .collect { bytes =>
    result += bytes
  }
```

#### Encoding with Custom Charsets

Use `encodeTo()` to encode strings with a specific charset:

```scala
import in.rcard.yaes.Flow
import java.nio.charset.StandardCharsets

val flow = Flow("Hello", "世界")

val encoded = flow
  .encodeTo(StandardCharsets.UTF_16BE)
  .fold(Array.empty[Byte])(_ ++ _)

val decoded = new String(encoded, StandardCharsets.UTF_16BE)
// decoded == "Hello世界"
```

Supported charsets include UTF_8, UTF_16, UTF_16BE, UTF_16LE, ISO_8859_1, and US_ASCII.

If a character cannot be represented in the target charset, an `UnmappableCharacterException` is thrown.

### Writing to OutputStreams

Flow provides the `toOutputStream` method to write byte arrays directly to an `OutputStream`:

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

Combine string encoding with `toOutputStream` to write text files:

```scala
import in.rcard.yaes.Flow
import java.io.FileOutputStream
import scala.util.Using

val lines = List("First line", "Second line", "Third line with Unicode: 世界 😀")

Using(new FileOutputStream("output.txt")) { outputStream =>
  lines.asFlow()
    .map(_ + "\n")
    .encodeToUtf8()
    .toOutputStream(outputStream)
}
```

Key characteristics: terminal operator, skips empty arrays, single flush at the end, no auto-close (caller is responsible for closing the stream).

### Writing to Files

Flow provides the `toFile` method to write byte arrays directly to files with automatic resource management:

```scala
import in.rcard.yaes.Flow
import java.nio.file.Paths

// Write binary data
val data = Array[Byte](1, 2, 3, 4, 5)
Flow(data).toFile(Paths.get("output.bin"))

// Write text data
val lines = List("First line", "Second line", "Third line with Unicode: 世界 😀")

lines.asFlow()
  .map(_ + "\n")
  .encodeToUtf8()
  .toFile(Paths.get("output.txt"))
```

Key characteristics: terminal operator, automatic resource management, creates parent directories if needed, overwrites existing files.

### Processing Text Line by Line

Flow provides methods to split byte streams into lines, essential for working with CSV files, log files, and configuration files.

#### Reading Lines from Files

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
}
```

#### Reading Lines with Custom Encoding

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
    .collect { line => println(line) }
}
```

Line-reading characteristics:
- Universal line separator support: `\n` (LF), `\r\n` (CRLF), and `\r` (CR)
- Clean output: line separators are removed from emitted strings
- Empty line preservation: empty lines are maintained
- Last line handling: emits the last line even without a trailing separator
- Chunk boundary safety: correctly handles multi-byte characters and CRLF sequences split across chunk boundaries

### Practical Examples

#### Data Processing Pipeline

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

#### Async Data Transformation

```scala
import in.rcard.yaes.Flow
import in.rcard.yaes.Async.*

def processDataAsync(data: List[Int])(using Async): List[String] = {
  val results = scala.collection.mutable.ArrayBuffer[String]()

  data.asFlow()
    .map { value =>
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

#### Complex Data Pipeline

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
      try {
        val number = raw.value.toInt
        Flow.emit(ParsedData(number))
      } catch {
        case _: NumberFormatException =>
          Raise.raise(s"Invalid number format: ${raw.value}")
      }
    }
    .filter(_.number > 0)
    .map { parsed => ProcessedData(s"Result: ${parsed.number * 2}") }
    .take(10)
    .collect { processed => results += processed }

  results.toList
}
```

### Integration with λÆS Effects

Flow works seamlessly with λÆS effects:

```scala
import in.rcard.yaes.Flow
import in.rcard.yaes.Random.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Log.*
import in.rcard.yaes.Log.given

def randomDataProcessor(using Random, Output, Log): List[Int] = {
  val logger = Log.getLogger("RandomProcessor")
  val results = scala.collection.mutable.ArrayBuffer[Int]()

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
    .filter(_ > 50)
    .onEach { value => Output.printLn(s"Processing value: $value") }
    .map(_ * 2)
    .collect { result => results += result }

  logger.info(s"Processed ${results.length} values")
  results.toList
}

val result = Log.run() {
  Output.run {
    Random.run {
      randomDataProcessor
    }
  }
}
```

### Flow Best Practices

1. **Use appropriate operators**: `map` for 1:1 transformations, `transform` for 1:many, `filter` for selection, `fold` for aggregation
2. **Use `onEach` for side effects** that don't change the flow
3. **Combine with `Raise` for error handling**: use `transform` to emit or raise based on parsing results
4. **Flows are cold** — they don't do work until collected; chain operators efficiently
5. **Use `take`** to limit processing when appropriate

---

## Reactive Streams Integration

λÆS provides seamless integration with Java Reactive Streams through the `FlowPublisher` class. This bridge converts YAES `Flow[A]` instances into standard `java.util.concurrent.Flow.Publisher[A]` instances that can be consumed by any Reactive Streams-compliant library.

### What is FlowPublisher?

`FlowPublisher` converts YAES's push-based cold streams (`Flow`) into the pull-based, backpressure-enabled world of Reactive Streams (`Publisher`). This enables interoperability with Akka Streams, Project Reactor, RxJava, and other reactive libraries.

Use cases:
- **Framework Integration**: Integrate YAES flows with existing reactive libraries
- **Backpressure Control**: Leverage Reactive Streams' demand-driven backpressure
- **Standard Compliance**: Expose YAES flows through `java.util.concurrent.Flow.Publisher`

### Key Characteristics

**Cold Execution**: Like YAES Flows, FlowPublisher maintains cold execution semantics — nothing happens until `subscribe()` is called, and each subscription triggers a fresh, independent execution:

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

  publisher.subscribe(subscriber1)  // Prints "Flow execution started"
  publisher.subscribe(subscriber2)  // Prints "Flow execution started" again
}
```

**Demand-Driven Backpressure**: Elements are only delivered when the subscriber requests them via `request(n)`.

**Reactive Streams Specification Compliance**: FlowPublisher implements all required rules including Rule 1.1 (demand respect), Rule 1.3 (serial delivery), Rule 1.7 (no signals after termination), Rule 1.9 (non-null subscriber), Rule 2.13 (no null elements), and Rule 3.9 (positive request).

**Buffered Architecture**: FlowPublisher uses a Channel internally to buffer elements. Default: Bounded(16, SUSPEND).

**Cancellable**: Subscribers can cancel at any time with proper resource cleanup. Idempotent: calling `cancel()` multiple times is safe.

### Creating Publishers

#### Basic Creation

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val flow = Flow(1, 2, 3, 4, 5)

Async.run {
  val publisher = FlowPublisher.fromFlow(flow)
  publisher.subscribe(subscriber)
}
```

#### Extension Method Syntax

```scala 3
import in.rcard.yaes.FlowPublisher.asPublisher
import in.rcard.yaes.Async.*

val flow = Flow(1, 2, 3, 4, 5)

Async.run {
  val publisher = flow.asPublisher()  // Extension method
  publisher.subscribe(subscriber)
}
```

#### With Custom Buffer Configuration

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher, Channel}
import in.rcard.yaes.FlowPublisher.asPublisher

val flow = Flow(1 to 1000: _*)

// Factory method
val publisher1 = FlowPublisher.fromFlow(
  flow,
  Channel.Type.Bounded(64, Channel.OverflowStrategy.SUSPEND)
)

// Extension method
val publisher2 = flow.asPublisher(
  bufferCapacity = Channel.Type.Bounded(64, Channel.OverflowStrategy.SUSPEND)
)
```

#### Complete Example

```scala 3
import in.rcard.yaes.{Flow, FlowPublisher, Channel}
import in.rcard.yaes.FlowPublisher.asPublisher
import in.rcard.yaes.Async.*
import java.util.concurrent.Flow.{Subscriber, Subscription}

val flow = Flow.flow[String] {
  (1 to 100).foreach { i => Flow.emit(s"Message $i") }
}

Async.run {
  val publisher = flow.asPublisher(
    bufferCapacity = Channel.Type.Bounded(capacity = 32, overflowStrategy = Channel.OverflowStrategy.SUSPEND)
  )

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

FlowPublisher supports all Channel buffer types:

| Buffer Type | Pros | Cons | Use When |
|-------------|------|------|----------|
| Unbounded | Producer never blocks | Can grow without limit | Memory abundant, short-lived streams |
| Bounded + SUSPEND (default) | True backpressure, bounded memory | Producer may block when full | Backpressure required |
| Bounded + DROP_OLDEST | Producer never blocks | May lose oldest data | Latest data most important (live metrics) |
| Bounded + DROP_LATEST | Producer never blocks | May lose newest data | Historical data most important |

The default configuration `Channel.Type.Bounded(16, Channel.OverflowStrategy.SUSPEND)` provides a balanced starting point.

Buffer size selection guide:
- **Small (8-16)**: Low memory, tight control, potential throughput impact
- **Medium (32-128)**: Balanced memory and throughput
- **Large (256+)**: High throughput, higher memory, longer backpressure latency

### Subscriber Implementation

#### Basic Pattern

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

Key points: store the subscription for later `request()` and `cancel()` calls; must call `request(n)` at least once; `onError` and `onComplete` are mutually exclusive.

#### Demand Management Patterns

**Request All Upfront** — simple, maximum throughput, no backpressure:
```scala 3
override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  s.request(Long.MaxValue)
}
```

**Request in Batches** — balanced control and throughput:
```scala 3
val BATCH_SIZE = 10
var received = 0

override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  s.request(BATCH_SIZE)
}

override def onNext(item: Int): Unit = {
  process(item)
  received += 1
  if (received % BATCH_SIZE == 0) {
    subscription.request(BATCH_SIZE)
  }
}
```

**Request One at a Time** — maximum backpressure control, lower throughput:
```scala 3
override def onSubscribe(s: Subscription): Unit = {
  subscription = s
  s.request(1)
}

override def onNext(item: Int): Unit = {
  process(item)
  subscription.request(1)
}
```

### Backpressure Handling

FlowPublisher's backpressure mechanism:

1. Subscriber signals demand via `subscription.request(n)`
2. Publisher tracks demand with an atomic counter
3. Emitter waits for demand when counter is zero
4. Elements are delivered and demand decremented
5. Buffer absorbs temporary speed mismatches
6. With SUSPEND strategy, producer suspends when buffer is full

#### Example: Slow Consumer with Backpressure

```scala 3
val fastFlow = Flow(1 to 1000: _*)  // Fast producer: 1000 elements

Async.run {
  val publisher = fastFlow.asPublisher(
    Channel.Type.Bounded(10, Channel.OverflowStrategy.SUSPEND)
  )

  publisher.subscribe(new Subscriber[Int] {
    var subscription: Subscription = _

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(1)  // One element at a time
    }

    override def onNext(item: Int): Unit = {
      Thread.sleep(100)  // Slow consumer: 10 items/second
      println(s"Processed: $item")
      subscription.request(1)
    }

    override def onError(t: Throwable): Unit = println(s"Error: ${t.getMessage}")
    override def onComplete(): Unit = println("Processing complete")
  })
}
```

Result: producer is throttled by the consumer's pace through backpressure.

### Error Handling

Flow exceptions propagate to the subscriber's `onError` callback:

```scala 3
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
      println(s"Flow error: ${t.getMessage}")
    }
    // ...
  })
}
```

Null elements trigger `NullPointerException` as required by Rule 2.13. Invalid `request(n)` values trigger `IllegalArgumentException` as required by Rule 3.9.

**Error handling best practices:**
- Always implement `onError`
- Don't throw from error handlers
- Handle terminal events (`onError`/`onComplete`) symmetrically for resource cleanup

### Cancellation

```scala 3
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
      // NOT called when subscription is cancelled
    }
    // ...
  })
}
```

Cancellation is idempotent (`cancel()` can be called multiple times safely). After cancellation, no more signals are sent, and resources are cleaned up (fibers terminate, channel cancelled).

| Aspect | Cancellation | Normal Completion |
|--------|-------------|-------------------|
| Trigger | `cancel()` called | Flow finishes naturally |
| Terminal Event | None | `onComplete()` called |
| Element Count | Partial | All elements |
| Resource Cleanup | Immediate | After last element |

### Integration Examples

#### Integration with Akka Streams

```scala 3
import akka.stream.scaladsl.{Source, Sink, JavaFlowSupport}
import akka.actor.ActorSystem
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

given actorSystem: ActorSystem = ActorSystem("reactive-system")

val yaesFlow = Flow(1, 2, 3, 4, 5)

Async.run {
  val publisher = yaesFlow.asPublisher()
  val akkaSource = JavaFlowSupport.Source.fromPublisher(publisher)

  val result = akkaSource
    .map(_ * 2)
    .filter(_ > 5)
    .runWith(Sink.seq)
  // result: Future[Seq[Int]] = Future(Success(Seq(6, 8, 10)))
}
```

#### Integration with Project Reactor

```scala 3
import reactor.core.publisher.Flux
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val yaesFlow = Flow("a", "b", "c", "d", "e")

Async.run {
  val publisher = FlowPublisher.fromFlow(yaesFlow)
  val flux = Flux.from(publisher)

  flux
    .map(_.toUpperCase)
    .filter(_.length > 0)
    .subscribe(value => println(s"Received: $value"))
}
```

#### Integration with RxJava

```scala 3
import io.reactivex.rxjava3.core.Flowable
import in.rcard.yaes.{Flow, FlowPublisher}
import in.rcard.yaes.Async.*

val yaesFlow = Flow(1 to 100: _*)

Async.run {
  val publisher = yaesFlow.asPublisher()
  val flowable = Flowable.fromPublisher(publisher)

  flowable
    .buffer(10)  // Batch into groups of 10
    .map(_.sum)  // Sum each batch
    .subscribe(sum => println(s"Batch sum: $sum"))
}
```

### Performance Considerations

Per-subscription overhead: 2 fibers (~1KB each), 1 channel buffer, 1 AtomicLong, 2 AtomicBooleans, 1 Semaphore, 1 Subscription object.

**Throughput optimization tips:**
1. **Buffer size**: Larger buffers reduce coordination overhead. Start with default (16), increase if producer/consumer speeds differ significantly
2. **Batch requests**: Request multiple elements (10-100) for better throughput
3. **Fast `onNext`**: Keep processing minimal, delegate heavy work to background workers
4. **Avoid Unbounded**: Can cause out-of-memory errors with slow consumers

### Common Patterns

#### Batch Processing

```scala 3
publisher.subscribe(new Subscriber[Int] {
  var subscription: Subscription = _
  val batch = scala.collection.mutable.ArrayBuffer[Int]()
  val BATCH_SIZE = 10

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    s.request(BATCH_SIZE)
  }

  override def onNext(item: Int): Unit = {
    batch += item
    if (batch.size >= BATCH_SIZE) {
      processBatch(batch.toList)
      batch.clear()
      subscription.request(BATCH_SIZE)
    }
  }

  override def onComplete(): Unit = {
    if (batch.nonEmpty) processBatch(batch.toList)
  }

  override def onError(t: Throwable): Unit = println(s"Error: ${t.getMessage}")
})
```

#### Rate Limiting

```scala 3
publisher.subscribe(new Subscriber[Int] {
  var subscription: Subscription = _
  val RATE_LIMIT_MS = 100  // 10 requests/second

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    s.request(1)
  }

  override def onNext(item: Int): Unit = {
    makeApiRequest(item)
    Async.delay(RATE_LIMIT_MS.millis)
    subscription.request(1)
  }

  override def onComplete(): Unit = println("All requests completed")
  override def onError(t: Throwable): Unit = println(s"Error: ${t.getMessage}")
})
```

### FlowPublisher Best Practices Summary

| Category | Best Practice | Rationale |
|----------|--------------|-----------|
| Demand | Always call `request(n)` | Without request, no elements delivered |
| Demand | Use batch requests for throughput | Reduces coordination overhead |
| Errors | Always implement `onError` | Prevents silent failures |
| Cancellation | Cancel when done early | Frees resources promptly |
| Buffers | Start with default (16) | Balanced for most cases |
| Buffers | Use SUSPEND for backpressure | True backpressure control |
| Processing | Keep `onNext` fast | Maximize throughput |

---

## Channels

A `Channel` is a communication primitive for transferring data between asynchronous computations (fibers). Conceptually similar to `java.util.concurrent.BlockingQueue`, but with suspending operations instead of blocking ones and the ability to be closed.

Channels are particularly useful for:
- Sharing data between multiple fibers
- Implementing producer-consumer patterns
- Creating pipelines of asynchronous transformations
- Coordinating work between concurrent computations

### Channel Types

Channels support different buffer configurations that control how elements are buffered and when senders/receivers suspend.

#### Unbounded Channel

A channel with unlimited buffer capacity that never suspends the sender:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

val channel = Channel.unbounded[Int]()

Raise.run {
  Async.run {
    Async.fork {
      channel.send(1)
      channel.send(2)
      channel.send(3)
      channel.close()
    }

    println(channel.receive()) // 1
    println(channel.receive()) // 2
    println(channel.receive()) // 3
  }
}
```

#### Bounded Channel

A channel with a fixed buffer capacity. When the buffer is full, behavior depends on the configured overflow policy:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import scala.concurrent.duration.*

// Default behavior: suspend when full
val channel = Channel.bounded[Int](capacity = 2)

Raise.run {
  Async.run {
    Async.fork {
      channel.send(1) // Succeeds immediately
      channel.send(2) // Succeeds immediately
      channel.send(3) // Suspends until receiver takes an element
      println("All messages sent")
    }

    Async.delay(1.second)
    println(channel.receive()) // 1
    println(channel.receive()) // 2
    println(channel.receive()) // 3
  }
}
```

#### Buffer Overflow Policies

**SUSPEND (default)**: The sender suspends until space becomes available. Provides natural backpressure:

```scala
val channel = Channel.bounded[Int](capacity = 2, onOverflow = OverflowStrategy.SUSPEND)
```

**DROP_OLDEST**: When the buffer is full, the oldest element is dropped. Useful when only the most recent data matters:

```scala
val channel = Channel.bounded[Int](capacity = 3, onOverflow = OverflowStrategy.DROP_OLDEST)

Raise.run {
  Async.run {
    Async.fork {
      channel.send(1) // Buffer: [1]
      channel.send(2) // Buffer: [1, 2]
      channel.send(3) // Buffer: [1, 2, 3]
      channel.send(4) // Buffer: [2, 3, 4] — 1 is dropped
      channel.send(5) // Buffer: [3, 4, 5] — 2 is dropped
      channel.close()
    }

    Async.delay(100.millis)
    channel.foreach(println) // Prints: 3, 4, 5
  }
}
```

**DROP_LATEST**: When the buffer is full, the new element is discarded. Useful when preserving the earliest data matters:

```scala
val channel = Channel.bounded[Int](capacity = 3, onOverflow = OverflowStrategy.DROP_LATEST)

Raise.run {
  Async.run {
    Async.fork {
      channel.send(1) // Buffer: [1]
      channel.send(2) // Buffer: [1, 2]
      channel.send(3) // Buffer: [1, 2, 3]
      channel.send(4) // Buffer: [1, 2, 3] — 4 is dropped
      channel.send(5) // Buffer: [1, 2, 3] — 5 is dropped
      channel.close()
    }

    Async.delay(100.millis)
    channel.foreach(println) // Prints: 1, 2, 3
  }
}
```

#### Rendezvous Channel

A channel with no buffer. The sender and receiver must meet: `send` suspends until another computation invokes `receive`, and vice versa:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

val channel = Channel.rendezvous[String]()

Raise.run {
  Async.run {
    val sender = Async.fork {
      println("Sender: waiting for receiver...")
      channel.send("hello") // Suspends until receiver calls receive
      println("Sender: message delivered!")
    }

    Async.delay(1.second)
    println("Receiver: ready to receive...")
    val msg = channel.receive() // Both sender and receiver meet here
    println(s"Receiver: got $msg")
  }
}
```

### Basic Operations

Channels are composed of two interfaces:

```scala
trait SendChannel[T] {
  def send(value: T)(using Raise[ChannelClosed]): Unit
  def close(): Boolean
}

trait ReceiveChannel[T] {
  def receive()(using Raise[ChannelClosed]): T
  def cancel(): Unit
}
```

- **send**: Sends an element, suspending if necessary. Raises `ChannelClosed` if the channel is closed.
- **close**: Closes the channel, preventing further sends. Receivers can still consume buffered elements.
- **receive**: Receives an element, suspending if the channel is empty. Raises `ChannelClosed` when the channel is closed and empty.
- **cancel**: Cancels the channel, clearing all buffered elements.

### Using Channels Without Async Context

As of version 0.11.0, basic channel operations (`send`, `receive`, `cancel`, `foreach`) no longer require an `Async` context parameter:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Raise.*

val channel = Channel.unbounded[Int]()

// Works with only Raise context — no Async needed
val result = Raise.run {
  channel.send(42)
  channel.send(43)
  channel.receive() + channel.receive()
}
// result: 85
```

Builder functions that fork fibers still require `Async`: `Channel.produce`, `Channel.produceWith`, `Channel.channelFlow`, `Channel.channelFlowWith`, and `Flow.buffer`.

### Producer Pattern

The `produce` function provides a convenient DSL for creating channels with producer coroutines:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.Producer
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

Raise.run {
  Async.run {
    val channel = Channel.produce[Int] {
      (1 to 10).foreach { i =>
        Producer.send(i * i)
      }
      // Channel automatically closed when block completes
    }

    channel.foreach { value =>
      println(s"Square: $value")
    }
  }
}
```

Use `produceWith` to specify the channel type:

```scala
// Create a bounded producer
val channel = Channel.produceWith(Channel.Type.Bounded(5)) {
  var count = 0
  while (count < 100) {
    Producer.send(count)
    count += 1
  }
}
```

### Iteration

Use the `foreach` extension method to iterate over all elements in a channel:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

val channel = Channel.unbounded[Int]()

Raise.run {
  Async.run {
    Async.fork {
      (1 to 5).foreach(channel.send)
      channel.close()
    }

    channel.foreach { value =>
      println(s"Processing: $value")
    }
    println("All elements processed")
  }
}
```

### Practical Examples

#### Producer-Consumer Pattern

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Log.*
import in.rcard.yaes.Log.given
import scala.concurrent.duration.*

case class Task(id: Int, data: String)

def producerConsumerExample()(using Log): Unit = {
  val logger = Log.getLogger("ProducerConsumer")

  Raise.run {
    Async.run {
      val channel = Channel.bounded[Task](10)

      val producer = Async.fork {
        logger.info("Producer started")
        for (i <- 1 to 20) {
          val task = Task(i, s"data-$i")
          channel.send(task)
          logger.debug(s"Produced task $i")
          Async.delay(100.millis)
        }
        channel.close()
        logger.info("Producer finished")
      }

      val consumer = Async.fork {
        logger.info("Consumer started")
        channel.foreach { task =>
          logger.debug(s"Processing task ${task.id}")
          Async.delay(200.millis)
        }
        logger.info("Consumer finished")
      }
    }
  }
}

Log.run() {
  producerConsumerExample()
}
```

#### Pipeline Pattern

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.Producer
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

case class RawData(value: Int)
case class ProcessedData(result: String)

def pipelineExample(): List[ProcessedData] = {
  Raise.run {
    Async.run {
      // Stage 1: Generate raw data
      val rawChannel = Channel.produce[RawData] {
        (1 to 10).foreach { i => Producer.send(RawData(i)) }
      }

      // Stage 2: Process data
      val processedChannel = Channel.produce[ProcessedData] {
        rawChannel.foreach { raw =>
          val processed = ProcessedData(s"Processed-${raw.value * 2}")
          Producer.send(processed)
        }
      }

      // Stage 3: Collect results
      val results = scala.collection.mutable.ArrayBuffer[ProcessedData]()
      processedChannel.foreach { processed => results += processed }
      results.toList
    }
  }
}
```

#### Fan-Out Pattern (Multiple Consumers)

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import scala.concurrent.duration.*

def fanOutExample(): Unit = {
  Raise.run {
    Async.run {
      val channel = Channel.unbounded[Int]()

      val producer = Async.fork {
        (1 to 20).foreach { i =>
          channel.send(i)
          Async.delay(50.millis)
        }
        channel.close()
      }

      val consumers = (1 to 3).map { consumerId =>
        Async.fork {
          channel.foreach { value =>
            println(s"Consumer $consumerId processing: $value")
            Async.delay(100.millis)
          }
        }
      }

      consumers.foreach(_.join())
    }
  }
}
```

### Channel Best Practices

#### 1. Choose the Right Channel Type

- **Unbounded**: Use when memory is not a concern and you want maximum throughput
- **Bounded with SUSPEND**: Use for backpressure and controlled memory usage
- **Bounded with DROP_OLDEST**: Use when only the most recent data matters (e.g., sensor readings)
- **Bounded with DROP_LATEST**: Use when the earliest data is most important (e.g., event logs)
- **Rendezvous**: Use when you need strict synchronization

#### 2. Always Close Channels

Ensure channels are properly closed to signal completion to receivers. The `produce` pattern automatically closes channels:

```scala
val channel = Channel.produce[Int] {
  (1 to 5).foreach(Producer.send)
  // Automatically closed
}
```

When closing manually, use a `finally` block:

```scala
Async.fork {
  try {
    (1 to 5).foreach(channel.send)
  } finally {
    channel.close()
  }
}
```

#### 3. Handle ChannelClosed Errors

```scala
val result = Raise.either {
  Async.run {
    val channel = Channel.unbounded[Int]()
    channel.close()
    channel.send(1) // Raises ChannelClosed
  }
}

result match {
  case Left(Channel.ChannelClosed) => println("Channel was closed")
  case Right(_) => println("Success")
}
```

#### 4. Separate Concerns with SendChannel and ReceiveChannel

Pass only the needed capability to producers and consumers:

```scala
def producer(channel: SendChannel[Int])(using Async, Raise[Channel.ChannelClosed]): Unit = {
  (1 to 5).foreach(channel.send)
  channel.close()
}

def consumer(channel: ReceiveChannel[Int])(using Async, Raise[Channel.ChannelClosed]): List[Int] = {
  val results = scala.collection.mutable.ArrayBuffer[Int]()
  channel.foreach { value => results += value }
  results.toList
}
```

### Performance Considerations

- **Unbounded channels** can lead to unbounded memory usage if producers are faster than consumers
- **Bounded channels with SUSPEND** provide natural backpressure but may cause producers to suspend
- **Bounded channels with DROP strategies** never suspend but may lose data
- **Rendezvous channels** provide the strongest synchronization but the lowest throughput
- Use appropriate buffer sizes for bounded channels based on your workload

### Channel Patterns Summary

| Pattern | Channel Type | Use Case |
|---------|-------------|----------|
| Producer-Consumer | Bounded | Single producer, single consumer with backpressure |
| Fan-Out | Unbounded | Single producer, multiple consumers |
| Pipeline | Any | Chain of processing stages |
| Buffered Communication | Bounded | Smooth out bursty producers |
| Strict Synchronization | Rendezvous | Sender and receiver must coordinate |

---

## Combining Flows with Channels

Channels and Flows complement each other. Channels provide concurrent communication, while Flows provide composable transformation pipelines. The `channelFlow` and `buffer` functions bridge these worlds.

### channelFlow Builder

The `channelFlow` function creates a cold `Flow` where elements are emitted through a `Producer` context parameter backed by a channel. This combines the concurrent power of channels with the composability of flows:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*

val flow = Channel.channelFlow[Int] {
  Channel.Producer.send(1)
  Channel.Producer.send(2)
  Channel.Producer.send(3)
}

val result = scala.collection.mutable.ArrayBuffer[Int]()
flow.collect { value => result += value }
// result: ArrayBuffer(1, 2, 3)
```

Use `channelFlowWith` to specify a different channel type:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*

val flow = Channel.channelFlowWith[Int](Channel.Type.Bounded(5)) {
  (1 to 100).foreach(Channel.Producer.send)
}
```

#### Concurrent Emission

A key advantage of `channelFlow` is support for concurrent emission from multiple fibers:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*

val flow = Channel.channelFlow[Int] {
  val fiber1 = Async.fork {
    Channel.Producer.send(1)
    Async.delay(50) // Simulate work
    Channel.Producer.send(2)
  }

  val fiber2 = Async.fork {
    Channel.Producer.send(3)
    Async.delay(50) // Simulate work
    Channel.Producer.send(4)
  }
}

val result = scala.collection.mutable.ArrayBuffer[Int]()
flow.collect { value => result += value }
// result contains all four values (order may vary due to concurrency)
```

#### Merging Multiple Flows

`channelFlow` is excellent for implementing flow operators that merge multiple sources:

```scala
import in.rcard.yaes.{Channel, Flow}
import in.rcard.yaes.Async.*

def merge[T](flow1: Flow[T], flow2: Flow[T]): Flow[T] =
  Channel.channelFlow[T] {
    val fiber1 = Async.fork {
      flow1.collect { value => Channel.Producer.send(value) }
    }

    val fiber2 = Async.fork {
      flow2.collect { value => Channel.Producer.send(value) }
    }
  }

val numbers = Flow(1, 2, 3)
val letters = Flow("a", "b", "c")

val combined = scala.collection.mutable.ArrayBuffer[Any]()
merge(numbers, letters).collect { value => combined += value }
// combined contains all six elements
```

#### Cold Flow Behavior

Like all flows in yaes, `channelFlow` creates a cold flow. The builder block executes every time `collect` is called:

```scala
val flow = Channel.channelFlow[Int] {
  println("Executing builder")
  Channel.Producer.send(1)
  Channel.Producer.send(2)
}

flow.collect { _ => } // Prints "Executing builder"
flow.collect { _ => } // Prints "Executing builder" again
```

#### Design Decision: Why channelFlow Doesn't Require External Async

You might notice that `channelFlow` doesn't require an external `Async` effect, unlike combinators such as `par`, `race`, or `zipWith`. This is intentional:

| Category | Examples | Async Required | Reason |
|----------|----------|----------------|--------|
| **Combinators** | `par`, `race`, `zipWith` | Yes (external) | Compose existing computations; caller controls concurrency scope |
| **Builders** | `channelFlow`, `Flow.flow` | No (internal) | Encapsulate their own effects; `Async.run` is part of `collect` implementation |

This approach ensures API consistency (all `Flow` operations don't require external `Async`), composability (flows can be used anywhere a `Flow[T]` is expected), and cold semantics (each collection triggers a fresh concurrent computation).

#### channelFlow vs produce/produceWith

| Feature | `produce`/`produceWith` | `channelFlow`/`channelFlowWith` |
|---------|------------------------|--------------------------------|
| Return type | `ReceiveChannel[T]` | `Flow[T]` |
| Execution | Hot (starts immediately) | Cold (starts on collect) |
| Composition | Channel operations | Flow operators (map, filter, etc.) |
| Concurrency | Supported | Supported |
| Use case | Direct channel consumption | Flow pipelines and transformations |

Choose `channelFlow` when you need flow composition and cold execution semantics. Choose `produce` when you need a hot channel that starts producing immediately.

### Flow Buffering with `buffer`

The `buffer` operator buffers flow emissions via a channel, allowing the producer (upstream flow) and consumer (downstream collector) to run concurrently. This can significantly improve performance when emissions and collection have different speeds.

```scala
import in.rcard.yaes.{Channel, Flow}
import in.rcard.yaes.Channel.buffer

val flow = Flow(1, 2, 3, 4, 5)

val result = scala.collection.mutable.ArrayBuffer[Int]()
flow.buffer().collect { value => result += value }
// result contains: 1, 2, 3, 4, 5
```

By default, `buffer` uses an unbounded channel. For backpressure control, use a bounded channel:

```scala
flow.buffer(Channel.Type.Bounded(2)).collect { value => result += value }
```

With DROP strategies for sampling scenarios:

```scala
import in.rcard.yaes.{Channel, Flow}
import in.rcard.yaes.Channel.{buffer, OverflowStrategy}
import in.rcard.yaes.Async.*
import scala.concurrent.duration.*

// DROP_OLDEST: drops oldest buffered values when full
Async.run {
  Flow(1, 2, 3, 4, 5)
    .buffer(Channel.Type.Bounded(2, OverflowStrategy.DROP_OLDEST))
    .collect { value =>
      Async.delay(100.millis) // Slow consumer
      println(value)
    }
}
```

Key characteristics of `buffer`:
- **Cold operator**: The producer doesn't start until `collect` is called
- **Concurrent execution**: Producer and consumer run in separate fibers
- **Error propagation**: Errors from producer or consumer are properly propagated
- **Channel cleanup**: The underlying channel is properly closed on completion or error

Use `buffer` when:
- Producer is faster than consumer and you want to avoid blocking
- You want to decouple emission and collection rates
- You need backpressure with a bounded buffer
- You can tolerate data loss with DROP strategies

### Integration with λÆS Effects

Channels work seamlessly with λÆS effects:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.Producer
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Log.*
import in.rcard.yaes.Log.given
import in.rcard.yaes.Random.*

def effectfulChannelExample()(using Log, Random): Unit = {
  val logger = Log.getLogger("ChannelExample")

  Raise.run {
    Async.run {
      val channel = Channel.produce[Int] {
        logger.info("Starting production")
        (1 to 10).foreach { _ =>
          val randomValue = Random.nextInt(100)
          logger.debug(s"Producing: $randomValue")
          Producer.send(randomValue)
        }
        logger.info("Production complete")
      }

      val filtered = scala.collection.mutable.ArrayBuffer[Int]()
      channel.foreach { value =>
        if (value > 50) {
          logger.debug(s"Accepted: $value")
          filtered += value
        } else {
          logger.debug(s"Rejected: $value")
        }
      }

      logger.info(s"Processed ${filtered.size} values")
    }
  }
}

Log.run() {
  Random.run {
    effectfulChannelExample()
  }
}
```

---

## What's Next

You now have the tools to work with reactive data streams and concurrent communication in λÆS. Proceed to [Step 8: Building Applications](/yaes/learn/8-building-applications/) to see how all these pieces — effects, error handling, concurrency, state, and streams — come together in complete applications.
