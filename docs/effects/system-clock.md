---
layout: default
title: "System & Clock Effects"
---

# System & Clock Effects

These effects provide access to system-level information and time management.

## System Effect

The `System` effect provides type-safe access to system properties and environment variables.

### Environment Variables

```scala
import in.rcard.yaes.System.*
import in.rcard.yaes.Raise.*

// With potential parsing errors
val port: (System, Raise[NumberFormatException]) ?=> Option[Int] = 
  System.env[Int]("PORT")

// With default value
val host: System ?=> String = 
  System.env[String]("HOST", "localhost")
```

### System Properties

```scala
import in.rcard.yaes.System.*
import in.rcard.yaes.Raise.*

// With potential parsing errors  
val serverPort: (System, Raise[NumberFormatException]) ?=> Option[Int] = 
  System.property[Int]("server.port")

// With default value
val serverHost: System ?=> String = 
  System.property[String]("server.host", "localhost")
```

### Supported Types

The `System` effect supports parsing for:

- `String` - Direct string values
- `Int` - Integer parsing
- `Long` - Long integer parsing  
- `Double` - Double precision parsing
- `Boolean` - Boolean parsing
- `Float` - Float parsing
- `Short` - Short integer parsing
- `Byte` - Byte parsing
- `Char` - Character parsing

### Configuration Example

```scala
import in.rcard.yaes.System.*
import in.rcard.yaes.Raise.*

case class DatabaseConfig(
  host: String,
  port: Int,
  database: String,
  ssl: Boolean
)

def loadDatabaseConfig(using System, Raise[String]): DatabaseConfig = {
  val host = System.env[String]("DB_HOST", "localhost")
  
  val port = System.env[Int]("DB_PORT").getOrElse {
    Raise.raise("DB_PORT environment variable is required")
  }
  
  val database = System.env[String]("DB_NAME").getOrElse {
    Raise.raise("DB_NAME environment variable is required") 
  }
  
  val ssl = System.env[Boolean]("DB_SSL", "false").toBoolean
  
  DatabaseConfig(host, port, database, ssl)
}
```

## Clock Effect

The `Clock` effect provides time management operations.

### Current Time

```scala
import in.rcard.yaes.Clock.*
import java.time.Instant

val currentTime: Clock ?=> Instant = Clock.now()
```

### Monotonic Time

For measuring durations and intervals:

```scala
import in.rcard.yaes.Clock.*
import java.time.Duration

val monotonicTime: Clock ?=> Duration = Clock.nowMonotonic()
```

### Time Measurement Example

```scala
import in.rcard.yaes.Clock.*
import in.rcard.yaes.Output.*

def measureOperation[A](operation: => A)(using Clock, Output): A = {
  val startTime = Clock.nowMonotonic()
  val result = operation
  val endTime = Clock.nowMonotonic()
  val duration = endTime.minus(startTime)
  
  Output.printLn(s"Operation took: ${duration.toMillis}ms")
  result
}
```

### Timestamped Logging

```scala
import in.rcard.yaes.Clock.*
import in.rcard.yaes.Output.*

def logWithTimestamp(message: String)(using Clock, Output): Unit = {
  val timestamp = Clock.now()
  Output.printLn(s"[$timestamp] $message")
}

def timedProcess(using Clock, Output): Unit = {
  logWithTimestamp("Process started")
  
  // Simulate work
  Thread.sleep(1000)
  
  logWithTimestamp("Process completed")
}
```

## Combined Usage

### Application Bootstrap

```scala
import in.rcard.yaes.System.*
import in.rcard.yaes.Clock.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Raise.*

case class AppInfo(
  name: String,
  version: String,
  startTime: java.time.Instant,
  environment: String
)

def initializeApp(using System, Clock, Output, Raise[String]): AppInfo = {
  val startTime = Clock.now()
  
  val name = System.property[String]("app.name", "λÆS Application")
  val version = System.property[String]("app.version", "1.0.0")
  val environment = System.env[String]("ENVIRONMENT", "development")
  
  val info = AppInfo(name, version, startTime, environment)
  
  Output.printLn(s"Starting ${info.name} v${info.version}")
  Output.printLn(s"Environment: ${info.environment}")
  Output.printLn(s"Start time: ${info.startTime}")
  
  info
}

// Usage
val appInfo = Raise.either {
  Output.run {
    Clock.run {
      System.run {
        initializeApp
      }
    }
  }
}
```

### Performance Monitoring

```scala
import in.rcard.yaes.System.*
import in.rcard.yaes.Clock.*
import in.rcard.yaes.Log.*

def monitoredOperation[A](name: String)(operation: => A)(using Clock, Log): A = {
  val logger = Log.getLogger("Performance")
  val debugEnabled = System.env[Boolean]("DEBUG", "false")
  
  if (debugEnabled) {
    logger.debug(s"Starting operation: $name")
  }
  
  val startTime = Clock.nowMonotonic()
  val result = operation
  val endTime = Clock.nowMonotonic()
  val duration = endTime.minus(startTime)
  
  logger.info(s"Operation '$name' completed in ${duration.toMillis}ms")
  result
}
```

## Running Effects

```scala
import in.rcard.yaes.System.*
import in.rcard.yaes.Clock.*

// Individual effects
val systemResult = System.run { /* system operations */ }
val clockResult = Clock.run { /* time operations */ }

// Combined
val combinedResult = Clock.run {
  System.run {
    // operations using both effects
  }
}
```

## Implementation Details

### System Effect
- Reads from `System.getProperty()` and `System.getenv()`
- Provides type-safe parsing with error handling
- Returns `Option` for missing values or uses defaults

### Clock Effect  
- Uses `java.time.Clock.systemDefaultZone()` by default
- `now()` returns wall-clock time as `Instant`
- `nowMonotonic()` returns monotonic time as `Duration`
- Suitable for both timestamping and duration measurement

## Best Practices

- Use environment variables for deployment-specific configuration
- Use system properties for application-specific settings
- Handle missing required configuration with `Raise`
- Use monotonic time for measuring durations
- Use wall-clock time for logging and timestamping
- Combine with `Log` effect for configuration logging
