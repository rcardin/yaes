
# Cats Integration

<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

The `yaes-cats` module provides seamless integration between λÆS and the Cats/Cats Effect ecosystem, enabling interoperability and providing typeclass instances for λÆS effects.

## Overview

The `yaes-cats` module bridges λÆS and Cats, offering:

- **Cats Effect Integration**: Bidirectional conversion between λÆS `Sync` and Cats Effect `IO`
- **MonadError Instance**: Use `Raise` with Cats abstractions and combinators
- **Validated Conversions**: Convert between `Raise` and Cats `Validated` types
- **Error Accumulation**: Leverage Cats `Semigroup` and `NonEmptyList` for error collection

This integration enables you to:
- Use Cats Effect libraries within λÆS programs
- Migrate incrementally between effect systems
- Leverage Cats typeclasses with λÆS effects
- Compose operations across both ecosystems

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-cats" % "0.11.0"
```

## Cats Effect Integration

### Quick Start

#### λÆS → Cats Effect

Convert λÆS programs to Cats Effect IO:

```scala
import in.rcard.yaes.{Sync => YaesSync, Raise}
import in.rcard.yaes.interop.catseffect
import cats.effect.{IO => CatsIO}

val yaesProgram: (YaesSync, Raise[Throwable]) ?=> Int = YaesSync {
  println("Hello from λÆS")
  42
}

val catsIO: CatsIO[Int] = catseffect.blockingSync(yaesProgram)
val result = catsIO.unsafeRunSync()  // 42
```

#### Cats Effect → λÆS

Convert Cats Effect IO to λÆS programs:

```scala
import in.rcard.yaes.{Sync => YaesSync, Raise}
import in.rcard.yaes.interop.catseffect
import in.rcard.yaes.syntax.catseffect.given
import cats.effect.{IO => CatsIO}

val catsProgram: CatsIO[String] = CatsIO.pure("Hello from Cats")

// Using object method
val result1 = YaesSync.run {
  Raise.either {
    catseffect.value(catsProgram)
  }
}

// Using extension method (fluent style)
val result2 = YaesSync.run {
  Raise.either {
    catsProgram.value  // Extension method with syntax import
  }
}
```

### Conversion Methods

**λÆS → Cats Effect:**

- `catseffect.blockingSync(yaesProgram)` - For blocking I/O operations (recommended)
- `catseffect.delaySync(yaesProgram)` - For CPU-bound computations only

```scala
import in.rcard.yaes.interop.catseffect
import in.rcard.yaes.{Sync => YaesSync, Raise}
import cats.effect.{IO => CatsIO}

val yaesProgram: (YaesSync, Raise[Throwable]) ?=> Int = YaesSync { 42 }

// For blocking I/O operations (default and recommended)
val catsIO: CatsIO[Int] = catseffect.blockingSync(yaesProgram)

// For CPU-bound computations only
val catsIONonBlocking: CatsIO[Int] = catseffect.delaySync(yaesProgram)
```

**Requirements:**
- The yaesProgram has access to `Raise[Throwable]` for typed error handling
- Use `blockingSync` for programs with blocking I/O (default and recommended)
- Use `delaySync` only for CPU-bound, non-blocking computations

**Cats Effect → λÆS:**

- `catseffect.value(catsIO)` - Object method
- `catsIO.value` - Extension method (requires syntax import)

```scala
import in.rcard.yaes.interop.catseffect
import in.rcard.yaes.syntax.catseffect.given

val catsIO: CatsIO[Int] = CatsIO.pure(42)

// Using object method
val result1 = YaesSync.run {
  Raise.either {
    catseffect.value(catsIO)
  }
}

// Using extension method (fluent style)
val result2 = YaesSync.run {
  Raise.either {
    catsIO.value
  }
}
```

**Note:** The conversion requires handling `Raise[Throwable]` using [Raise combinators](effects/raise.html) like `either`, `fold`, `recover`, etc.

### Timeout Support

Prevent indefinite blocking when converting Cats Effect to λÆS:

```scala
import in.rcard.yaes.interop.catseffect
import scala.concurrent.duration._

val slowCatsIO = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)

// Using object method with timeout
val result1 = YaesSync.run {
  Raise.fold(
    catseffect.value(slowCatsIO, 5.seconds)  // Timeout after 5 seconds
  )(
    error => -1  // Handle timeout
  )(
    value => value
  )
}

// Using extension method with timeout
import in.rcard.yaes.syntax.catseffect.given

val result2 = YaesSync.run {
  Raise.either {
    slowCatsIO.value(5.seconds)  // Fluent style with timeout
  }
}
```

If the computation doesn't complete within the timeout, a `java.util.concurrent.TimeoutException` is raised via `Raise[Throwable]`.

### Referential Transparency

Effects are deferred until explicitly executed:

```scala
import in.rcard.yaes.interop.catseffect

var counter = 0

val yaesProgram: (YaesSync, Raise[Throwable]) ?=> Int = YaesSync {
  counter += 1
  counter
}

val catsIO = catseffect.blockingSync(yaesProgram)
// counter is still 0 - not executed yet!

val result1 = catsIO.unsafeRunSync()  // counter = 1
val result2 = catsIO.unsafeRunSync()  // counter = 2
val result3 = catsIO.unsafeRunSync()  // counter = 3
```

### Error Handling

Errors are preserved across conversions and can be handled using [Raise](effects/raise.html) combinators:

```scala
import in.rcard.yaes.interop.catseffect

// λÆS → Cats Effect
val yaesError: (YaesSync, Raise[Throwable]) ?=> Int = YaesSync {
  throw new RuntimeException("λÆS error")
}

val catsIO = catseffect.blockingSync(yaesError)
// Error thrown when unsafeRunSync() is called

// Cats Effect → λÆS
val catsError = CatsIO.raiseError[Int](new RuntimeException("Cats error"))

val result = YaesSync.run {
  Raise.either {
    catseffect.value(catsError)
  }
}
// result: Future[Either[Throwable, Int]] = Future(Left(RuntimeException: Cats error))
```

### Typed Error Handling with Raise

Use Raise combinators for type-safe error handling:

```scala
import in.rcard.yaes.interop.catseffect

val catsIO = CatsIO.raiseError[Int](new RuntimeException("Oops"))

// Using Raise.either
val result1 = YaesSync.run {
  Raise.either {
    catseffect.value(catsIO)
  } match {
    case Right(value) => println(s"Success: $value")
    case Left(error) => println(s"Error: ${error.getMessage}")
  }
}

// Using Raise.fold
val result2 = YaesSync.run {
  Raise.fold(
    catseffect.value(catsIO)
  )(
    error => println(s"Error: ${error.getMessage}")
  )(
    value => println(s"Success: $value")
  )
}

// Using Raise.recover for default values
val result3 = YaesSync.run {
  Raise.recover {
    catseffect.value(catsIO)
  } { _ => 0 }  // Return 0 on any error
}
```

See the [Raise Effect documentation](effects/raise.html) for more error handling patterns.

### Composition and Chaining

Conversions can be composed and chained:

```scala
import in.rcard.yaes.interop.catseffect
import in.rcard.yaes.syntax.catseffect.given

val originalYaes: (YaesSync, Raise[Throwable]) ?=> Int = YaesSync { 21 }

// λÆS → Cats Effect → transformation → λÆS
val result = YaesSync.run {
  Raise.either {
    catseffect.blockingSync(originalYaes)
      .map(_ * 2)
      .flatMap(x => CatsIO.pure(x + 1))
      .value  // Extension method
  }
}
// result: Future[Either[Throwable, Int]] = Future(Right(43))
```

Extension methods enable fluent chaining:

```scala
import in.rcard.yaes.syntax.catseffect.given

val result = YaesSync.run {
  Raise.either {
    CatsIO.pure(21)
      .map(_ * 2)
      .flatMap(x => CatsIO.pure(x + 1))
      .value  // Convert to λÆS at the end
  }
}
```

## MonadError Instance for Raise

The `yaes-cats` module provides a `MonadError` instance for `Raise`, allowing you to use Cats abstractions and combinators with λÆS error handling.

### Using Cats Combinators

```scala
import cats.syntax.all.*
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.instances.raise.given

def computation1: Int raises String = Raise.raise("error")
def computation2: Int raises String = 42

// Use Cats combinators like handleError
val result: Int raises String = computation1.handleError(_ => computation2)

// Use other Cats combinators
def safeDivide(a: Int, b: Int): Int raises String =
  if (b == 0) Raise.raise("Division by zero")
  else a / b

val composed = for {
  x <- safeDivide(10, 2)  // 5
  y <- safeDivide(20, x)  // 4
} yield y

Raise.fold(composed)(
  error => println(s"Error: $error")
)(
  value => println(s"Result: $value")  // "Result: 4"
)
```

### Integration with Cats Libraries

The `MonadError` instance enables seamless integration with Cats-based libraries:

```scala
import cats.implicits.*
import in.rcard.yaes.instances.raise.given

def validateAge(age: Int): Int raises String =
  if (age >= 0 && age <= 150) age
  else Raise.raise("Invalid age")

def validateName(name: String): String raises String =
  if (name.nonEmpty) name
  else Raise.raise("Name cannot be empty")

// Use applicative validation
val result = (validateAge(25), validateName("Alice")).mapN { (age, name) =>
  s"$name is $age years old"
}

Raise.fold(result)(
  error => println(s"Validation failed: $error")
)(
  value => println(value)  // "Alice is 25 years old"
)
```

## Validated Conversions

Convert between λÆS `Raise` and Cats `Validated` types for validation workflows.

### Raise → Validated

Convert Raise computations to Cats Validated:

```scala
import in.rcard.yaes.Raise
import in.rcard.yaes.cats.validated
import cats.data.Validated

// Basic Validated
val result: Validated[String, Int] = validated.validated {
  if (condition) 42
  else Raise.raise("error")
}

// ValidatedNec (Validated with NonEmptyChain)
import cats.data.ValidatedNec

val resultNec: ValidatedNec[String, Int] = validated.validatedNec {
  if (condition) 42
  else Raise.raise("error")
}

// ValidatedNel (Validated with NonEmptyList)
import cats.data.ValidatedNel

val resultNel: ValidatedNel[String, Int] = validated.validatedNel {
  if (condition) 42
  else Raise.raise("error")
}
```

### Validated → Raise

Extract values from Validated or raise errors:

```scala
import in.rcard.yaes.syntax.validated.given
import cats.data.Validated

val validated: Validated[String, Int] = Validated.valid(42)

val result = Raise.either {
  validated.value  // Extract value or raise error
}
// result: Either[String, Int] = Right(42)

val invalid: Validated[String, Int] = Validated.invalid("error")

val errorResult = Raise.either {
  invalid.value
}
// errorResult: Either[String, Int] = Left("error")
```

### Validation Workflows

Combine Validated conversions with Raise for flexible validation:

```scala
import in.rcard.yaes.cats.validated
import cats.data.ValidatedNel
import cats.implicits.*

case class User(name: String, age: Int, email: String)

def validateName(name: String): ValidatedNel[String, String] =
  validated.validatedNel {
    if (name.nonEmpty) name
    else Raise.raise("Name cannot be empty")
  }

def validateAge(age: Int): ValidatedNel[String, Int] =
  validated.validatedNel {
    if (age >= 0 && age <= 150) age
    else Raise.raise("Invalid age")
  }

def validateEmail(email: String): ValidatedNel[String, String] =
  validated.validatedNel {
    if (email.contains("@")) email
    else Raise.raise("Invalid email")
  }

val userValidation = (
  validateName(""),
  validateAge(200),
  validateEmail("not-an-email")
).mapN(User.apply)

// userValidation: ValidatedNel[String, User] =
//   Invalid(NonEmptyList("Name cannot be empty", "Invalid age", "Invalid email"))
```

## Error Accumulation with Cats

Accumulate multiple errors using Cats `Semigroup` or `NonEmptyList`.

### Using Semigroup

Combine errors using any `Semigroup` instance:

```scala
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.cats.accumulate
import cats.Semigroup

case class MyError(errors: List[String])

given Semigroup[MyError] with {
  def combine(error1: MyError, error2: MyError): MyError =
    MyError(error1.errors ++ error2.errors)
}

val result: List[Int] raises MyError =
  accumulate.mapAccumulatingS(List(1, 2, 3, 4, 5)) { value =>
    if (value % 2 == 0) {
      Raise.raise(MyError(List(value.toString)))
    } else {
      value
    }
  }

val actual = Raise.fold(result, identity, identity)
// actual: MyError(List("2", "4"))
```

Works with `NonEmptyList` too:

```scala
import cats.data.NonEmptyList

val nelResult: NonEmptyList[Int] raises MyError =
  accumulate.mapAccumulatingS(NonEmptyList.of(1, 2, 3, 4, 5)) { value =>
    if (value % 2 == 0) {
      Raise.raise(MyError(List(value.toString)))
    } else {
      value
    }
  }
```

### Using NonEmptyList

Collect errors in a `NonEmptyList`:

```scala
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.cats.accumulate
import cats.data.NonEmptyList

val result: List[Int] raises NonEmptyList[String] =
  accumulate.mapAccumulating(List(1, 2, 3, 4, 5)) { value =>
    if (value % 2 == 0) {
      Raise.raise(value.toString)
    } else {
      value
    }
  }

val actual = Raise.fold(result, identity, identity)
// actual: NonEmptyList("2", "4")
```

Also works with `NonEmptyList` input:

```scala
val nelResult: NonEmptyList[Int] raises NonEmptyList[String] =
  accumulate.mapAccumulating(NonEmptyList.of(1, 2, 3, 4, 5)) { value =>
    if (value % 2 == 0) {
      Raise.raise(value.toString)
    } else {
      value
    }
  }
```

### Extension Methods

Use fluent syntax for error combination:

```scala
import in.rcard.yaes.syntax.accumulate.given
import cats.Semigroup
import cats.data.NonEmptyList

// With Semigroup
given Semigroup[String] = Semigroup.instance(_ + _)

val computations: List[Int raises String] = List(
  if (condition1) 1 else Raise.raise("error1"),
  if (condition2) 2 else Raise.raise("error2"),
  if (condition3) 3 else Raise.raise("error3")
)

// Combine errors with Semigroup
val results: List[Int] raises String = computations.combineErrorsS

// Or collect errors in NonEmptyList
val resultsNel: List[Int] raises NonEmptyList[String] = computations.combineErrors
```

Works with `NonEmptyList` of computations:

```scala
val nelComputations: NonEmptyList[Int raises String] = NonEmptyList.of(
  if (condition1) 1 else Raise.raise("error1"),
  if (condition2) 2 else Raise.raise("error2")
)

val nelResults: NonEmptyList[Int] raises String = nelComputations.combineErrorsS
val nelResultsNel: NonEmptyList[Int] raises NonEmptyList[String] = nelComputations.combineErrors
```

### Polymorphic Error Accumulation

The core `Raise.accumulate` function is polymorphic and can collect errors into different collection types. Import collector instances from `instances.accumulate` to use `NonEmptyList` or `NonEmptyChain`.

**Using NonEmptyList:**

```scala
import in.rcard.yaes.{Raise, RaiseNel}  // RaiseNel = Raise[NonEmptyList[E]]
import in.rcard.yaes.Raise.accumulating
import in.rcard.yaes.instances.accumulate.given  // Import collector instances
import cats.data.NonEmptyList

def validatePositive(n: Int)(using Raise[String]): Int =
  if (n > 0) n else Raise.raise(s"$n is not positive")

// Accumulate errors into NonEmptyList
val result: Either[NonEmptyList[String], (Int, Int)] = Raise.either {
  Raise.accumulate[NonEmptyList, String, (Int, Int)] {
    val a = accumulating { validatePositive(-1) }
    val b = accumulating { validatePositive(-2) }
    (a, b)
  }
}
// result: Left(NonEmptyList("-1 is not positive", List("-2 is not positive")))

// Using the RaiseNel type alias:
def validatePair(x: Int, y: Int): RaiseNel[String] ?=> (Int, Int) =
  Raise.accumulate[NonEmptyList, String, (Int, Int)] {
    val a = accumulating { validatePositive(x) }
    val b = accumulating { validatePositive(y) }
    (a, b)
  }
```

**Using NonEmptyChain:**

```scala
import in.rcard.yaes.RaiseNec  // RaiseNec = Raise[NonEmptyChain[E]]
import cats.data.NonEmptyChain

// Accumulate errors into NonEmptyChain
val result: Either[NonEmptyChain[String], List[Int]] = Raise.either {
  Raise.accumulate[NonEmptyChain, String, List[Int]] {
    val numbers = List(1, -2, 3, -4, 5).map { n =>
      accumulating { validatePositive(n) }
    }
    numbers
  }
}
// result: Left(NonEmptyChain("-2 is not positive", "-4 is not positive"))

// Using the RaiseNec type alias:
def validateList(numbers: List[Int]): RaiseNec[String] ?=> List[Int] =
  Raise.accumulate[NonEmptyChain, String, List[Int]] {
    numbers.map { n =>
      accumulating { validatePositive(n) }
    }
  }
```

**Using List (default):**

```scala
// No extra imports needed for List
val result: Either[List[String], (Int, Int)] = Raise.either {
  Raise.accumulate[List, String, (Int, Int)] {
    val a = accumulating { validatePositive(-1) }
    val b = accumulating { validatePositive(-2) }
    (a, b)
  }
}
// result: Left(List("-1 is not positive", "-2 is not positive"))
```

The type parameter `M[_]` specifies the error collection type. An `AccumulateCollector[M]` typeclass instance converts the internal error list to `M[Error]`:

- **`List`**: Built-in collector (always available)
- **`NonEmptyList`**: Provided by `instances.accumulate` module
- **`NonEmptyChain`**: Provided by `instances.accumulate` module

**Type Aliases:** For convenience, type aliases are provided following Cats conventions:
- `RaiseNel[E]` = `Raise[NonEmptyList[E]]`
- `RaiseNec[E]` = `Raise[NonEmptyChain[E]]`

## Usage Examples

### Simple Value Conversion

```scala
import in.rcard.yaes.{Sync => YaesSync, Raise}
import in.rcard.yaes.interop.catseffect
import in.rcard.yaes.syntax.catseffect.given
import cats.effect.{IO => CatsIO}
import scala.concurrent.Await
import scala.concurrent.duration._

// Cats Effect → λÆS
val number: CatsIO[Int] = CatsIO.pure(42)
val result = YaesSync.run {
  Raise.either {
    number.value
  }
}
val either = Await.result(result, 5.seconds)  // Right(42)

// λÆS → Cats Effect
val yaesNumber: (YaesSync, Raise[Throwable]) ?=> Int = YaesSync { 42 }
val catsNumber = catseffect.blockingSync(yaesNumber)
catsNumber.unsafeRunSync()  // 42
```

### Complex Computations

```scala
import in.rcard.yaes.interop.catseffect
import in.rcard.yaes.syntax.catseffect.given

var accumulator = 0

val yaesProgram: (YaesSync, Raise[Throwable]) ?=> String = YaesSync {
  accumulator += 1
  s"λÆS: $accumulator"
}

val complexComputation = catseffect.blockingSync(yaesProgram)
  .flatMap { yaesResult =>
    CatsIO {
      accumulator += 10
      s"$yaesResult, Cats: $accumulator"
    }
  }

val result = YaesSync.run {
  Raise.either {
    complexComputation.value
  }
}

val either = Await.result(result, 5.seconds)
// Right("λÆS: 1, Cats: 11")
```

### Error Handling with Timeout

```scala
import in.rcard.yaes.interop.catseffect
import in.rcard.yaes.syntax.catseffect.given
import scala.concurrent.duration._

val slowComputation = CatsIO.sleep(10.seconds) *> CatsIO.pure("Done")

val result = YaesSync.run {
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

## Available Modules

The `yaes-cats` integration is organized into these modules:

| Module | Purpose |
|--------|---------|
| `interop.catseffect` | Bidirectional IO conversions between λÆS and Cats Effect |
| `syntax.catseffect` | Extension methods for fluent Cats Effect conversion syntax |
| `cats.validated` | Conversions between Raise and Validated/ValidatedNec/ValidatedNel |
| `cats.accumulate` | Error accumulation with Semigroup and NonEmptyList |
| `instances.raise` | MonadError typeclass instance for Raise |
| `instances.accumulate` | AccumulateCollector instances for NonEmptyList/NonEmptyChain |
| `syntax.validated` | Extension methods for Validated types |
| `syntax.accumulate` | Extension methods for error accumulation |

### Import Guide

```scala
// Cats Effect conversions (object methods)
import in.rcard.yaes.interop.catseffect

// Cats Effect conversions (extension methods)
import in.rcard.yaes.syntax.catseffect.given

// MonadError instance for Raise
import in.rcard.yaes.instances.raise.given

// Validated conversions
import in.rcard.yaes.cats.validated
import in.rcard.yaes.syntax.validated.given

// Error accumulation (utility functions)
import in.rcard.yaes.cats.accumulate
import in.rcard.yaes.syntax.accumulate.given

// Polymorphic accumulate collectors (NonEmptyList/NonEmptyChain)
import in.rcard.yaes.instances.accumulate.given

// All syntax extensions
import in.rcard.yaes.syntax.all.given
```

## Best Practices

### When to Use This Integration

Use the Cats integration when you need to:

- **Integrate with Cats Effect libraries**: Use existing Cats Effect libraries in λÆS programs
- **Migrate incrementally**: Gradually migrate between effect systems
- **Leverage Cats typeclasses**: Use MonadError and other Cats abstractions with λÆS
- **Validation workflows**: Combine Raise with Cats Validated for robust validation
- **Error accumulation**: Collect multiple errors using Semigroup or NonEmptyList
- **Interoperate**: Share code between teams using different effect systems

### Performance Considerations

**Execution Models:**
- λÆS Sync uses Java Virtual Threads via `Executors.newVirtualThreadPerTaskExecutor()`
- Cats Effect IO uses fiber-based concurrency
- The Cats Effect → λÆS conversion uses `Await.result`, which blocks the current thread
- Blocking on Virtual Threads is efficient and cheap compared to platform threads

**Recommendations:**
- Use conversions at application boundaries, not in hot paths
- For high-throughput scenarios, prefer staying within one effect system
- Use the timeout variant in production to prevent indefinite blocking
- Consider the overhead of crossing effect system boundaries
- Prefer `blockingSync` over `delaySync` for most conversions from λÆS to Cats Effect

### Error Handling Best Practices

Always handle `Raise[Throwable]` when converting Cats Effect to λÆS:

```scala
// Good: Handle errors explicitly
val result = YaesSync.run {
  Raise.either {
    catsIO.value
  }
}

// Better: Provide default values
val result = YaesSync.run {
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
val result = YaesSync.run {
  Raise.fold(
    catsIO.value(30.seconds)
  )(
    error => handleError(error)
  )(
    value => value
  )
}
```

### Choosing Conversion Methods

**λÆS → Cats Effect:**
- Use `blockingSync` by default - it's safe for both I/O and CPU-bound operations
- Only use `delaySync` when you're certain the operation is CPU-bound with no blocking

**Cats Effect → λÆS:**
- Use extension methods (`.value`) for fluent, readable code
- Use object methods (`catseffect.value(...)`) when you prefer explicit imports
- Always specify timeouts for production code

## Requirements

- **Scala Version**: 3.7.4+
- **Java Version**: 24+ (for Virtual Threads)
- **Cats Effect Version**: 3.6.3+
- **Cats Version**: 2.13.0+
- **λÆS Core**: 0.9.0+

## Related Documentation

- [yaes-cats README](../yaes-cats/README.md) - Complete module documentation
- [IO Effect](effects/sync.html) - λÆS side-effecting operations
- [Raise Effect](effects/raise.html) - Typed error handling in λÆS
- [Async Effect](effects/async.html) - Structured concurrency in λÆS
