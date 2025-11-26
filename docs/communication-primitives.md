---
layout: default
title: "Communication Primitives"
---

# Communication Primitives

Beyond effects, λÆS provides communication primitives for coordinating between asynchronous computations. These primitives are part of the `yaes-core` module and enable structured communication patterns between fibers.

## Available Primitives

### [Channel](#channel)
A communication primitive for transferring data between asynchronous computations, similar to blocking queues but with suspending operations.

---

## Channel

A `Channel` is a communication primitive for transferring data between asynchronous computations (fibers). Conceptually, a channel is similar to `java.util.concurrent.BlockingQueue`, but it has suspending operations instead of blocking ones and can be closed.

Channels are particularly useful when you need to:
- Share data between multiple fibers
- Implement producer-consumer patterns
- Create pipelines of asynchronous transformations
- Coordinate work between concurrent computations

### Channel Types

Channels support different buffer configurations that control how elements are buffered and when senders/receivers suspend:

#### Unbounded Channel

A channel with unlimited buffer capacity that never suspends the sender. Elements are buffered in an unlimited queue. This channel type is suitable when you need to ensure that senders never block, but be aware that memory usage can grow without bounds.

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

val channel = Channel.unbounded[Int]()

Raise.run {
  Async.run {
    Async.fork {
      // These sends will never suspend
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

A channel with a fixed buffer capacity. When the buffer is full, behavior depends on the configured overflow policy.

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.OverflowStrategy
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

##### Buffer Overflow Policies

Bounded channels support different strategies for handling buffer overflow, controlled by the `onOverflow` parameter:

**SUSPEND (default)**: The sender suspends until space becomes available in the buffer. This provides natural backpressure to prevent fast producers from overwhelming slow consumers.

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.OverflowStrategy

val channel = Channel.bounded[Int](capacity = 2, onOverflow = OverflowStrategy.SUSPEND)
// Equivalent to: Channel.bounded[Int](capacity = 2)
```

**DROP_OLDEST**: When the buffer is full, the oldest element is dropped to make space for the new element. The send operation completes immediately without suspending. This is useful when you only care about the most recent data.

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.OverflowStrategy
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

val channel = Channel.bounded[Int](capacity = 3, onOverflow = OverflowStrategy.DROP_OLDEST)

Raise.run {
  Async.run {
    Async.fork {
      // All sends complete immediately
      channel.send(1) // Buffer: [1]
      channel.send(2) // Buffer: [1, 2]
      channel.send(3) // Buffer: [1, 2, 3]
      channel.send(4) // Buffer: [2, 3, 4] - 1 is dropped
      channel.send(5) // Buffer: [3, 4, 5] - 2 is dropped
      channel.close()
    }

    Async.delay(100.millis)
    
    // Only receives the last 3 elements
    channel.foreach(println) // Prints: 3, 4, 5
  }
}
```

**DROP_LATEST**: When the buffer is full, the new element is discarded and the buffer remains unchanged. The send operation completes immediately without suspending. This is useful when you want to preserve the earliest data.

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.OverflowStrategy
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

val channel = Channel.bounded[Int](capacity = 3, onOverflow = OverflowStrategy.DROP_LATEST)

Raise.run {
  Async.run {
    Async.fork {
      // All sends complete immediately
      channel.send(1) // Buffer: [1]
      channel.send(2) // Buffer: [1, 2]
      channel.send(3) // Buffer: [1, 2, 3]
      channel.send(4) // Buffer: [1, 2, 3] - 4 is dropped
      channel.send(5) // Buffer: [1, 2, 3] - 5 is dropped
      channel.close()
    }

    Async.delay(100.millis)
    
    // Only receives the first 3 elements
    channel.foreach(println) // Prints: 1, 2, 3
  }
}
```

#### Rendezvous Channel

A channel with no buffer. The sender and receiver must meet (rendezvous): `send` suspends until another computation invokes `receive`, and vice versa. This provides the strongest synchronization between sender and receiver.

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

## Basic Operations

Channels are composed of two interfaces:

### SendChannel

The send-only side of a channel, providing operations for sending elements and closing it:

```scala
trait SendChannel[T] {
  def send(value: T)(using Async, Raise[ChannelClosed]): Unit
  def close(): Boolean
}
```

**send**: Sends an element to the channel, suspending if necessary. If the channel's buffer is full (for bounded channels) or if there's no receiver ready (for rendezvous channels), this operation suspends until space becomes available or a receiver is ready.

**close**: Closes the channel, preventing further sends. After closing, no more elements can be sent. Receivers can still receive remaining buffered elements. Once all buffered elements are consumed, receive operations will raise `ChannelClosed`.

### ReceiveChannel

The receive-only side of a channel, providing operations for receiving elements and canceling it:

```scala
trait ReceiveChannel[T] {
  def receive()(using Async, Raise[ChannelClosed]): T
  def cancel()(using Async): Unit
}
```

**receive**: Receives an element from the channel, suspending if necessary. If the channel is empty, this operation suspends until an element becomes available. If the channel is closed and empty, it raises `ChannelClosed`.

**cancel**: Cancels the channel, clearing all buffered elements. After cancellation, all buffered elements are discarded, and ongoing operations are interrupted.

## Producer Pattern

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

You can also specify the channel type with `produceWith`:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.Producer
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import scala.concurrent.duration.*

Raise.run {
  Async.run {
    // Create a bounded producer
    val channel = Channel.produceWith(Channel.Type.Bounded(5)) {
      var count = 0
      while (count < 100) {
        Producer.send(count)
        count += 1
      }
    }

    // Consume with backpressure
    channel.foreach { value =>
      Async.delay(100.millis) // Slow consumer
      println(value)
    }
  }
}
```

### Channel Flow Builder

The `channelFlow` and `channelFlowWith` functions create a bridge between channels and flows. They build cold flows where elements are emitted through a `Producer` context parameter. This approach combines the concurrent communication power of channels with the composability of flows.

#### Basic Usage

The `channelFlow` function creates a flow with an unbounded channel:

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

The `Producer[T]` is available as a context parameter using Scala 3's context function syntax (`?=>`), allowing you to call `Channel.Producer.send()` directly within the builder block.

#### Custom Channel Types

Use `channelFlowWith` to specify a different channel type (bounded with overflow strategy or rendezvous):

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

// Using a bounded channel with capacity 5
val flow = Channel.channelFlowWith[Int](Channel.Type.Bounded(5)) {
  (1 to 100).foreach(Channel.Producer.send)
}

val result = scala.collection.mutable.ArrayBuffer[Int]()
flow.collect { value => result += value }
// result: ArrayBuffer(1, 2, ..., 100)
```

Available channel types:
- `Channel.Type.Unbounded`: No capacity limit (default for `channelFlow`)
- `Channel.Type.Bounded(capacity)`: Limited capacity with SUSPEND overflow strategy
- `Channel.Type.Bounded(capacity, overflowStrategy)`: Custom overflow behavior
- `Channel.Type.Rendezvous`: Zero capacity, requires sender-receiver rendezvous

#### Concurrent Emission

One of the key advantages of `channelFlow` is support for concurrent emission from multiple fibers:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

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
import in.rcard.yaes.Raise.*

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
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*

val flow = Channel.channelFlow[Int] {
  println("Executing builder")
  Channel.Producer.send(1)
  Channel.Producer.send(2)
}

flow.collect { _ => } // Prints "Executing builder"
flow.collect { _ => } // Prints "Executing builder" again
```

#### Comparison with Producer Pattern

| Feature | `produce`/`produceWith` | `channelFlow`/`channelFlowWith` |
|---------|------------------------|--------------------------------|
| Return type | `ReceiveChannel[T]` | `Flow[T]` |
| Execution | Hot (starts immediately) | Cold (starts on collect) |
| Composition | Channel operations | Flow operators (map, filter, etc.) |
| Concurrency | Supported | Supported |
| Use case | Direct channel consumption | Flow pipelines and transformations |

Choose `channelFlow` when you need flow composition and cold execution semantics. Choose `produce` when you need a hot channel that starts producing immediately.

#### Design Decision: Internal vs External `Async` Context

You might notice that `channelFlow` doesn't require an external `Async` effect to run, unlike combinators such as `par`, `race`, or `zipWith`. This is an intentional design choice based on the distinction between **builders** and **combinators**:

| Category | Examples | `Async` Required | Reason |
|----------|----------|------------------|--------|
| **Combinators** | `par`, `race`, `zipWith` | Yes (external) | Compose existing computations; caller controls concurrency scope |
| **Builders** | `channelFlow`, `Flow.flow` | No (internal) | Encapsulate their own effects; `Async.run` is part of `collect` implementation |

**Why this matters:**

1. **API Consistency**: All `Flow` operations (like `map`, `filter`, `collect`) don't require `Async` externally. If `channelFlow` required it, the resulting flow would leak implementation details to callers.

2. **Composability**: A flow created by `channelFlow` can be used anywhere a `Flow[T]` is expected, without special handling.

3. **Cold Semantics**: The `Async.run` is invoked internally when `collect` is called, ensuring each collection triggers a fresh concurrent computation—preserving the cold flow contract.

4. **Encapsulation**: The builder pattern encapsulates complexity. Users of the flow don't need to know (or care) that channels and fibers are used internally.

This approach mirrors how Kotlin's `channelFlow` manages its own coroutine scope internally, providing a clean separation between flow creation and flow consumption.

### Flow Buffering with `buffer`

The `buffer` operator buffers flow emissions via a channel, allowing the producer (upstream flow) and consumer (downstream collector) to run concurrently. This can significantly improve performance when emissions and collection have different speeds.

#### Basic Usage

```scala
import in.rcard.yaes.{Channel, Flow}
import in.rcard.yaes.Channel.buffer

val flow = Flow(1, 2, 3, 4, 5)

val result = scala.collection.mutable.ArrayBuffer[Int]()
flow.buffer().collect { value => result += value }
// result contains: 1, 2, 3, 4, 5
```

By default, `buffer` uses an unbounded channel, meaning the producer will never suspend due to buffer capacity.

#### With Bounded Buffer

For backpressure control, use a bounded channel:

```scala
import in.rcard.yaes.{Channel, Flow}
import in.rcard.yaes.Channel.buffer

val flow = Flow(1, 2, 3, 4, 5)

val result = scala.collection.mutable.ArrayBuffer[Int]()
flow.buffer(Channel.Type.Bounded(2)).collect { value => result += value }
// result contains: 1, 2, 3, 4, 5
```

With a bounded buffer and the default `SUSPEND` strategy, the producer will suspend when the buffer is full, providing natural backpressure.

#### With Rendezvous Channel

For strict synchronization between producer and consumer:

```scala
import in.rcard.yaes.{Channel, Flow}
import in.rcard.yaes.Channel.buffer

val flow = Flow(1, 2, 3)

val result = scala.collection.mutable.ArrayBuffer[Int]()
flow.buffer(Channel.Type.Rendezvous).collect { value => result += value }
// result contains: 1, 2, 3
```

#### Overflow Strategies

Bounded channels support different overflow strategies:

```scala
import in.rcard.yaes.{Channel, Flow}
import in.rcard.yaes.Channel.{buffer, OverflowStrategy}
import in.rcard.yaes.Async.*
import scala.concurrent.duration.*

// DROP_OLDEST: drops oldest buffered values when full
Async.run {
  val flow1 = Flow(1, 2, 3, 4, 5)
  flow1.buffer(Channel.Type.Bounded(2, OverflowStrategy.DROP_OLDEST)).collect { value =>
    Async.delay(100.millis) // Slow consumer
    println(value)
  }
}
// May print: 1, 4, 5 (oldest values dropped when buffer overflows)

// DROP_LATEST: drops new values when buffer is full
Async.run {
  val flow2 = Flow(1, 2, 3, 4, 5)
  flow2.buffer(Channel.Type.Bounded(2, OverflowStrategy.DROP_LATEST)).collect { value =>
    Async.delay(100.millis) // Slow consumer
    println(value)
  }
}
// May print: 1, 2, 3 (latest values dropped when buffer overflows)
```

#### Key Characteristics

| Feature | Description |
|---------|-------------|
| **Cold operator** | The producer doesn't start until `collect` is called |
| **Concurrent execution** | Producer and consumer run in separate fibers |
| **Error propagation** | Errors from producer or consumer are properly propagated |
| **Channel cleanup** | The underlying channel is properly closed on completion or error |

#### When to Use `buffer`

- **Performance**: When producer is faster than consumer and you want to avoid blocking
- **Decoupling**: When you want to decouple the emission and collection rates
- **Backpressure**: Use bounded buffer with `SUSPEND` to apply backpressure to fast producers
- **Sampling**: Use `DROP_OLDEST` or `DROP_LATEST` when you can tolerate data loss

## Iteration

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

## Practical Examples

### Producer-Consumer Pattern

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Log.*
import scala.concurrent.duration.*

case class Task(id: Int, data: String)

def producerConsumerExample()(using Log): Unit = {
  val logger = Log.getLogger("ProducerConsumer")
  
  Raise.run {
    Async.run {
      val channel = Channel.bounded[Task](10)

      // Producer fiber
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

      // Consumer fibers
      val consumer1 = Async.fork {
        logger.info("Consumer 1 started")
        channel.foreach { task =>
          logger.debug(s"Consumer 1 processing task ${task.id}")
          Async.delay(200.millis) // Simulate work
        }
        logger.info("Consumer 1 finished")
      }
    }
  }
}

// Usage
Log.run {
  producerConsumerExample()
}
```

### Pipeline Pattern

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
        (1 to 10).foreach { i =>
          Producer.send(RawData(i))
        }
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
      processedChannel.foreach { processed =>
        results += processed
      }
      
      results.toList
    }
  }
}
```

### Fan-Out Pattern (Multiple Consumers)

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import scala.concurrent.duration.*

def fanOutExample(): Unit = {
  Raise.run {
    Async.run {
      val channel = Channel.unbounded[Int]()

      // Single producer
      val producer = Async.fork {
        (1 to 20).foreach { i =>
          channel.send(i)
          Async.delay(50.millis)
        }
        channel.close()
      }

      // Multiple consumers
      val consumers = (1 to 3).map { consumerId =>
        Async.fork {
          channel.foreach { value =>
            println(s"Consumer $consumerId processing: $value")
            Async.delay(100.millis)
          }
        }
      }

      // Wait for all consumers
      consumers.foreach(_.join())
    }
  }
}
```

### Buffered Communication

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Output.*
import scala.concurrent.duration.*

def bufferedCommunicationExample(): Unit = {
  Output.run {
    Raise.run {
      Async.run {
        val channel = Channel.bounded[String](3)

        val sender = Async.fork {
          val messages = List("msg1", "msg2", "msg3", "msg4", "msg5")
          messages.foreach { msg =>
            Output.printLn(s"Sending: $msg")
            channel.send(msg)
            Output.printLn(s"Sent: $msg")
          }
          channel.close()
        }

        Async.delay(2.seconds)
        
        channel.foreach { msg =>
          Output.printLn(s"Received: $msg")
          Async.delay(1.second)
        }
      }
    }
  }
}
```

## Best Practices

### 1. Choose the Right Channel Type

- **Unbounded**: Use when memory is not a concern and you want maximum throughput
- **Bounded with SUSPEND**: Use for backpressure and controlled memory usage
- **Bounded with DROP_OLDEST**: Use when only the most recent data matters (e.g., sensor readings, real-time updates)
- **Bounded with DROP_LATEST**: Use when the earliest data is most important (e.g., event logs, audit trails)
- **Rendezvous**: Use when you need strict synchronization between sender and receiver

### 2. Always Close Channels

Ensure channels are properly closed to signal completion to receivers:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

Raise.run {
  Async.run {
    val channel = Channel.unbounded[Int]()
    
    Async.fork {
      try {
        (1 to 5).foreach(channel.send)
      } finally {
        channel.close() // Always close in finally block
      }
    }
  }
}
```

Or use the `produce` pattern which automatically closes:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.Producer
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

Raise.run {
  Async.run {
    val channel = Channel.produce[Int] {
      (1 to 5).foreach(Producer.send)
      // Automatically closed
    }
  }
}
```

### 3. Handle ChannelClosed Errors

Use the `Raise` effect to handle `ChannelClosed` errors appropriately:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

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

### 4. Separate Concerns with SendChannel and ReceiveChannel

Pass only the needed capability to producers and consumers:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.{SendChannel, ReceiveChannel}
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

def producer(channel: SendChannel[Int])(using Async, Raise[Channel.ChannelClosed]): Unit = {
  (1 to 5).foreach(channel.send)
  channel.close()
}

def consumer(channel: ReceiveChannel[Int])(using Async, Raise[Channel.ChannelClosed]): List[Int] = {
  val results = scala.collection.mutable.ArrayBuffer[Int]()
  channel.foreach { value =>
    results += value
  }
  results.toList
}

val result = Raise.run {
  Async.run {
    val channel = Channel.unbounded[Int]()
    
    Async.fork {
      producer(channel)
    }
    
    consumer(channel)
  }
}
```

### 5. Consider Cancellation

Use `cancel()` when you need to abort channel operations and clear buffers:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import scala.concurrent.duration.*

Raise.run {
  Async.run {
    val channel = Channel.unbounded[Int]()
    
    Async.fork {
      (1 to 100).foreach { i =>
        channel.send(i)
        Async.delay(100.millis)
      }
    }
    
    Async.delay(1.second)
    channel.cancel() // Clear all buffered messages
  }
}
```

## Integration with Effects

Channels work seamlessly with λÆS effects:

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Channel.Producer
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Log.*
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

// Usage
Log.run {
  Random.run {
    effectfulChannelExample()
  }
}
```

## Performance Considerations

- **Unbounded channels** can lead to unbounded memory usage if producers are faster than consumers
- **Bounded channels with SUSPEND** provide natural backpressure but may cause producers to suspend
- **Bounded channels with DROP_OLDEST/DROP_LATEST** never suspend but may lose data; use when data loss is acceptable
- **Rendezvous channels** provide the strongest synchronization but the lowest throughput
- Use appropriate buffer sizes for bounded channels based on your workload
- Consider the trade-offs between throughput, latency, memory usage, and data loss tolerance
- DROP_OLDEST is ideal for monitoring scenarios where only current state matters
- DROP_LATEST is ideal for preserving historical data when buffer fills

## Thread Safety

Channels are thread-safe and can be safely shared between multiple fibers. All operations are properly synchronized internally.

## Common Patterns Summary

| Pattern | Channel Type | Use Case |
|---------|-------------|----------|
| Producer-Consumer | Bounded | Single producer, single consumer with backpressure |
| Fan-Out | Unbounded | Single producer, multiple consumers |
| Pipeline | Any | Chain of processing stages |
| Buffered Communication | Bounded | Smooth out bursty producers |
| Strict Synchronization | Rendezvous | Sender and receiver must coordinate |

## See Also

- [Async Effect](effects/async.html) - For fiber management and structured concurrency
- [Raise Effect](effects/raise.html) - For error handling with channels
- [Flow Data Structure](data-structures.html#flow) - For cold asynchronous data streams
