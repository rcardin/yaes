
# Async Effect

The `Async` effect enables asynchronous computations and structured concurrency with fiber management.

## Overview

Built around ideas from structured concurrency, the `Async` effect provides a way to define asynchronous computations executed in a structured manner using Java's Structured Concurrency (Java 21+).

## Core Operations

### Forking Fibers

Create lightweight threads (fibers) for concurrent execution:

```scala
import in.rcard.yaes.Async.*

def findUserByName(name: String): Option[User] = Some(User(name))

val fiber: Async ?=> Fiber[Option[User]] = Async.fork { 
  findUserByName("John") 
}
```

### Getting Values

Wait for a fiber's result:

```scala
import in.rcard.yaes.Raise.*

val maybeUser: (Async, Raise[Cancelled]) ?=> Option[User] = fiber.value
```

### Joining Fibers

Wait for completion without getting the value:

```scala
val result: Async ?=> Option[User] = fiber.join()
```

## Structured Concurrency

The `Async.run` handler creates a structured scope where all fibers are managed:

```scala
import in.rcard.yaes.Async.*

def updateUser(user: User): Unit = ???
def updateClicks(user: User, clicks: Int): Unit = ???

Async.run {
  val john = User("John")
  Async.fork { updateUser(john) }
  Async.fork { updateClicks(john, 10) }
  // Waits for both fibers to complete
}
```

## Cancellation

Fibers can be cancelled cooperatively:

```scala
import in.rcard.yaes.Async.*
import java.util.concurrent.ConcurrentLinkedQueue

val queue = Async.run {
  val queue = new ConcurrentLinkedQueue[String]()
  val cancellable = Async.fork {
    Async.delay(2.seconds)
    queue.add("cancellable")
  }
  Async.fork {
    Async.delay(500.millis)
    cancellable.cancel()
    queue.add("cancelled")
  }
  queue
}
```

## Concurrency Primitives

### Parallel Execution

Run computations in parallel:

```scala
val (result1, result2) = Async.par(computation1, computation2)
```

### Racing

Get the first result and cancel the other:

```scala
val winner = Async.race(computation1, computation2)
```

### Race with Pairs

Get the first result and the remaining fiber:

```scala
val (winner, remaining) = Async.racePair(computation1, computation2)
```

## Key Features

- **Structured Concurrency**: All fibers are properly managed and cleaned up
- **Cooperative Cancellation**: Based on JVM interruption
- **Parent-Child Relationships**: Cancelling a parent cancels all children
- **Exception Transparency**: Exceptions propagate naturally

## See Also

- [Communication Primitives](../communication-primitives.html) - Use Channels for communication between fibers
- [IO Effect](io.html) - For handling exceptions in async computations
- [Raise Effect](raise.html) - For typed error handling with cancellation
