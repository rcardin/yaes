
# Retry Handler

The `Retry` handler re-executes a failing block according to a `Schedule` retry policy. It catches typed errors via `Raise[E]` and uses `Async` for delays between attempts.

> **Note:** `Retry` is not an effect — it is a handler that orchestrates existing effects (`Raise` and `Async`). The block being retried just runs, succeeds, or fails; it never calls `Retry` directly.

## Overview

Retry is useful for operations that may transiently fail, such as HTTP requests, database queries, or external API calls. Only errors raised via `Raise[E]` trigger a retry — other effect types in scope are not intercepted.

## Basic Usage

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

## Schedule Policies

A `Schedule` is a pure trait that computes `Option[Duration]` for each retry attempt. Attempts are 1-indexed: attempt 1 is the first retry after the initial failure.

### Fixed Delay

Constant delay between each retry. Retries forever unless combined with `attempts`:

```scala
val schedule = Schedule.fixed(500.millis)
schedule.delay(1)   // Some(500.millis)
schedule.delay(100) // Some(500.millis)
```

### Exponential Backoff

Delay grows exponentially: `initial * factor^(attempt-1)`, optionally capped at a maximum:

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
- `max` — maximum delay cap (default `Duration.Inf`)

### Limiting Attempts

The `attempts` extension limits the total number of executions (1 initial + N-1 retries):

```scala
val schedule = Schedule.fixed(100.millis).attempts(3)
schedule.delay(1) // Some(100.millis) — 1st retry
schedule.delay(2) // Some(100.millis) — 2nd retry
schedule.delay(3) // None — stop (3 total executions reached)
```

### Adding Jitter

The `jitter` extension adds random variation to each delay, preventing thundering herd problems:

```scala
val schedule = Schedule.fixed(1.second).jitter(0.5)
// Each delay will be random in [500ms, 1500ms]
```

The `factor` parameter controls the range as a fraction of the base delay. A factor of `0.5` on a 1-second delay produces delays in `[500ms, 1500ms]`.

## Composing Schedules

Schedule extensions compose naturally via chaining:

```scala
// Exponential backoff with jitter, capped at 30s, up to 5 total attempts
val schedule = Schedule
  .exponential(100.millis, factor = 2.0, max = 30.seconds)
  .jitter(0.25)
  .attempts(5)
```

## Practical Examples

### HTTP Client with Retry

```scala
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*
import scala.concurrent.duration.*

sealed trait HttpError
case class Timeout(msg: String)    extends HttpError
case class ServerError(code: Int)  extends HttpError

def fetchData(url: String)(using Raise[HttpError], Async): String = ???

val result: Either[HttpError, String] = Async.run {
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
```

### Retrying Only Specific Errors

`Retry` retries all errors of the specified type `E`. If your block raises multiple error types via different `Raise` contexts, only the type parameter of `Retry` is intercepted — other error types propagate immediately:

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
