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

A channel with a fixed buffer capacity. When the buffer is full, the sender suspends until there is space available, providing backpressure to prevent overwhelming the receiver.

```scala
import in.rcard.yaes.Channel
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import scala.concurrent.duration.*

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
- **Bounded**: Use for backpressure and controlled memory usage
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
- **Bounded channels** provide natural backpressure but may cause producers to suspend
- **Rendezvous channels** provide the strongest synchronization but the lowest throughput
- Use appropriate buffer sizes for bounded channels based on your workload
- Consider the trade-offs between throughput, latency, and memory usage

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
