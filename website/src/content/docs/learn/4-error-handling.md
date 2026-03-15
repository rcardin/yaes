---
title: Error Handling
description: Learn typed error handling with the Raise effect and resilient retry strategies with the Retry handler.
sidebar:
  label: "4. Error Handling"
  order: 4
---

λÆS approaches error handling functionally: errors are typed, explicit in function signatures, and handled without throwing exceptions. This step covers the `Raise` effect for typed errors and the `Retry` handler for resilient retry strategies.

---

## Raise Effect

The `Raise[E]` effect describes the possibility that a function can raise an error of type `E`. It provides typed error handling inspired by the [`raise4s`](https://github.com/rcardin/raise4s) library.

### Basic Usage

**With Exception Types:**

```scala
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int)(using Raise[ArithmeticException]): Int =
  if (b == 0) Raise.raise(new ArithmeticException("Division by zero"))
  else a / b
```

**With Custom Error Types:**

```scala
import in.rcard.yaes.Raise.*

object DivisionByZero
type DivisionByZero = DivisionByZero.type

def divide(a: Int, b: Int)(using Raise[DivisionByZero]): Int =
  if (b == 0) Raise.raise(DivisionByZero)
  else a / b
```

**Using the `raises` Infix Type:**

For more concise syntax, use the `raises` infix type instead of `using Raise[E]`:

```scala
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int): Int raises DivisionByZero =
  if (b == 0) Raise.raise(DivisionByZero)
  else a / b

val result: Int | DivisionByZero = Raise.run {
  divide(10, 0)
}
```

### Utility Functions

**Ensuring Conditions:**

```scala
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int)(using Raise[DivisionByZero]): Int = {
  Raise.ensure(b != 0) { DivisionByZero }
  a / b
}
```

**Ensuring Non-Null Values:**

```scala
import in.rcard.yaes.Raise.*

object NullError
type NullError = NullError.type

def processName(name: String | Null)(using Raise[NullError]): String = {
  val validName = Raise.ensureNotNull(name) { NullError }
  validName.toUpperCase
}

val result = Raise.either { processName(null) }
// result will be Left(NullError)
```

**Accumulating Errors:**

Use `accumulate` and `accumulating` to collect multiple errors instead of short-circuiting on the first one:

```scala
import in.rcard.yaes.Raise.*

def validateName(name: String)(using Raise[String]): String =
  if (name.nonEmpty) name else Raise.raise("Name cannot be empty")

def validateAge(age: Int)(using Raise[String]): Int =
  if (age >= 0) age else Raise.raise("Age cannot be negative")

val result = Raise.either {
  Raise.accumulate {
    val name = accumulating { validateName("") }
    val age = accumulating { validateAge(-1) }
    (name, age)
  }
}
// result will be Left(List("Name cannot be empty", "Age cannot be negative"))
```

:::caution
When using `accumulate` with lists or other collections, you **must** assign the result to a variable before returning it. Direct return of accumulated collections may not work correctly.

```scala
// Correct — assign to variable first
val result = Raise.either {
  Raise.accumulate {
    val processedItems = List(1, 2, 3, 4, 5).map { i =>
      accumulating {
        if (i % 2 == 0) Raise.raise(i.toString)
        else i
      }
    }
    processedItems  // Return the assigned variable
  }
}
```
:::

**`mapAccumulating` — Transform Collections While Collecting Errors:**

```scala
import in.rcard.yaes.Raise.*

def validateNumber(n: Int)(using Raise[String]): Int =
  if (n > 0) n else Raise.raise(s"$n is not positive")

val result = Raise.either {
  Raise.mapAccumulating(List(1, -2, 3, -4, 5)) { number =>
    validateNumber(number)
  }
}
// result will be Left(List("-2 is not positive", "-4 is not positive"))
```

For complex error types, provide a custom error combination function:

```scala
import in.rcard.yaes.Raise.*

case class ValidationErrors(errors: List[String])

def combineErrors(e1: ValidationErrors, e2: ValidationErrors): ValidationErrors =
  ValidationErrors(e1.errors ++ e2.errors)

def validateUserData(data: String)(using Raise[ValidationErrors]): String =
  if (data.isEmpty) Raise.raise(ValidationErrors(List("Data cannot be empty")))
  else if (data.length < 3) Raise.raise(ValidationErrors(List("Data too short")))
  else data

val result = Raise.either {
  Raise.mapAccumulating(List("Alice", "", "Bo", "Charlie"), combineErrors) { userData =>
    validateUserData(userData)
  }
}
// result will be Left(ValidationErrors(List("Data cannot be empty", "Data too short")))
```

**Polymorphic Error Accumulation** (requires `yaes-cats`):

```scala
import in.rcard.yaes.{Raise, RaiseNel}
import in.rcard.yaes.Raise.accumulating
import in.rcard.yaes.instances.accumulate.given
import cats.data.NonEmptyList

def validatePositive(n: Int)(using Raise[String]): Int =
  if (n > 0) n else Raise.raise(s"$n is not positive")

val result: Either[NonEmptyList[String], (Int, Int)] = Raise.either {
  Raise.accumulate[NonEmptyList, String, (Int, Int)] {
    val a = accumulating { validatePositive(-1) }
    val b = accumulating { validatePositive(-2) }
    (a, b)
  }
}
// result: Left(NonEmptyList("-1 is not positive", List("-2 is not positive")))
```

Available collector types:
- **`List`**: Built-in (always available)
- **`NonEmptyList`** (`RaiseNel[E]`): Requires `yaes-cats`
- **`NonEmptyChain`** (`RaiseNec[E]`): Requires `yaes-cats`

**Transforming Error Types:**

```scala
import in.rcard.yaes.Raise.*

sealed trait NetworkError
case object ConnectionTimeout extends NetworkError
case object InvalidResponse extends NetworkError

sealed trait ServiceError
case object ServiceUnavailable extends ServiceError
case object InvalidData extends ServiceError

def fetchData(url: String)(using Raise[NetworkError]): String =
  if (url.isEmpty) Raise.raise(InvalidResponse)
  else "data"

def processData(url: String)(using Raise[ServiceError]): String = {
  Raise.withError[ServiceError, NetworkError, String] {
    case ConnectionTimeout => ServiceUnavailable
    case InvalidResponse => InvalidData
  } {
    fetchData(url)
  }
}

val result = Raise.either {
  processData("")  // Raises InvalidResponse, transformed to InvalidData
}
// result will be Left(InvalidData)
```

**Catching Exceptions:**

Transform exceptions into typed errors:

```scala
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int)(using Raise[DivisionByZero]): Int =
  Raise.catching[ArithmeticException] {
    a / b
  } { _ => DivisionByZero }
```

### Handlers

**Union Type Handler:**

```scala
val result: Int | DivisionByZero = Raise.run {
  divide(10, 0)
}
```

**Either Handler:**

```scala
val result: Either[DivisionByZero, Int] = Raise.either {
  divide(10, 0)
}
```

**Option Handler** — requires the block to raise `None` explicitly:

```scala
def safeDivide(x: Int, y: Int)(using Raise[None.type]): Int =
  if (y == 0) then Raise.raise(None)
  else x / y

val result: Option[Int] = Raise.option {
  safeDivide(10, 0)
}
// result will be None
```

**Nullable Handler** — requires the block to raise `null` explicitly:

```scala
def safeDivide(x: Int, y: Int)(using Raise[Null]): Int =
  if (y == 0) then Raise.raise(null)
  else x / y

val result: Int | Null = Raise.nullable {
  safeDivide(10, 0)
}
// result will be null
```

### Error Tracing

The `traced` function adds tracing capabilities to error handling, capturing stack traces when errors occur:

```scala
import in.rcard.yaes.Raise.*

given TraceWith[String] = trace => {
  println(s"Error occurred: ${trace.original}")
  trace.printStackTrace()
}

def riskyOperation(value: Int)(using Raise[String]): Int =
  if (value < 0) Raise.raise("Negative value not allowed")
  else value * 2

val result = Raise.either {
  traced {
    riskyOperation(-5)
  }
}
// Prints error details and stack trace, then returns Left("Negative value not allowed")
```

**Default Tracing:**

```scala
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Raise.given  // Import default tracing

val result = Raise.either {
  traced {
    Raise.raise("Something went wrong")
  }
}
// Automatically prints stack trace, then returns Left("Something went wrong")
```

:::note
Tracing has performance implications since it creates full stack traces. Use it judiciously in production code.
:::

### Error Composition

Combine multiple error types in a single function:

```scala
import in.rcard.yaes.Raise.*

sealed trait ValidationError
case object InvalidEmail extends ValidationError
case object InvalidAge extends ValidationError

def validateUser(email: String, age: Int)(using Raise[ValidationError]): User = {
  val validEmail = if (email.contains("@")) email
                   else Raise.raise(InvalidEmail)
  val validAge = if (age >= 0) age
                 else Raise.raise(InvalidAge)
  User(validEmail, validAge)
}
```

### Best Practices

- Use specific error types rather than generic exceptions
- Combine with other effects like `Sync` for comprehensive error handling
- Handle errors at appropriate boundaries in your application
- Use union types for simple error handling, `Either` for more complex scenarios

---

## Retry Handler

The `Retry` handler re-executes a failing block according to a `Schedule` retry policy. It catches typed errors via `Raise[E]` and uses `Async` for delays between attempts.

:::note
`Retry` is not an effect — it is a handler that orchestrates existing effects (`Raise` and `Async`). The block being retried just runs, succeeds, or fails; it never calls `Retry` directly.
:::

### Basic Usage

```scala
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import scala.concurrent.duration.*

case class DbError(msg: String)

def findUser(id: Int)(using Raise[DbError]): String =
  Raise.raise(DbError("connection timeout"))

val result: Either[DbError, String] = Async.run {
  Raise.either {
    Retry[DbError](Schedule.fixed(500.millis).attempts(3)) {
      findUser(42)
    }
  }
}
// result will be Left(DbError("connection timeout")) after 3 total attempts
```

If the block succeeds on any attempt, its value is returned immediately. If all attempts are exhausted, the last error is re-raised via the outer `Raise[E]`.

### Schedule Policies

A `Schedule` computes `Option[Duration]` for each retry attempt. Attempts are 1-indexed: attempt 1 is the first retry after the initial failure.

**Fixed Delay** — constant delay between each retry:

```scala
val schedule = Schedule.fixed(500.millis)
schedule.delay(1)   // Some(500.millis)
schedule.delay(100) // Some(500.millis)
```

**Exponential Backoff** — delay grows as `initial * factor^(attempt-1)`, optionally capped:

```scala
val schedule = Schedule.exponential(100.millis, factor = 2.0, max = 5.seconds)
schedule.delay(1) // Some(100.millis)
schedule.delay(2) // Some(200.millis)
schedule.delay(3) // Some(400.millis)
schedule.delay(4) // Some(800.millis)
```

Parameters:
- `initial` — delay before the first retry
- `factor` — multiplier per attempt (default `2.0`)
- `max` — maximum delay cap (default `Duration.Inf`, meaning no cap)

**Limiting Attempts:**

The `attempts` extension limits the total number of executions (1 initial + N-1 retries). `attempts(0)` and `attempts(1)` both result in no retries:

```scala
val schedule = Schedule.fixed(100.millis).attempts(3)
schedule.delay(1) // Some(100.millis) — 1st retry
schedule.delay(2) // Some(100.millis) — 2nd retry
schedule.delay(3) // None — stop (3 total executions reached)
```

**Adding Jitter** — prevents thundering herd problems:

```scala
// jitter requires the Random effect in scope
val schedule = Random.run {
  Schedule.fixed(1.second).jitter(0.5)
}
// Each delay will be random in [500ms, 1500ms]
```

A `factor` of `0.5` on a 1-second delay produces delays in `[500ms, 1500ms]`.

### Composing Schedules

Schedule extensions compose naturally via chaining:

```scala
// Exponential backoff with jitter, capped at 30s, up to 5 total attempts
val schedule = Random.run {
  Schedule
    .exponential(100.millis, factor = 2.0, max = 30.seconds)
    .jitter(0.25)
    .attempts(5)
}
```

### Practical Examples

**HTTP Client with Retry:**

```scala
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Random.*
import scala.concurrent.duration.*

sealed trait HttpError
case class Timeout(msg: String)   extends HttpError
case class ServerError(code: Int) extends HttpError

def fetchData(url: String)(using Raise[HttpError], Async): String = ???

val result: Either[HttpError, String] = Random.run {
  Async.run {
    Raise.either {
      Retry[HttpError](
        Schedule.exponential(100.millis, factor = 2.0, max = 5.seconds)
          .jitter(0.5)
          .attempts(5)
      ) {
        fetchData("https://api.example.com/data")
      }
    }
  }
}
```

**Retrying Only Specific Errors:**

`Retry` retries all errors of the specified type `E`. If your block raises multiple error types, only the type parameter of `Retry` is intercepted — other error types propagate immediately:

```scala
val result: Either[String, Either[Int, Int]] = Async.run {
  Raise.either[String, Either[Int, Int]] {
    Raise.either[Int, Int] {
      Retry[Int](Schedule.fixed(10.millis).attempts(5)) {
        // Int errors are retried
        // String errors propagate immediately through the outer Raise
        Raise.raise("fatal error")
        42
      }
    }
  }
}
// result is Left("fatal error") — no retries occurred
```
