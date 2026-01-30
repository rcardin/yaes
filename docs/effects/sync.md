
# Sync Effect

The `Sync` effect allows for running side-effecting operations while maintaining referential transparency.

## Overview

The `Sync` effect provides a guard rail to uncontrolled exceptions by lifting functions into the world of effectful computations.

## Basic Usage

```scala
import in.rcard.yaes.Sync.*

case class User(name: String)

def saveUser(user: User)(using Sync): Long =
  throw new RuntimeException("Read timed out")
```

## Handlers

### Non-blocking Handler

Returns a `Future` without blocking the current thread:

```scala
import in.rcard.yaes.Sync.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val result: Future[Long] = Sync.run {
  saveUser(User("John"))
}
```

### Blocking Handler

Blocks the current thread until completion:

```scala
import in.rcard.yaes.Sync.*
import scala.concurrent.ExecutionContext.Implicits.global

val result: Long = Sync.blockingRun {
  saveUser(User("John"))
}
```

## Implementation Details

- Uses Java Virtual Threads for execution
- Each effectful computation runs in a new virtual thread
- Handlers break referential transparency and should be used only at application edges

## Best Practices

- Use `Sync` for any operation that might throw exceptions
- Combine with other effects like `Raise` for better error handling
- Keep handlers at the application boundary
