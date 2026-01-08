
# Shutdown Effect

The `Shutdown` effect provides graceful shutdown coordination for YAES applications. It manages shutdown state and callback hooks, allowing applications to cleanly terminate concurrent operations and reject new work during shutdown.

## Overview

The `Shutdown` effect automatically handles JVM shutdown signals (SIGTERM, SIGINT, Ctrl+C) and provides coordination mechanisms for graceful application termination. It's particularly useful for long-running services, daemon processes, and applications with concurrent operations.

## Basic Usage

### Simple Shutdown Coordination

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

### Manual Shutdown Trigger

Programmatically initiate shutdown for testing or controlled termination:

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

## Shutdown Hooks

### Registering Callbacks

Use `onShutdown` to register callbacks that execute when shutdown is initiated:

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

  // Start server
  while (!Shutdown.isShuttingDown()) {
    // Accept and process requests
  }
}
```

### Hook Execution Order

Hooks execute synchronously in registration order after the shutdown state transition:

```scala
import in.rcard.yaes.Shutdown.*

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

### Late Hook Registration

Hooks registered after shutdown has begun are **silently ignored**:

```scala
import in.rcard.yaes.Shutdown.*

Shutdown.run {
  Shutdown.onShutdown(() => {
    println("First hook executing")

    // This hook will NOT be called
    Shutdown.onShutdown(() => println("Late hook - won't execute"))
  })

  Shutdown.initiateShutdown()
  // Output:
  // First hook executing
  // (Late hook is ignored)
}
```

This behavior prevents race conditions and ensures predictable shutdown sequences. Register all hooks before shutdown begins.

## Error Handling

### Exception Safety

Hooks are wrapped in exception handling - if one hook fails, others continue to execute:

```scala
import in.rcard.yaes.Shutdown.*

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

### Idempotent Shutdown

Multiple shutdown calls are safe - hooks execute only once:

```scala
import in.rcard.yaes.Shutdown.*

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

## Integration with Other Effects

### With Resource Management

Combine with `Resource` for guaranteed cleanup:

```scala
import in.rcard.yaes.Shutdown.*
import in.rcard.yaes.Resource.*
import java.io.FileInputStream

def processWithResources()(using Shutdown, Resource): Unit = {
  val file = Resource.acquire(new FileInputStream("data.txt"))

  Shutdown.onShutdown(() => {
    println("Shutdown initiated - finishing current operation")
  })

  while (!Shutdown.isShuttingDown()) {
    // Process file data
  }
  // File automatically closed by Resource effect
}

Shutdown.run {
  Resource.run {
    processWithResources()
  }
}
```

### With Async (Daemon Processes)

The `Shutdown` effect is designed to work seamlessly with `Async` for daemon processes:

```scala
import in.rcard.yaes.Shutdown.*
import in.rcard.yaes.Async.*
import scala.concurrent.duration.*

def daemonServer()(using Shutdown, Async): Unit = {
  val server = Async.fork {
    startServer()
  }

  // Wait until shutdown is initiated
  while (!Shutdown.isShuttingDown()) {
    Async.delay(100.millis)
  }

  // Gracefully stop the server
  server.cancel()
}

Shutdown.run {
  Async.run {
    daemonServer()
  }
}
```

## Practical Examples

### HTTP Server

```scala
import in.rcard.yaes.Shutdown.*
import in.rcard.yaes.Output.*
import java.util.concurrent.atomic.AtomicInteger

case class Request(path: String)
case class Response(status: Int, body: String)

def httpServer(port: Int)(using Shutdown, Output): Unit = {
  val activeRequests = new AtomicInteger(0)

  Shutdown.onShutdown(() => {
    Output.printLn(s"Shutdown initiated with ${activeRequests.get()} active requests")
    Output.printLn("Waiting for active requests to complete...")
  })

  while (!Shutdown.isShuttingDown() || activeRequests.get() > 0) {
    // Accept new requests only if not shutting down
    if (!Shutdown.isShuttingDown()) {
      val request = acceptRequest()
      activeRequests.incrementAndGet()

      processRequest(request)
      activeRequests.decrementAndGet()
    } else {
      Thread.sleep(100)  // Wait for active requests
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

### Background Worker

```scala
import in.rcard.yaes.Shutdown.*
import in.rcard.yaes.Log.*

def backgroundWorker()(using Shutdown, Log): Unit = {
  val logger = Log.getLogger("Worker")

  Shutdown.onShutdown(() => {
    logger.info("Shutdown signal received, completing current job")
  })

  while (!Shutdown.isShuttingDown()) {
    val job = pollJobQueue()

    job.foreach { j =>
      logger.info(s"Processing job ${j.id}")
      processJob(j)
      logger.info(s"Completed job ${j.id}")
    }
  }

  logger.info("Worker shutdown complete")
}
```

### Metrics Reporter

```scala
import in.rcard.yaes.Shutdown.*
import scala.concurrent.duration.*

def metricsReporter(interval: Duration)(using Shutdown): Unit = {
  var lastReport = System.currentTimeMillis()

  Shutdown.onShutdown(() => {
    // Send final metrics on shutdown
    sendMetrics()
    println("Final metrics reported")
  })

  while (!Shutdown.isShuttingDown()) {
    val now = System.currentTimeMillis()

    if (now - lastReport >= interval.toMillis) {
      sendMetrics()
      lastReport = now
    }

    Thread.sleep(100)
  }
}
```

## JVM Shutdown Hooks

The `Shutdown` effect automatically registers a JVM shutdown hook that:

- Listens for SIGTERM and SIGINT signals
- Triggers `initiateShutdown()` when JVM is shutting down
- Is properly cleaned up when the program completes normally
- Handles the case where the JVM is already shutting down

This means your application will automatically respond to:
- Ctrl+C in the terminal
- `kill` command (SIGTERM)
- Container orchestration shutdown signals (Docker, Kubernetes)
- IDE stop button

## Key Features

- **Automatic Signal Handling**: Responds to SIGTERM/SIGINT without manual setup
- **Callback Hooks**: Register multiple shutdown callbacks that execute in order
- **Exception Safety**: Hook failures don't prevent other hooks from executing
- **Idempotent**: Multiple shutdown calls are safe and predictable
- **Thread-Safe**: State transitions and hook management use proper synchronization
- **Composable**: Works seamlessly with other YAES effects

## Best Practices

1. **Check State Before Accepting Work**: Always check `isShuttingDown()` before starting new operations
2. **Use Hooks for Notifications**: Register hooks to notify components (like async scopes) about shutdown
3. **Use Resource for Cleanup**: Prefer `Resource` effect for resource cleanup over shutdown hooks
4. **Allow Work to Complete**: Don't abruptly terminate - let in-flight operations finish
5. **Log Shutdown Progress**: Use hooks to log shutdown milestones for debugging

## Thread Safety

The `Shutdown` effect provides thread-safe operations:

- **State transitions** are protected by locks - multiple threads can safely call `initiateShutdown()`
- **Hook registration** is thread-safe - `onShutdown()` can be called from any thread
- **State checking** is thread-safe - `isShuttingDown()` can be called from any thread
- **Hook callbacks** execute outside the lock to prevent deadlock scenarios
- **Late registration**: Hooks registered after shutdown has begun are silently ignored

**Important**: Your hook callbacks themselves must be thread-safe if they access shared state.

## Notes

- Shutdown hooks are **user callbacks**, not JVM Runtime shutdown hooks
- For resource cleanup, prefer the `Resource` effect which guarantees LIFO order
- Hooks execute **after** the state transitions to SHUTTING_DOWN
- Hooks registered after shutdown has started are **silently ignored**
- Hook execution failures are logged to `System.err` but don't prevent other hooks from running
