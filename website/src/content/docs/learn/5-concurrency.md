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
import io.yaes.Async.*

def findUserByName(name: String): Option[User] = Some(User(name))

val fiber: Async ?=> Fiber[Option[User]] = Async.fork {
  findUserByName("John")
}
```

**Getting Values** — wait for a fiber's result:

```scala
import io.yaes.Raise.*

val maybeUser: (Async, Raise[Cancelled]) ?=> Option[User] = fiber.value
```

**Joining Fibers** — wait for completion without getting the value:

```scala
val result: Async ?=> Option[User] = fiber.join()
```

### Structured Concurrency

The `Async.run` handler creates a structured scope where all fibers are managed. The scope waits for all forked fibers to complete before returning:

```scala
import io.yaes.Async.*

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
import io.yaes.Async.*
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

### Unsupervised Scopes

`Async.unsupervised` is an alternative handler for blocks that fork fibers nobody waits on — daemon loops, fire-and-forget background work. When the block returns, any fiber still running is cancelled through cooperative interruption:

```scala
import io.yaes.Async.*
import scala.concurrent.duration.*

Async.run {
  Async.unsupervised {
    // Never joined: cancelled as soon as the block returns
    Async.fork {
      Async.delay(10.seconds)
      neverReached()
    }
    42
  } // returns 42 promptly, then cancels the forked fiber
}
```

How it differs from `Async.run`:

- **No waiting**: the handler returns as soon as the block does, then cancels the leftover fibers. It returns only once cancellation has propagated.
- **No fail-fast**: a fiber that throws and is never joined does not propagate its exception to the enclosing scope, and its siblings keep running. To observe a fiber's failure, join it explicitly with `join()` or `value`.
- **Same exception transparency**: an exception thrown from the main body of the block still propagates to the caller.

Supervision is a property of the scope, not of the fork. There is no separate "unsupervised fork" operation — `Async.fork` and `Async.forkNamed` work unchanged inside the block. Like `Async.run`, `Async.unsupervised` is a standalone handler providing its own `Async` capability, so it can be used on its own or nested in an existing scope. When nested, the enclosing scope is saved and restored, and is left untouched.

### Concurrency Primitives

**Parallel Execution** — run two computations in parallel:

```scala
val (result1, result2) = Async.par(computation1, computation2)
```

**Parallel Traversal** — apply a function to every element of a collection in parallel, returning results in input order. If any computation fails, remaining fibers are automatically cancelled (fail-fast):

```scala
import io.yaes.Async.*

case class UserProfile(id: Int, name: String)
def fetchUserProfile(id: Int)(using Async): UserProfile = ???

val profiles: Seq[UserProfile] = Async.run {
  Async.parTraverse(List(1, 2, 3, 4, 5))(fetchUserProfile)
}
```

`parTraverse` composes with other effects like `Raise`:

```scala
import io.yaes.Raise.*

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

**Bounded Parallel Traversal**: like `parTraverse`, but capping how many invocations of `f` run at the same time:

```scala
import io.yaes.Async.*

val profiles: Seq[UserProfile] = Async.run {
  // At most 3 calls to fetchUserProfile run at the same time.
  Async.parTraverseLimit(List(1, 2, 3, 4, 5), concurrency = 3)(fetchUserProfile)
}
```

Unlike `parTraverse`, `parTraverseLimit` does not fork one fiber per element. It forks at most `concurrency` worker fibers, capped at the number of elements, and each worker repeatedly claims the next unclaimed element and runs `f` on it. This keeps both the number of invocations in flight and the number of fibers created bounded by `concurrency`, no matter how large the input collection is, which makes it a good fit when `f` calls something with limited capacity, such as a connection pool or a rate limited API, and running every element at once would overwhelm it, or simply when the collection itself can be large.

Key properties of `parTraverseLimit`:
- At most `concurrency` invocations of `f`, and at most `concurrency` worker fibers, exist at the same time
- Results are returned in the same order as the input, regardless of completion or claim order
- If any invocation fails, every worker is cancelled; the failing worker flags the failure before its exception unwinds, so once that failure has been observed no worker invokes `f` on an element it had not already started, though an element a worker had already started still runs to completion
- A `concurrency` of `items.size` or greater produces the same result as `parTraverse`, but does not guarantee that every element runs on its own fiber, since workers still claim indices from a shared counter; computations that need every element running at the same time, such as a rendezvous or a barrier, must use `parTraverse` instead
- A non-positive `concurrency` is clamped to `1`, running fully sequentially, instead of throwing
- If the traversal is cancelled before every element is computed, it fails with `java.util.concurrent.CancellationException` rather than returning a partial result, mirroring `parTraverse`

**Racing** — get the first result and cancel the other:

```scala
val winner = Async.race(computation1, computation2)
```

`race` returns whichever branch completes first, success or failure — a fast failure beats a slow success.

**Racing for success** — like `race`, but ignore failures unless every branch fails:

```scala
val winner = Async.raceSuccess(computation1, computation2)
```

Unlike `race`, `raceSuccess` keeps waiting on the surviving branch when one of them fails, instead of letting a fast failure win. It only fails if *both* branches fail, surfacing the failure of whichever branch finished second. As soon as one branch succeeds, the other is cancelled, exactly like `race` does.

**Race with Pairs** — get the first result and the remaining fiber:

```scala
val (winner, remaining) = Async.racePair(computation1, computation2)
```

**Never Completing**: a computation that blocks forever on its own:

```scala
import io.yaes.Async.*
import scala.concurrent.duration.*

val result: Int = Async.run {
  Async.race(
    Async.never, // never completes on its own
    {
      Async.delay(1.second)
      42
    }
  )
}
// result == 42; the never branch is cancelled once the other one wins
```

`Async.never` parks the fiber efficiently, without busy-waiting, until it is cancelled. It is useful for a computation that must run until cancelled, or for a branch of `race`, `raceSuccess`, or `timeout` that should never complete on its own. It must always be forked, directly with `fork`/`attemptFork` or indirectly through a combinator such as `race` that forks its branches for you, and that fork must itself be cancelled, raced away, or wrapped in `timeout`. A bare `Async.run { Async.never }` with no fork in between hangs forever, and so does a forked `never` left un-cancelled and un-raced directly inside `run`, since `run` waits for every forked fiber to finish, joined or not. `Async.unsupervised` does not have that problem: it cancels any fiber still running as soon as its block returns. `par` and `parTraverse` wait for every branch to finish before returning, so pairing either of them with `never` deadlocks unconditionally.

### Key Features

- **Structured Concurrency**: All fibers are properly managed and cleaned up
- **Cooperative Cancellation**: Based on JVM interruption
- **Parent-Child Relationships**: Cancelling a parent cancels all children
- **Exception Transparency**: Exceptions propagate naturally
- **Unsupervised Scopes**: `Async.unsupervised` opts out of waiting and fail-fast when needed

---

## Shutdown Effect

The `Shutdown` effect provides graceful shutdown coordination for λÆS applications. It manages shutdown state and callback hooks, allowing applications to cleanly terminate concurrent operations and reject new work during shutdown.

The `Shutdown` effect automatically handles JVM shutdown signals (SIGTERM, SIGINT, Ctrl+C).

### Basic Usage

Check shutdown state to control work acceptance:

```scala
import io.yaes.Shutdown.*

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
import io.yaes.Shutdown.*

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
import io.yaes.Shutdown.*
import io.yaes.Output.*

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
import io.yaes.Shutdown.*
import io.yaes.Async.*
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
import io.yaes.{Async, Shutdown, Raise}
import io.yaes.Async.{Deadline, ShutdownTimedOut}
import scala.concurrent.duration.*

Shutdown.run {
  Raise.either {
    Async.withGracefulShutdown(Deadline.after(30.seconds)) {
      val serverFiber = Async.forkNamed("server") {
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
import io.yaes.{Async, Shutdown, Output, Raise}
import io.yaes.Async.{Deadline, ShutdownTimedOut}
import scala.concurrent.duration.*

val result: Either[ShutdownTimedOut, Unit] = Shutdown.run {
  Output.run {
    Raise.either {
      Async.withGracefulShutdown(Deadline.after(3.seconds)) {
        val slowFiber = Async.forkNamed("slow-work") {
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
import io.yaes.Shutdown.*
import io.yaes.Output.*
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
3. **Use Resource for cleanup**: Prefer the `Resource` effect (see [Step 6](/learn/6-state-and-resources/)) for resource cleanup over shutdown hooks
4. **Allow work to complete**: Don't abruptly terminate — let in-flight operations finish
5. **Log shutdown progress**: Use hooks to log shutdown milestones for debugging
6. **Choose appropriate deadlines**: Base the deadline on your longest normal operation and add a buffer for cleanup
