---
title: Concurrency
description: Learn structured concurrency with the Async effect and graceful shutdown coordination with the Shutdown effect.
sidebar:
  label: "5. Concurrency"
  order: 5
---

λÆS builds concurrency on Java's Virtual Threads and Structured Concurrency (Java 21+). The `Async` effect provides fiber management with structured scopes, and the `Shutdown` effect coordinates graceful termination for long-running services.

---

## Async Effect

The `Async` effect enables asynchronous computations and structured concurrency with fiber management. It wraps Java's Structured Concurrency, ensuring all fibers are properly managed and cleaned up.

### Core Operations

**Forking Fibers** — create lightweight threads (fibers) for concurrent execution:

```scala
import in.rcard.yaes.Async.*

def findUserByName(name: String): Option[User] = Some(User(name))

val fiber: Async ?=> Fiber[Option[User]] = Async.fork {
  findUserByName("John")
}
```

**Getting Values** — wait for a fiber's result:

```scala
import in.rcard.yaes.Raise.*

val maybeUser: (Async, Raise[Cancelled]) ?=> Option[User] = fiber.value
```

**Joining Fibers** — wait for completion without getting the value:

```scala
val result: Async ?=> Option[User] = fiber.join()
```

### Structured Concurrency

The `Async.run` handler creates a structured scope where all fibers are managed. The scope waits for all forked fibers to complete before returning:

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

### Cancellation

Fibers can be cancelled cooperatively. Cancellation propagates via JVM interruption — fibers must reach an interruptible operation to be cancelled:

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

### Concurrency Primitives

**Parallel Execution** — run two computations in parallel:

```scala
val (result1, result2) = Async.par(computation1, computation2)
```

**Parallel Traversal** — apply a function to every element of a collection in parallel, returning results in input order. If any computation fails, remaining fibers are automatically cancelled (fail-fast):

```scala
import in.rcard.yaes.Async.*

case class UserProfile(id: Int, name: String)
def fetchUserProfile(id: Int)(using Async): UserProfile = ???

val profiles: Seq[UserProfile] = Async.run {
  Async.parTraverse(List(1, 2, 3, 4, 5))(fetchUserProfile)
}
```

`parTraverse` composes with other effects like `Raise`:

```scala
import in.rcard.yaes.Raise.*

def validateAndFetch(id: Int)(using Async, Raise[String]): UserProfile =
  if (id <= 0) Raise.raise(s"Invalid id: $id")
  else fetchUserProfile(id)

val result: Either[String, Seq[UserProfile]] = Raise.either {
  Async.run {
    Async.parTraverse(List(1, 2, 3))(validateAndFetch)
  }
}
```

Key properties of `parTraverse`:
- Elements are processed concurrently, one fiber per element
- Results are returned in the same order as the input
- If any fiber fails, all remaining fibers are cancelled
- Works with an empty collection (returns an empty `Seq`)

**Racing** — get the first result and cancel the other:

```scala
val winner = Async.race(computation1, computation2)
```

**Race with Pairs** — get the first result and the remaining fiber:

```scala
val (winner, remaining) = Async.racePair(computation1, computation2)
```

### Key Features

- **Structured Concurrency**: All fibers are properly managed and cleaned up
- **Cooperative Cancellation**: Based on JVM interruption
- **Parent-Child Relationships**: Cancelling a parent cancels all children
- **Exception Transparency**: Exceptions propagate naturally

---

## Shutdown Effect

The `Shutdown` effect provides graceful shutdown coordination for λÆS applications. It manages shutdown state and callback hooks, allowing applications to cleanly terminate concurrent operations and reject new work during shutdown.

The `Shutdown` effect automatically handles JVM shutdown signals (SIGTERM, SIGINT, Ctrl+C).

### Basic Usage

Check shutdown state to control work acceptance:

```scala
import in.rcard.yaes.Shutdown.*

def processWork()(using Shutdown): Unit = {
  while (!Shutdown.isShuttingDown()) {
    // Process work items
    println("Processing...")
    Thread.sleep(1000)
  }
  println("Shutdown initiated, stopping work")
}

Shutdown.run {
  processWork()
}
```

**Manual Shutdown Trigger:**

```scala
import in.rcard.yaes.Shutdown.*

def healthMonitor()(using Shutdown): Unit = {
  val isHealthy = checkSystemHealth()

  if (!isHealthy) {
    println("System unhealthy, initiating shutdown")
    Shutdown.initiateShutdown()
  }
}
```

### Shutdown Hooks

Register callbacks that execute when shutdown is initiated:

```scala
import in.rcard.yaes.Shutdown.*
import in.rcard.yaes.Output.*

def serverWithHooks()(using Shutdown, Output): Unit = {
  Shutdown.onShutdown(() => {
    Output.printLn("Shutdown signal received")
    Output.printLn("Stopping new request acceptance")
  })

  Shutdown.onShutdown(() => {
    Output.printLn("Logging final metrics")
  })

  while (!Shutdown.isShuttingDown()) {
    // Accept and process requests
  }
}
```

Hooks execute synchronously in registration order after the shutdown state transition:

```scala
Shutdown.run {
  Shutdown.onShutdown(() => println("First hook"))
  Shutdown.onShutdown(() => println("Second hook"))
  Shutdown.onShutdown(() => println("Third hook"))

  Shutdown.initiateShutdown()
  // Output:
  // First hook
  // Second hook
  // Third hook
}
```

:::note
Hooks registered **after** shutdown has begun are silently ignored. This prevents race conditions and ensures predictable shutdown sequences. Register all hooks before shutdown begins.
:::

### Error Handling in Hooks

Hooks are wrapped in exception handling — if one hook fails, others continue to execute:

```scala
Shutdown.run {
  Shutdown.onShutdown(() => {
    throw new RuntimeException("Hook 1 failed!")
  })

  Shutdown.onShutdown(() => {
    println("Hook 2 still executes")
  })

  Shutdown.initiateShutdown()
  // Output: Hook 2 still executes
}
```

Multiple shutdown calls are safe — hooks execute only once (idempotent):

```scala
Shutdown.run {
  var count = 0
  Shutdown.onShutdown(() => count += 1)

  Shutdown.initiateShutdown()
  Shutdown.initiateShutdown()
  Shutdown.initiateShutdown()

  println(s"Hook called $count times")
  // Output: Hook called 1 times
}
```

### Integration with Async: Basic Approach

Combine `Shutdown` with `Async` for daemon process coordination:

```scala
import in.rcard.yaes.Shutdown.*
import in.rcard.yaes.Async.*
import scala.concurrent.duration.*

def daemonServer()(using Shutdown, Async): Unit = {
  val server = Async.fork {
    startServer()
  }

  while (!Shutdown.isShuttingDown()) {
    Async.delay(100.millis)
  }

  server.cancel()
}

Shutdown.run {
  Async.run {
    daemonServer()
  }
}
```

### Graceful Shutdown with `withGracefulShutdown`

For production applications, the `Async.withGracefulShutdown` handler provides automatic shutdown coordination with deadline enforcement. Use this when you need to:
- Automatically respond to JVM shutdown signals (SIGTERM, SIGINT)
- Enforce a maximum shutdown duration
- Coordinate cleanup across multiple concurrent tasks

The handler requires both `Shutdown` and `Raise[Async.ShutdownTimedOut]` contexts:

```scala
import in.rcard.yaes.{Async, Shutdown, Raise}
import in.rcard.yaes.Async.{Deadline, ShutdownTimedOut}
import scala.concurrent.duration.*

Shutdown.run {
  Raise.either {
    Async.withGracefulShutdown(Deadline.after(30.seconds)) {
      val serverFiber = Async.fork("server") {
        while (!Shutdown.isShuttingDown()) {
          // Process work
          Async.delay(100.millis)
        }
        println("Server stopped accepting work")
      }
      // Initiate a graceful shutdown programmatically
      Shutdown.initiateShutdown()
    }
  }
} // Returns Either[ShutdownTimedOut, Unit]
```

**The Graceful Shutdown Lifecycle:**

1. **Startup**: Main task and forked fibers begin executing
2. **Shutdown Signal**: Shutdown is initiated via JVM signals or `Shutdown.initiateShutdown()`
3. **Hook Execution**: The shutdown hook registered by `withGracefulShutdown` triggers `scope.initiateGracefulShutdown()`
4. **Grace Period**: Main task continues executing, allowing cleanup while checking `Shutdown.isShuttingDown()`
5. **Main Task Completion**: When the main task completes, the scope shuts down and cancels remaining forked fibers
6. **Deadline Enforcement**: If the main task doesn't complete within the deadline, the timeout enforcer triggers and `ShutdownTimedOut` is raised
7. **Completion**: `scope.join()` completes when all fibers finish

**Timeout Enforcement Example:**

```scala
import in.rcard.yaes.{Async, Shutdown, Output, Raise}
import in.rcard.yaes.Async.{Deadline, ShutdownTimedOut}
import scala.concurrent.duration.*

val result: Either[ShutdownTimedOut, Unit] = Shutdown.run {
  Output.run {
    Raise.either {
      Async.withGracefulShutdown(Deadline.after(3.seconds)) {
        val slowFiber = Async.fork("slow-work") {
          Async.delay(10.seconds) // Takes longer than deadline
          Output.printLn("Slow work completed") // Won't print
        }

        Shutdown.initiateShutdown()
        slowFiber.join() // Wait for fiber that won't complete in time
      }
    }
  }
}
// result is Left(ShutdownTimedOut) because deadline expired
```

### Choosing Between Async.run and withGracefulShutdown

| Aspect | `Async.run` | `withGracefulShutdown` |
|--------|-------------|------------------------|
| **Use Case** | Short-lived computations | Long-running services, daemons |
| **Shutdown Support** | None (fail-fast on errors) | Full graceful shutdown coordination |
| **Effect Requirements** | None | Requires `Shutdown` and `Raise[ShutdownTimedOut]` |
| **Deadline Enforcement** | No | Yes, configurable grace period |
| **JVM Signal Handling** | No | Yes (via Shutdown effect) |
| **Typical Duration** | Milliseconds to seconds | Minutes to hours (or indefinite) |

### Practical Examples

**HTTP Server:**

```scala
import in.rcard.yaes.Shutdown.*
import in.rcard.yaes.Output.*
import java.util.concurrent.atomic.AtomicInteger

def httpServer(port: Int)(using Shutdown, Output): Unit = {
  val activeRequests = new AtomicInteger(0)

  Shutdown.onShutdown(() => {
    Output.printLn(s"Shutdown initiated with ${activeRequests.get()} active requests")
    Output.printLn("Waiting for active requests to complete...")
  })

  while (!Shutdown.isShuttingDown() || activeRequests.get() > 0) {
    if (!Shutdown.isShuttingDown()) {
      val request = acceptRequest()
      activeRequests.incrementAndGet()
      processRequest(request)
      activeRequests.decrementAndGet()
    } else {
      Thread.sleep(100)
    }
  }

  Output.printLn("All requests processed, server shutdown complete")
}

Shutdown.run {
  Output.run {
    httpServer(8080)
  }
}
```

### JVM Shutdown Hooks

The `Shutdown` effect automatically registers a JVM shutdown hook that:

- Listens for SIGTERM and SIGINT signals
- Triggers `initiateShutdown()` when JVM is shutting down
- Is properly cleaned up when the program completes normally

This means your application automatically responds to:
- Ctrl+C in the terminal
- `kill` command (SIGTERM)
- Container orchestration shutdown signals (Docker, Kubernetes)
- IDE stop button

### Best Practices

1. **Check state before accepting work**: Always check `isShuttingDown()` before starting new operations
2. **Use hooks for notifications**: Register hooks to notify components about shutdown
3. **Use Resource for cleanup**: Prefer the `Resource` effect (see [Step 6](/learn/6-state-and-resources)) for resource cleanup over shutdown hooks
4. **Allow work to complete**: Don't abruptly terminate — let in-flight operations finish
5. **Log shutdown progress**: Use hooks to log shutdown milestones for debugging
6. **Choose appropriate deadlines**: Base the deadline on your longest normal operation and add a buffer for cleanup
