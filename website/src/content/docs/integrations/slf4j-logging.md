---
title: SLF4J Logging
description: SLF4J logging backend for the λÆS Log effect with structured logging and level filtering.
sidebar:
  label: SLF4J Logging
  order: 2
---

The `Log` effect provides structured logging capabilities at different levels with configurable output.

## Overview

The `Log` effect offers a functional approach to logging with level-based filtering and customizable formatting. The logging level is a handler concern; set it once at `Log.run(level)` and all loggers within the block inherit it.

## Log Levels

Available log levels (in order of severity):

- `TRACE` - Detailed diagnostic information
- `DEBUG` - Debug information for development
- `INFO` - General informational messages
- `WARN` - Warning messages for potential issues
- `ERROR` - Error messages for failures
- `FATAL` - Critical errors that may cause termination

## Basic Usage

### Creating a Logger

```scala
import in.rcard.yaes.Log.*

val logger: Log ?=> Logger = Log.getLogger("MyApp")
```

### Logging Messages

```scala
import in.rcard.yaes.Log.*

def businessLogic(using Log): Unit = {
  val logger = Log.getLogger("BusinessLogic")

  logger.trace("Entering business logic")
  logger.debug("Processing user request")
  logger.info("User request processed successfully")
  logger.warn("Deprecated API used")
  logger.error("Failed to save to database")
  logger.fatal("System is shutting down")
}
```

## Level Filtering

The logging level is set at the handler, controlling which messages are emitted for all loggers within the block:

```scala
import in.rcard.yaes.Log.*

Log.run(Log.Level.Info) {
  val logger = Log.getLogger("InfoLogger")

  logger.trace("This won't be shown")   // Below Info level
  logger.debug("This won't be shown")   // Below Info level
  logger.info("This will be shown")     // At Info level
  logger.warn("This will be shown")     // Above Info level
  logger.error("This will be shown")    // Above Info level
}
```

## Output Format

The default `ConsoleLogger` outputs messages in this format:

```
2025-06-11T10:30:45 - INFO - MyLogger - User logged in successfully
2025-06-11T10:30:46 - WARN - MyLogger - Session will expire soon
2025-06-11T10:30:47 - ERROR - MyLogger - Database connection failed
```

## Running Log Effects

### Basic Usage

```scala
import in.rcard.yaes.Log.*

// Default level is Debug
val result = Log.run() {
  val logger = Log.getLogger("Application")
  logger.info("Application started")

  // Your application logic here

  logger.info("Application finished")
}
```

### With Explicit Level

```scala
import in.rcard.yaes.Log.*

val result = Log.run(Log.Level.Info) {
  val logger = Log.getLogger("Application")
  logger.debug("This will NOT be printed")
  logger.info("Application started")
}
```

### Custom Clock

You can provide a custom clock for timestamps:

```scala
import in.rcard.yaes.Log.*
import java.time.Clock

given customClock: Clock = Clock.systemUTC()

val result = Log.run(Log.Level.Info) {
  // Logging will use UTC timestamps
  val logger = Log.getLogger("UTCApp")
  logger.info("Using UTC timestamps")
}
```

## Practical Examples

### Application Lifecycle

```scala
import in.rcard.yaes.Log.*
import in.rcard.yaes.System.*

def startApplication(using Log, System): Unit = {
  val logger = Log.getLogger("Application")

  logger.info("Application starting...")

  val environment = System.env[String]("ENVIRONMENT", "development")
  logger.info(s"Running in environment: $environment")

  // Load configuration
  logger.debug("Loading configuration...")

  // Initialize services
  logger.info("Initializing services...")

  logger.info("Application started successfully")
}
```

### Error Handling with Logging

```scala
import in.rcard.yaes.Log.*
import in.rcard.yaes.Raise.*

sealed trait ServiceError
case class DatabaseError(message: String) extends ServiceError
case class NetworkError(message: String) extends ServiceError

def processRequest(userId: Int)(using Log, Raise[ServiceError]): String = {
  val logger = Log.getLogger("RequestProcessor")

  logger.info(s"Processing request for user: $userId")

  try {
    // Simulate database operation
    if (userId < 0) {
      logger.error(s"Invalid user ID: $userId")
      Raise.raise(DatabaseError("Invalid user ID"))
    }

    logger.debug(s"Database query successful for user: $userId")
    logger.info(s"Request processed successfully for user: $userId")

    s"Success for user $userId"

  } catch {
    case ex: Exception =>
      logger.error(s"Unexpected error processing user $userId: ${ex.getMessage}")
      Raise.raise(NetworkError(ex.getMessage))
  }
}
```

### Performance Monitoring

```scala
import in.rcard.yaes.Log.*
import in.rcard.yaes.Clock.*

def monitoredOperation[A](name: String)(operation: => A)(using Log, Clock): A = {
  val logger = Log.getLogger("Performance")

  logger.debug(s"Starting operation: $name")
  val startTime = Clock.nowMonotonic()

  try {
    val result = operation
    val duration = Clock.nowMonotonic().minus(startTime)
    logger.info(s"Operation '$name' completed successfully in ${duration.toMillis}ms")
    result
  } catch {
    case ex: Exception =>
      val duration = Clock.nowMonotonic().minus(startTime)
      logger.error(s"Operation '$name' failed after ${duration.toMillis}ms: ${ex.getMessage}")
      throw ex
  }
}
```

### Structured Logging Context

```scala
import in.rcard.yaes.Log.*

case class RequestContext(requestId: String, userId: Option[Int], clientIp: String)

def contextualLogging(context: RequestContext)(using Log): Unit = {
  val logger = Log.getLogger("RequestHandler")

  val baseMessage = s"[${context.requestId}] [${context.clientIp}]"
  val userInfo = context.userId.map(id => s" [user:$id]").getOrElse("")

  logger.info(s"$baseMessage$userInfo Request started")

  // Process request...

  logger.info(s"$baseMessage$userInfo Request completed")
}
```

### Multiple Loggers

```scala
import in.rcard.yaes.Log.*

def multipleLoggers(using Log): Unit = {
  val accessLogger = Log.getLogger("AccessLog")
  val errorLogger = Log.getLogger("ErrorLog")
  val debugLogger = Log.getLogger("DebugLog")

  accessLogger.info("User accessed /api/users")
  debugLogger.trace("Detailed trace information")
  errorLogger.error("Critical system error occurred")
}
```

## Advanced Configuration

### Environment-Based Log Level

Since the log level is a handler concern, configure it at the application entry point using environment variables:

```scala
import in.rcard.yaes.Log.*
import in.rcard.yaes.Log.given
import in.rcard.yaes.System.*

def runWithEnvLevel(block: Log ?=> Unit)(using System): Unit = {
  val levelString = System.env[String]("LOG_LEVEL", "INFO")
  val level = levelString.toUpperCase match {
    case "TRACE" => Log.Level.Trace
    case "DEBUG" => Log.Level.Debug
    case "INFO" => Log.Level.Info
    case "WARN" => Log.Level.Warn
    case "ERROR" => Log.Level.Error
    case "FATAL" => Log.Level.Fatal
    case _ => Log.Level.Info
  }

  Log.run(level) {
    block
  }
}
```

## SLF4J Integration

The `yaes-slf4j` module provides an alternative handler that delegates logging to [SLF4J](https://www.slf4j.org/), letting you use any SLF4J-compatible backend (Logback, Log4j2, slf4j-simple, etc.) without changing application code.

### Setup

Add the `yaes-slf4j` dependency:

```sbt
libraryDependencies += "in.rcard.yaes" %% "yaes-slf4j" % "0.15.0"
```

And an SLF4J backend, for example Logback:

```sbt
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.18"
```

### Usage

Replace `Log.run` with `Slf4jLog.run`. All existing `Log.getLogger` and logger method calls remain unchanged:

```scala
import in.rcard.yaes.Log
import in.rcard.yaes.slf4j.Slf4jLog

Slf4jLog.run {
  val logger = Log.getLogger("MyService")
  logger.info("Hello from SLF4J!")
}
```

### Level Configuration

Unlike `Log.run(level)`, `Slf4jLog.run` does **not** accept a level parameter. Level filtering is controlled entirely by the SLF4J backend configuration (e.g., `logback.xml`, `simplelogger.properties`).

### Key Differences from Log.run

| Feature | `Log.run` | `Slf4jLog.run` |
|---------|-----------|----------------|
| Backend | Built-in `ConsoleLogger` | Any SLF4J backend |
| Level control | `Log.run(level)` parameter | SLF4J backend config |
| Output format | Fixed `timestamp - LEVEL - name - msg` | Backend-configurable |
| Timestamps | `java.time.Clock` (configurable) | Backend-managed |
| FATAL level | Native support | Maps to ERROR |

### Lazy Message Evaluation

Messages are **not** evaluated when the level is disabled by the SLF4J backend:

```scala
Slf4jLog.run {
  val logger = Log.getLogger("MyService")
  // If DEBUG is disabled, the string interpolation never runs
  logger.debug(s"Expensive: ${heavyComputation()}")
}
```

## Implementation Details

- Default implementation uses `ConsoleLogger`
- SLF4J integration available via the `yaes-slf4j` module
- Thread-safe logging operations
- Configurable clock for timestamp generation (console handler)
- Level-based filtering at the handler level
- ISO-8601 timestamp format (console handler)

## Best Practices

- Use appropriate log levels for different types of messages
- Include contextual information (request IDs, user IDs, etc.)
- Set log levels based on environment (TRACE/DEBUG in dev, INFO+ in production)
- Use structured message format for easier parsing
- Combine with other effects for comprehensive logging
- Avoid logging sensitive information (passwords, tokens, etc.)
