![Made for Scala 3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/rcardin/yaes/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/in.rcard.yaes/yaes-data_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/rcardin/yaes)
[![javadoc](https://javadoc.io/badge2/in.rcard.yaes/yaes-data_3/javadoc.svg)](https://javadoc.io/doc/in.rcard.yaes/yaes-data_3)

# λÆS Data

The `yaes-data` module provides a collection of functional data structures that complement the λÆS effects system. Currently, it includes:

* `Flow`: A cold asynchronous data stream that sequentially emits values

## Requirements

- **Java 24 or higher** is required to run λÆS Data due to its use of modern Java features like Virtual Threads and Structured Concurrency.

## Flow

A Flow is a cold asynchronous data stream that sequentially emits values and completes normally or with an exception.

Flows are conceptually similar to Iterators from the Collections framework but emit items asynchronously. The main differences between a Flow and an Iterator are:
- Flows can emit values asynchronously
- Flow emissions can be transformed with various operators
- Flow emissions can be observed through the `collect` method

### Creating Flows

There are several ways to create a Flow:

```scala
import in.rcard.yaes.Flow

// Create from explicit emits
val flow1: Flow[Int] = Flow.flow[Int] {
  Flow.emit(1)
  Flow.emit(2)
  Flow.emit(3)
}

// Create from a sequence
val flow2: Flow[Int] = List(1, 2, 3).asFlow()

// Create from varargs
val flow3: Flow[Int] = Flow(1, 2, 3)
```

### Collecting Flow Values

You can collect values from a Flow using the `collect` method:

```scala
import in.rcard.yaes.Flow
import scala.collection.mutable.ArrayBuffer

val result = ArrayBuffer[Int]()
Flow(1, 2, 3).collect { value =>
  result += value
}
// result now contains: 1, 2, 3
```

### Flow Operators

Flow provides a rich set of operators to transform and manipulate streams:

#### onStart

Executes an action before the flow starts collecting:

```scala
val result = ArrayBuffer[Int]()
Flow(1, 2, 3)
  .onStart {
    Flow.emit(0)
  }
  .collect { value =>
    result += value
  }
// result now contains: 0, 1, 2, 3
```

#### transform

Applies a transformation function to each value:

```scala
val result = ArrayBuffer[String]()
Flow(1, 2, 3)
  .transform { value =>
    Flow.emit(value.toString)
  }
  .collect { value =>
    result += value
  }
// result now contains: "1", "2", "3"
```

#### map

Transforms each emitted value:

```scala
val result = ArrayBuffer[String]()
Flow(1, 2, 3)
  .map(_.toString)
  .collect { value =>
    result += value
  }
// result now contains: "1", "2", "3"
```

#### filter

Keeps only values that match a predicate:

```scala
val result = ArrayBuffer[Int]()
Flow(1, 2, 3, 4, 5)
  .filter(_ % 2 == 0)
  .collect { value =>
    result += value
  }
// result now contains: 2, 4
```

#### take

Limits the number of emitted values:

```scala
val result = ArrayBuffer[Int]()
Flow(1, 2, 3, 4, 5)
  .take(3)
  .collect { value =>
    result += value
  }
// result now contains: 1, 2, 3
```

#### drop

Skips the first n emitted values:

```scala
val result = ArrayBuffer[Int]()
Flow(1, 2, 3, 4, 5)
  .drop(2)
  .collect { value =>
    result += value
  }
// result now contains: 3, 4, 5
```

#### onEach

Performs a side effect for each value without changing the flow:

```scala
val result = ArrayBuffer[Int]()
val sideEffects = ArrayBuffer[Int]()

Flow(1, 2, 3)
  .onEach { value =>
    sideEffects += value * 10
  }
  .collect { value =>
    result += value
  }
// result now contains: 1, 2, 3
// sideEffects now contains: 10, 20, 30
```

### Terminal Operators

Flow also provides several terminal operators that process the entire flow:

#### fold

Accumulates values to a single result:

```scala
val sum = Flow(1, 2, 3, 4, 5).fold(0) { (acc, value) =>
  acc + value
}
// sum is 15
```

#### count

Counts the number of emitted values:

```scala
val count = Flow(1, 2, 3, 4, 5).count()
// count is 5
```

## Dependency

To use the `yaes-data` module, add the following dependency to your build.sbt file:

```sbt
libraryDependencies += "in.rcard.yaes" %% "yaes-data" % "0.7.0"
```

The library is only available for Scala 3 and is currently in an experimental stage. The API is subject to change.

## Contributing

Contributions to the λÆS project are welcome! Please feel free to submit pull requests or open issues if you find bugs or have feature requests.