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
libraryDependencies += "in.rcard.yaes" %% "yaes-data" % "0.7.0"
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

### Empty and Single Value Flows

```scala
import in.rcard.yaes.Flow

val emptyFlow: Flow[Int] = Flow.empty[Int]
val singleFlow: Flow[String] = Flow.just("single value")
```

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
