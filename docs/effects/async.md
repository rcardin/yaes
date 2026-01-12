
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

## Graceful Shutdown with Async

For long-running applications and daemon processes, the `Async.withGracefulShutdown` handler provides automatic shutdown coordination by combining the `Async` and `Shutdown` effects. This handler ensures your application can cleanly terminate concurrent operations within a specified deadline.

Unlike the standard `Async.run` handler, the `Async.withGracefulShutdown` handler is designed for applications that need to:
- Respond to JVM shutdown signals (SIGTERM, SIGINT, Ctrl+C)
- Enforce a maximum shutdown duration to prevent hanging
- Coordinate cleanup across multiple concurrent tasks

### Basic Usage

The handler requires a `Shutdown` context and provides an `Async` context to the block. When shutdown is initiated, it gives your main task a grace period (deadline) to complete cleanup operations:

```scala
import in.rcard.yaes.{Async, Shutdown}
import in.rcard.yaes.Async.Deadline
import scala.concurrent.duration.*

Shutdown.run {
  Async.withGracefulShutdown(Deadline.after(30.seconds)) {
    val serverFiber = Async.fork("server") {
      while (!Shutdown.isShuttingDown()) {
        // Process work
        Async.delay(100.millis)
      }
      println("Server stopped accepting work")
    }
    // For demonstration purposes, initiate a graceful shutdown programmatically
    Shutdown.initiateShutdown()
  }
}
```

### How It Works

The graceful shutdown lifecycle follows seven steps:

1. **Startup**: Main task and forked fibers begin executing
2. **Shutdown Signal**: Shutdown is initiated either via JVM signals (SIGTERM, SIGINT) or by calling `Shutdown.initiateShutdown()`
3. **Hook Execution**: The shutdown hook registered by `withGracefulShutdown` triggers `scope.initiateGracefulShutdown()`
4. **Grace Period**: Main task continues executing, allowing cleanup code to run while checking `Shutdown.isShuttingDown()`
5. **Main Task Completion**: When the main task completes, the scope shuts down and cooperatively cancels any remaining forked fibers
6. **Deadline Enforcement**: If the main task doesn't complete within the deadline, the timeout enforcer triggers and remaining fibers are cancelled
7. **Completion**: `scope.join()` completes when all fibers finish (or are cancelled)

**Key Distinctions:**
- **Main Task**: The block passed to `withGracefulShutdown` - tracked separately for completion logic
- **Forked Fibers**: Tasks created with `Async.fork` - cancelled when main task completes or deadline expires
- **Timeout Enforcer**: Internal fiber that waits for the deadline and then forces cancellation if needed

**Cooperative Cancellation:**
Fibers are cancelled using JVM interruption, meaning they must reach an interruptible operation (like `Async.delay`, blocking I/O, or lock acquisition) to be cancelled. Non-interruptible work cannot be forcibly stopped.

### Deadline Behavior

The deadline specifies how long the main task has to complete after shutdown is initiated. If the deadline expires before the main task finishes, the timeout enforcer cancels any remaining forked fibers.

**Timeout Enforcement Example:**

```scala
import in.rcard.yaes.{Async, Shutdown, Output}
import in.rcard.yaes.Async.Deadline
import scala.concurrent.duration.*

Shutdown.run {
  Output.run {
    Async.withGracefulShutdown(Deadline.after(3.seconds)) {
      val slowFiber = Async.fork("slow-work") {
        Async.delay(10.seconds) // Takes longer than deadline
        Output.printLn("Slow work completed") // Won't print
      }

      Shutdown.initiateShutdown()
    }
  }
}
```

**What Happens on Timeout:**
1. Deadline expires while main task is still running
2. Timeout enforcer calls `scope.shutdown()` to begin cancellation
3. All forked fibers are cooperatively cancelled via JVM interruption
4. Main task continues until it completes or is interrupted
5. `scope.join()` completes once all fibers finish

### Exception Handling

The `withGracefulShutdown` handler implements the same exception policy management as `Async.run`. 

### Best Practices

When using `withGracefulShutdown`, follow these guidelines:

1. **Check Shutdown State in Loops**
   ```scala
   while (!Shutdown.isShuttingDown()) {
     // Accept and process work
   }
   ```

2. **Choose Appropriate Deadlines**
   - Base the deadline on your longest normal operation
   - Add a buffer for cleanup (e.g., if operations take 10s, use 15-20s deadline)
   - Test with realistic workloads to verify the deadline is sufficient

3. **Perform Cleanup in Main Task**
   ```scala
   Async.withGracefulShutdown(deadline) {
     val fiber = Async.fork { /* work */ }
     fiber.join()

     // Cleanup code here runs after fiber completes
     closeConnections()
     flushBuffers()
   }
   ```

4. **Use Hooks for Notifications**
   ```scala
   Shutdown.onShutdown(() => {
     logger.info("Shutdown initiated")
     metrics.recordShutdown()
   })
   ```

5. **Track Active Work**
   - Use `AtomicInteger` or similar for concurrent counters
   - Wait for active work to complete within the deadline
   - Log progress for debugging

6. **Test Shutdown Behavior**
   - Trigger shutdown programmatically with `Shutdown.initiateShutdown()`
   - Verify operations complete within the deadline
   - Test timeout scenarios where work exceeds the deadline

7. **Avoid Long-Running Cleanup**
   - Cleanup should be fast (seconds, not minutes)
   - If cleanup might take long, increase the deadline
   - Consider async cleanup that doesn't block shutdown

### Comparison: Async.run vs withGracefulShutdown

| Aspect | `Async.run` | `withGracefulShutdown` |
|--------|-------------|------------------------|
| **Use Case** | Short-lived computations | Long-running services, daemons |
| **Shutdown Support** | None (fail-fast on errors) | Full graceful shutdown coordination |
| **Effect Requirements** | None | Requires `Shutdown` context |
| **Deadline Enforcement** | No | Yes, configurable grace period |
| **JVM Signal Handling** | No | Yes (via Shutdown effect) |
| **Cleanup Control** | Immediate cancellation on error | Controlled shutdown with cleanup time |
| **Typical Duration** | Milliseconds to seconds | Minutes to hours (or indefinite) |

**When to use `Async.run`:**
- Short-lived parallel computations
- No need for graceful shutdown
- Fail-fast behavior is desired

**When to use `withGracefulShutdown`:**
- Long-running servers or services
- Daemon processes
- Need to respond to SIGTERM/SIGINT
- Must complete in-flight operations before exit
- Want deadline enforcement to prevent hanging

## See Also

- [Shutdown Effect](shutdown.html) - Full documentation on graceful shutdown coordination
- [Communication Primitives](../communication-primitives.html) - Use Channels for communication between fibers
- [IO Effect](io.html) - For handling exceptions in async computations
- [Raise Effect](raise.html) - For typed error handling with cancellation
