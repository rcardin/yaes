
# IO Effect

The `IO` effect allows for running side-effecting operations while maintaining referential transparency.

## Overview

The `IO` effect provides a guard rail to uncontrolled exceptions by lifting functions into the world of effectful computations.

## Basic Usage

```scala
import in.rcard.yaes.IO.*

case class User(name: String)

def saveUser(user: User)(using IO): Long =
  throw new RuntimeException("Read timed out")
```

## Handlers

### Non-blocking Handler

Returns a `Future` without blocking the current thread:

```scala
import in.rcard.yaes.IO.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val result: Future[Long] = IO.run {
  saveUser(User("John"))
}
```

### Blocking Handler

Blocks the current thread until completion:

```scala
import in.rcard.yaes.IO.*
import scala.concurrent.ExecutionContext.Implicits.global

val result: Long = IO.blockingRun {
  saveUser(User("John"))
}
```

## Implementation Details

- Uses Java Virtual Threads for execution
- Each effectful computation runs in a new virtual thread
- Handlers break referential transparency and should be used only at application edges

## Best Practices

- Use `IO` for any operation that might throw exceptions
- Combine with other effects like `Raise` for better error handling
- Keep handlers at the application boundary
