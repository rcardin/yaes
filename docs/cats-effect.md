---
layout: default
title: "Cats Effect Integration"
---

# Cats Effect Integration

<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

The `yaes-cats` module provides seamless bidirectional conversion between λÆS IO and Cats Effect 3 IO, enabling interoperability between the two effect systems.

## Overview

λÆS uses context-based effects with deferred execution, while Cats Effect uses monadic IO. This integration allows you to:

- Convert λÆS programs to Cats Effect IO for use in Cats Effect ecosystems
- Use Cats Effect libraries and code within λÆS programs
- Compose and chain operations across both effect systems
- Leverage the strengths of both approaches in the same application

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-cats" % "0.9.0"
```

## Quick Start

### λÆS → Cats Effect

Convert λÆS programs to Cats Effect IO:

```scala
import in.rcard.yaes.{IO => YaesIO, Raise, Cats}
import cats.effect.{IO => CatsIO}
import scala.concurrent.ExecutionContext.Implicits.global

val yaesProgram: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
  println("Hello from λÆS")
  42
}

val catsIO: CatsIO[Int] = Cats.run(yaesProgram)
val result = catsIO.unsafeRunSync()  // 42
```

### Cats Effect → λÆS

Convert Cats Effect IO to λÆS programs:

```scala
import in.rcard.yaes.{IO => YaesIO, Raise}
import in.rcard.yaes.Cats._
import cats.effect.{IO => CatsIO}

val catsProgram: CatsIO[String] = CatsIO.pure("Hello from Cats")

val yaesResult = YaesIO.run {
  Raise.either {
    catsProgram.value  // Extension method
  }
}
```

## API Overview

### Converting λÆS to Cats Effect

Use `Cats.run()` to convert λÆS programs to Cats Effect IO:

```scala
import scala.concurrent.ExecutionContext.Implicits.global

val yaesProgram: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO { 42 }
val catsIO: CatsIO[Int] = Cats.run(yaesProgram)
```

**Requirements:**
- An implicit `ExecutionContext` for running the λÆS handler
- The program has access to `Raise[Throwable]` for typed error handling

**How it works:**
1. Runs the λÆS handler to obtain a `Future[A]`
2. Lifts the Future into Cats Effect IO using `IO.fromFuture`
3. Preserves async semantics

### Converting Cats Effect to λÆS

Use `Cats.value()` or the `.value` extension method:

```scala
val catsIO: CatsIO[Int] = CatsIO.pure(42)

// Using object method
val result1 = YaesIO.run {
  Raise.either {
    Cats.value(catsIO)
  }
}

// Using extension method (fluent style)
val result2 = YaesIO.run {
  Raise.either {
    catsIO.value
  }
}
```

**Note:** The conversion requires handling `Raise[Throwable]` using [Raise combinators](effects/raise.html) like `either`, `fold`, or `recover`.

**How it works:**
1. Converts Cats IO to a `Future` using `unsafeToFuture`
2. Blocks within λÆS IO context using `Await.result`
3. Virtual Threads handle blocking efficiently

### Timeout Support

Prevent indefinite blocking when converting Cats Effect to λÆS:

```scala
import scala.concurrent.duration._

val slowCatsIO = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)

// With timeout - raises TimeoutException if exceeded
val result = YaesIO.run {
  Raise.fold(
    slowCatsIO.value(5.seconds)  // Timeout after 5 seconds
  )(
    error => -1  // Handle timeout
  )(
    value => value
  )
}
```

If the computation doesn't complete within the timeout, a `java.util.concurrent.TimeoutException` is raised via `Raise[Throwable]`.

## Features

### Referential Transparency

Effects are deferred until explicitly executed:

```scala
var counter = 0

val yaesProgram: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
  counter += 1
  counter
}

val catsIO = Cats.run(yaesProgram)
// counter is still 0 - not executed yet!

val result1 = catsIO.unsafeRunSync()  // counter = 1
val result2 = catsIO.unsafeRunSync()  // counter = 2
val result3 = catsIO.unsafeRunSync()  // counter = 3
```

### Error Handling

Errors are preserved across conversions and can be handled using [Raise](effects/raise.html) combinators:

```scala
// λÆS → Cats Effect
val yaesError: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
  throw new RuntimeException("λÆS error")
}

val catsIO = Cats.run(yaesError)
// Error thrown when unsafeRunSync() is called

// Cats Effect → λÆS
val catsError = CatsIO.raiseError[Int](new RuntimeException("Cats error"))

val result = YaesIO.run {
  Raise.either {
    catsError.value
  }
}
// result: Future[Either[Throwable, Int]] = Future(Left(RuntimeException: Cats error))
```

### Typed Error Handling with Raise

Use Raise combinators for type-safe error handling:

```scala
val catsIO = CatsIO.raiseError[Int](new RuntimeException("Oops"))

// Using Raise.either
val result1 = YaesIO.run {
  Raise.either {
    catsIO.value
  } match {
    case Right(value) => println(s"Success: $value")
    case Left(error) => println(s"Error: ${error.getMessage}")
  }
}

// Using Raise.fold
val result2 = YaesIO.run {
  Raise.fold(
    catsIO.value
  )(
    error => println(s"Error: ${error.getMessage}")
  )(
    value => println(s"Success: $value")
  )
}

// Using Raise.recover for default values
val result3 = YaesIO.run {
  Raise.recover {
    catsIO.value
  } { _ => 0 }  // Return 0 on any error
}
```

See the [Raise Effect documentation](effects/raise.html) for more error handling patterns.

### Composition and Chaining

Conversions can be composed and chained:

```scala
val originalYaes: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO { 21 }

// λÆS → Cats Effect → transformation → λÆS
val result = YaesIO.run {
  Raise.either {
    Cats.run(originalYaes)
      .map(_ * 2)
      .flatMap(x => CatsIO.pure(x + 1))
      .value
  }
}
// result: Future[Either[Throwable, Int]] = Future(Right(43))
```

Extension methods enable fluent chaining:

```scala
val result = YaesIO.run {
  Raise.either {
    CatsIO.pure(21)
      .map(_ * 2)
      .flatMap(x => CatsIO.pure(x + 1))
      .value  // Convert to λÆS at the end
  }
}
```

## Usage Examples

### Simple Value Conversion

```scala
import in.rcard.yaes.{IO => YaesIO, Raise, Cats}
import in.rcard.yaes.Cats._
import cats.effect.{IO => CatsIO}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

// Cats Effect → λÆS
val number: CatsIO[Int] = CatsIO.pure(42)
val result = YaesIO.run {
  Raise.either {
    number.value
  }
}
val either = Await.result(result, 5.seconds)  // Right(42)

// λÆS → Cats Effect
val yaesNumber: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO { 42 }
val catsNumber = Cats.run(yaesNumber)
catsNumber.unsafeRunSync()  // 42
```

### Complex Computations

```scala
var accumulator = 0

val yaesProgram: (YaesIO, Raise[Throwable]) ?=> String = YaesIO {
  accumulator += 1
  s"λÆS: $accumulator"
}

val complexComputation = Cats.run(yaesProgram)
  .flatMap { yaesResult =>
    CatsIO {
      accumulator += 10
      s"$yaesResult, Cats: $accumulator"
    }
  }

val result = YaesIO.run {
  Raise.either {
    complexComputation.value
  }
}

val either = Await.result(result, 5.seconds)
// Right("λÆS: 1, Cats: 11")
```

### Error Handling with Timeout

```scala
import scala.concurrent.duration._

val slowComputation = CatsIO.sleep(10.seconds) *> CatsIO.pure("Done")

val result = YaesIO.run {
  Raise.fold(
    slowComputation.value(1.second)  // Timeout after 1 second
  )(
    error => "Computation timed out!"
  )(
    value => s"Success: $value"
  )
}

Await.result(result, 5.seconds)  // "Computation timed out!"
```

## Best Practices

### When to Use This Integration

Use the Cats Effect integration when you need to:

- **Integrate with Cats Effect libraries**: Use existing Cats Effect libraries in λÆS programs
- **Migrate incrementally**: Gradually migrate between effect systems
- **Leverage both ecosystems**: Combine λÆS's context-based effects with Cats Effect's monadic approach
- **Interoperate**: Share code between teams using different effect systems

### Performance Considerations

**Execution Models:**
- λÆS IO uses Java Virtual Threads via `Executors.newVirtualThreadPerTaskExecutor()`
- Cats Effect IO uses fiber-based concurrency
- The Cats → λÆS conversion uses `Await.result`, which blocks the current thread
- Blocking on Virtual Threads is efficient and cheap compared to platform threads

**Recommendations:**
- Use conversions at application boundaries, not in hot paths
- For high-throughput scenarios, prefer staying within one effect system
- Use the timeout variant in production to prevent indefinite blocking
- Consider the overhead of crossing effect system boundaries

### Error Handling Best Practices

Always handle `Raise[Throwable]` when converting Cats Effect to λÆS:

```scala
// Good: Handle errors explicitly
val result = YaesIO.run {
  Raise.either {
    catsIO.value
  }
}

// Better: Provide default values
val result = YaesIO.run {
  Raise.recover {
    catsIO.value
  } { error =>
    logger.error(s"Error: ${error.getMessage}")
    defaultValue
  }
}
```

Use timeouts for production code:

```scala
// Production-ready with timeout
val result = YaesIO.run {
  Raise.fold(
    catsIO.value(30.seconds)
  )(
    error => handleError(error)
  )(
    value => value
  )
}
```

## Type Aliases

For convenience, type aliases are available:

```scala
import in.rcard.yaes.Cats._

// YaesIO is an alias for in.rcard.yaes.IO
val yaes: YaesIO = ???

// CatsIO[A] is an alias for cats.effect.IO[A]
val cats: CatsIO[Int] = ???
```

## Requirements

- **Scala Version**: 3.7.4+
- **Java Version**: 24+ (for Virtual Threads)
- **Cats Effect Version**: 3.6.3+
- **λÆS Core**: 0.9.0+

## Related Effects

- [IO Effect](effects/io.html) - λÆS side-effecting operations
- [Raise Effect](effects/raise.html) - Typed error handling in λÆS
- [Async Effect](effects/async.html) - Structured concurrency in λÆS
