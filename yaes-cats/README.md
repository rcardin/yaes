![Made for Scala 3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/rcardin/yaes/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/in.rcard.yaes/yaes-data_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/rcardin/yaes)
[![javadoc](https://javadoc.io/badge2/in.rcard.yaes/yaes-data_3/javadoc.svg)](https://javadoc.io/doc/in.rcard.yaes/yaes-data_3)
<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>
<br/>

# yaes-cats - Cats Effect Integration

Bidirectional conversion between YAES IO and Cats Effect 3 IO.

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-cats" % "0.9.0"
```

## Quick Start

```scala
import in.rcard.yaes.{IO => YaesIO}
import cats.effect.{IO => CatsIO}
import in.rcard.yaes.interop.cats._
import scala.concurrent.ExecutionContext.Implicits.global

// YAES IO → Cats Effect IO
val yaesProgram: YaesIO ?=> Int = YaesIO {
  println("Hello from YAES")
  42
}

val catsIO: CatsIO[Int] = Cats.run(yaesProgram)
val result = catsIO.unsafeRunSync()  // 42

// Cats Effect IO → YAES IO
val catsProgram: CatsIO[String] = CatsIO.pure("Hello from Cats")

val yaesResult = YaesIO.run {
  catsProgram.value  // Extension method
}
```

## API Overview

### YAES → Cats Effect

Convert YAES IO programs to Cats Effect IO:

```scala
import scala.concurrent.ExecutionContext.Implicits.global

val yaesProgram: YaesIO ?=> Int = YaesIO { 42 }
val catsIO: CatsIO[Int] = Cats.run(yaesProgram)
```

**Requirements:**
- Requires an implicit `ExecutionContext` for running the YAES handler

### Cats Effect → YAES

Convert Cats Effect IO to YAES IO programs:

```scala
val catsIO: CatsIO[Int] = CatsIO.pure(42)

// Using object method
val result1 = YaesIO.run {
  Cats.value(catsIO)
}

// Using extension method (fluent style)
val result2 = YaesIO.run {
  catsIO.value
}
```

### Timeout Support

Prevent indefinite blocking when converting Cats Effect IO to YAES IO:

```scala
import scala.concurrent.duration._

val slowCatsIO = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)

// Using object method
val result1 = YaesIO.run {
  Cats.value(slowCatsIO, 5.seconds)  // Timeout after 5 seconds
}

// Using extension method
val result2 = YaesIO.run {
  slowCatsIO.value(5.seconds)  // Fluent style with timeout
}
```

If the computation doesn't complete within the timeout, a `java.util.concurrent.TimeoutException` is thrown.

## Features

### Referential Transparency

Effects are deferred until explicitly executed:

```scala
var counter = 0

val yaesProgram: YaesIO ?=> Int = YaesIO {
  counter += 1
  counter
}

val catsIO = Cats.run(yaesProgram)
// counter is still 0 - not executed yet!

val result1 = catsIO.unsafeRunSync()  // counter = 1
val result2 = catsIO.unsafeRunSync()  // counter = 2
val result3 = catsIO.unsafeRunSync()  // counter = 3
```

### Error Propagation

Errors are preserved across conversions:

```scala
// YAES → Cats
val yaesError: YaesIO ?=> Int = YaesIO {
  throw new RuntimeException("YAES error")
}

val catsIO = Cats.run(yaesError)
// Error will be thrown when unsafeRunSync() is called

// Cats → YAES
val catsError = CatsIO.raiseError[Int](new RuntimeException("Cats error"))

val result = YaesIO.run {
  catsError.value
}
// Error will be thrown when awaiting the result
```

### Composition

Conversions can be composed and chained:

```scala
val originalYaes: YaesIO ?=> Int = YaesIO { 21 }

// YAES → Cats → transformation → YAES
val result = YaesIO.run {
  Cats.run(originalYaes)
    .map(_ * 2)
    .flatMap(x => CatsIO.pure(x + 1))
    .value
}
// result: Future[Int] = 43
```

### Fluent Chaining

Extension methods enable fluent chaining:

```scala
val result = YaesIO.run {
  CatsIO.pure(21)
    .map(_ * 2)
    .flatMap(x => CatsIO.pure(x + 1))
    .value  // Convert to YAES at the end
}
```

## Implementation Details

### Execution Models

**YAES IO:**
- Uses Java Virtual Threads via `Executors.newVirtualThreadPerTaskExecutor()`
- Handler returns `Future[A]`
- Non-blocking by default

**Cats Effect IO:**
- Uses fiber-based concurrency
- Provides `IO.fromFuture` and `unsafeToFuture` for interop

### Conversion Strategy

**YAES → Cats Effect:**
```scala
CatsIO.fromFuture(CatsIO(YaesIO.run(yaesProgram)))
```
- Runs YAES handler to obtain `Future[A]`
- Lifts into Cats Effect IO using `IO.fromFuture`
- Preserves async semantics

**Cats Effect → YAES:**
```scala
YaesIO {
  val future = catsIO.unsafeToFuture()(using runtime)
  Await.result(future, timeout)
}
```
- Converts Cats IO to `Future` using `unsafeToFuture`
- Blocks within YAES IO context using `Await.result`
- Virtual Threads handle blocking efficiently
- Uses Cats Effect global runtime for execution

### Performance Considerations

**Blocking in YAES IO:**
The Cats → YAES conversion uses `Await.result`, which blocks the current thread. This is acceptable because:
- YAES IO runs on Virtual Threads, which are designed to handle blocking efficiently
- Blocking a Virtual Thread is cheap compared to blocking a platform thread
- The Virtual Thread is parked and can be reused while waiting

**Usage Recommendations:**
- Use conversions at application boundaries, not in hot paths
- For high-throughput scenarios, prefer staying within one effect system
- The timeout variant is recommended for production code to prevent indefinite blocking

## Examples

### Simple Value Conversion

```scala
// Cats → YAES
val number: CatsIO[Int] = CatsIO.pure(42)
val result = YaesIO.run {
  number.value
}
Await.result(result, 5.seconds)  // 42

// YAES → Cats
val yaesNumber: YaesIO ?=> Int = YaesIO { 42 }
val catsNumber = Cats.run(yaesNumber)
catsNumber.unsafeRunSync()  // 42
```

### Side Effects

```scala
var counter = 0

val catsIO = CatsIO {
  counter += 1
  s"Count: $counter"
}

val result = YaesIO.run {
  catsIO.value
}

Await.result(result, 5.seconds)  // "Count: 1"
counter  // 1
```

### Complex Computations

```scala
var accumulator = 0

val yaesProgram: YaesIO ?=> String = YaesIO {
  accumulator += 1
  s"YAES: $accumulator"
}

val complexComputation = Cats.run(yaesProgram)
  .flatMap { yaesResult =>
    CatsIO {
      accumulator += 10
      s"$yaesResult, Cats: $accumulator"
    }
  }

val result = YaesIO.run {
  complexComputation.value
}

Await.result(result, 5.seconds)  // "YAES: 1, Cats: 11"
accumulator  // 11
```

### Error Handling with Timeout

```scala
import scala.concurrent.duration._

val slowComputation = CatsIO.sleep(10.seconds) *> CatsIO.pure("Done")

val result = YaesIO.run {
  slowComputation.value(1.second)  // Timeout after 1 second
}

try {
  Await.result(result, 5.seconds)
} catch {
  case _: TimeoutException => println("Computation timed out!")
}
```

## Type Aliases

For convenience, the package object provides type aliases:

```scala
import in.rcard.yaes.interop.cats._

// These are equivalent:
val io1: in.rcard.yaes.IO = ???
val io2: YaesIO = ???

val ce1: cats.effect.IO[Int] = ???
val ce2: CatsIO[Int] = ???
```

## Requirements

- **Scala Version:** 3.7.4+
- **Java Version:** 24+ (for Virtual Threads)
- **Cats Effect Version:** 3.6.3+
- **YAES Core:** 0.9.0+

## License

MIT License
