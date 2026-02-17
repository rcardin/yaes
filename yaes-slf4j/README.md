![Made for Scala 3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/rcardin/yaes/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/in.rcard.yaes/yaes-slf4j_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/rcardin/yaes)
[![javadoc](https://javadoc.io/badge2/in.rcard.yaes/yaes-slf4j_3/javadoc.svg)](https://javadoc.io/doc/in.rcard.yaes/yaes-slf4j_3)

# λÆS SLF4J

The `yaes-slf4j` module provides an SLF4J-backed handler for the `Log` effect. It allows you to swap the built-in `ConsoleLogger` for any SLF4J-compatible logging backend (Logback, Log4j2, slf4j-simple, etc.) without changing any application code.

## Requirements

- **Java 25 or higher** is required by λÆS and its effect runtime.
- An **SLF4J backend** on the classpath (e.g., Logback, Log4j2, or slf4j-simple).

## Quick Start

Replace `Log.run` with `Slf4jLog.run`. That's it — all existing `Log.getLogger`, `logger.info(...)`, etc. calls work unchanged:

```scala
import in.rcard.yaes.Log
import in.rcard.yaes.slf4j.Slf4jLog

// Before (built-in console logger)
Log.run(Log.Level.Info) {
  val logger = Log.getLogger("MyService")
  logger.info("Hello from console!")
}

// After (SLF4J backend)
Slf4jLog.run {
  val logger = Log.getLogger("MyService")
  logger.info("Hello from SLF4J!")
}
```

## How It Works

`Slf4jLog.run` provides a `Log` effect handler that delegates to SLF4J:

- `Log.getLogger(name)` returns a `Logger` backed by `org.slf4j.LoggerFactory.getLogger(name)`.
- Each log method (`trace`, `debug`, `info`, `warn`, `error`, `fatal`) checks the corresponding SLF4J guard (e.g., `isInfoEnabled`) before evaluating the by-name message, avoiding unnecessary string construction.
- **FATAL maps to ERROR** — SLF4J has no FATAL level, so `logger.fatal(msg)` delegates to `underlying.error(msg)`.

## Level Configuration

Unlike `Log.run(level)`, `Slf4jLog.run` does **not** accept a level parameter. Level filtering is controlled entirely by the SLF4J backend configuration:

- **Logback**: `logback.xml` or `logback-test.xml`
- **Log4j2**: `log4j2.xml` or `log4j2.properties`
- **slf4j-simple**: `simplelogger.properties`

### Example: slf4j-simple

```properties
# simplelogger.properties
org.slf4j.simpleLogger.defaultLogLevel=info
org.slf4j.simpleLogger.log.com.myapp.db=debug
org.slf4j.simpleLogger.log.com.myapp.http=warn
```

### Example: Logback

```xml
<!-- logback.xml -->
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="STDOUT" />
  </root>

  <logger name="com.myapp.db" level="DEBUG" />
</configuration>
```

## Lazy Message Evaluation

Messages passed as by-name parameters are **not** evaluated when the level is disabled by the SLF4J backend:

```scala
Slf4jLog.run {
  val logger = Log.getLogger("MyService")
  // If DEBUG is disabled, the string interpolation never executes
  logger.debug(s"Expensive computation: ${heavyOperation()}")
}
```

## Multiple Loggers

You can create multiple loggers with different names within the same `Slf4jLog.run` block. Each logger independently respects its SLF4J-configured level:

```scala
Slf4jLog.run {
  val dbLogger  = Log.getLogger("com.myapp.db")
  val apiLogger = Log.getLogger("com.myapp.api")

  dbLogger.debug("Executing query")   // Shown if com.myapp.db >= DEBUG
  apiLogger.info("Request received")  // Shown if com.myapp.api >= INFO
}
```

## Combining with Other Effects

`Slf4jLog.run` composes with other λÆS effects just like `Log.run`:

```scala
import in.rcard.yaes.{Log, Output, Clock}
import in.rcard.yaes.slf4j.Slf4jLog

val result = Output.run {
  Clock.run {
    Slf4jLog.run {
      val logger = Log.getLogger("MyApp")
      logger.info("Application started")

      val now = Clock.now()
      Output.printLn(s"Current time: $now")
    }
  }
}
```

## Dependency

To use the `yaes-slf4j` module, add the following dependency to your `build.sbt` file:

```sbt
libraryDependencies += "in.rcard.yaes" %% "yaes-slf4j" % "0.14.0"
```

You also need an SLF4J backend on the classpath. For example, to use Logback:

```sbt
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.18"
```

Or for slf4j-simple (useful for testing):

```sbt
libraryDependencies += "org.slf4j" % "slf4j-simple" % "2.0.17"
```

The library is only available for Scala 3 and is currently in an experimental stage. The API is subject to change.

## Contributing

Contributions to the λÆS project are welcome! Please feel free to submit pull requests or open issues if you find bugs or have feature requests.
