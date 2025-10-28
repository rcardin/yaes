# YaesApp: Common Entry Point for YAES Applications

`YaesApp` provides a unified entry point for applications built with the YAES (Yet Another Effect System) library. It is inspired by similar abstractions in other effect systems like Kyo's `KyoApp`, providing a convenient way to define applications with automatic effect handling.

## Overview

`YaesApp` is an abstract trait that:
- Provides a `main` method entry point
- Automatically handles common YAES effects in the correct order
- Supports a single `run` block that defines your application logic
- Includes built-in error handling with customization points
- Offers access to command-line arguments

## Quick Start

### Basic Example

```scala 3
import in.rcard.yaes.*

object MyApp extends YaesApp {
  override def run {
    Output.printLn("Hello, YAES!")
    
    val currentTime = Clock.now
    Output.printLn(s"Current time: $currentTime")
    
    val randomNumber = Random.nextInt
    Output.printLn(s"Random number: $randomNumber")
    
    val logger = Log.getLogger("MyApp")
    logger.info("Application started successfully")
  }
}
```

Run it:
```bash
sbt "runMain in.rcard.yaes.MyApp"
```

## Available Effects

Within a `run` block, the following effects are automatically available:

### Output Effect
Console output operations.

```scala 3
override def run {
  Output.print("Hello ")
  Output.printLn("World!")
}
```

### Random Effect
Random number generation.

```scala 3
override def run {
  val randomInt = Random.nextInt
  val randomBool = Random.nextBoolean
  val randomDouble = Random.nextDouble
  Output.printLn(s"Random int: $randomInt")
}
```

### Clock Effect
Time and date operations.

```scala 3
override def run {
  val now = Clock.now              // Current instant
  val monotonic = Clock.nowMonotonic // Monotonic duration
  Output.printLn(s"Current time: $now")
}
```

### System Effect
System properties and environment variables.

```scala 3
override def run {
  // For operations that require typed error handling (like parsing),
  // wrap with Raise.run to handle parsing errors
  val port = Raise.run {
    System.property[Int]("server.port", 8080)
  }
  
  // For simple string access, no Raise needed
  val javaHome = System.env("JAVA_HOME")
  
  Output.printLn(s"Java Home: $javaHome")
  Output.printLn(s"Port: $port")
}
```

### Log Effect
Structured logging with different levels.

```scala 3
override def run {
  val logger = Log.getLogger("MyApp", Log.Level.Info)
  
  logger.trace("Trace message")
  logger.debug("Debug message")
  logger.info("Info message")
  logger.warn("Warning message")
  logger.error("Error message")
  logger.fatal("Fatal message")
}
```

## Exception Handling

YaesApp automatically catches all exceptions thrown during execution via the `IO` effect. The `IO.runBlocking` method returns a `Try[A]`, ensuring that any unhandled exceptions are captured and passed to the `handleError` method.

```scala 3
override def run {
  // Any exception thrown here will be caught by IO
  if (someCondition) {
    throw new RuntimeException("Something went wrong")
  }
  
  Output.printLn("This won't execute if exception is thrown")
}
```

For **typed error handling** of domain-specific errors (not exceptions), use the `Raise` effect explicitly:

```scala 3
override def run {
  // Typed error handling for parsing errors
  val result: Either[NumberFormatException, Option[Int]] = Raise.run {
    System.env[Int]("PORT")  // Requires Raise[NumberFormatException]
  }
  
  result match {
    case Right(Some(port)) => Output.printLn(s"Port: $port")
    case Right(None) => Output.printLn("PORT not set")
    case Left(error) => Output.printLn(s"Invalid PORT: ${error.getMessage}")
  }
}
```

## Command-Line Arguments

Access command-line arguments via the protected `args` field:

```scala 3
object ArgsApp extends YaesApp {
  override def run {
    Output.printLn(s"Received ${args.length} arguments")
    args.foreach(arg => Output.printLn(s"  - $arg"))
  }
}
```

Run with arguments:
```bash
sbt "runMain in.rcard.yaes.ArgsApp arg1 arg2 arg3"
```

## Effect Handler Order

YaesApp automatically applies effect handlers in the following order (from outermost to innermost):

1. **IO** - Handles side effects, async operations, and catches all exceptions
2. **Output** - Console output
3. **Random** - Random generation
4. **Clock** - Time operations
5. **System** - System properties/environment
6. **Log** - Structured logging

This ordering ensures that:
- All effects run within an IO context
- All exceptions are automatically caught by IO and handled via `handleError`
- Resources are managed correctly
- Effects can compose naturally

**Note**: Unlike some effect systems (like Kyo), YaesApp does not include `Raise[Throwable]` in the automatic effect stack because `IO.runBlocking` already returns `Try[A]`, providing built-in exception handling. Use `Raise[E]` explicitly when you need typed error handling for domain-specific errors.

## Customization

### Custom Error Handling

Override `handleError` to customize error handling:

```scala 3
object CustomErrorApp extends YaesApp {
  override protected def handleError(error: Throwable): Unit = {
    Output.run {
      Output.printLn(s"Custom error: ${error.getMessage}")
    }
    // Custom logic here - don't exit
  }
  
  
  override def run {
    throw new RuntimeException("Test error")
  }
}
```

### Custom Timeout

Override `runTimeout` to set a timeout for blocking operations:

```scala 3
import scala.concurrent.duration.*

object TimeoutApp extends YaesApp {
  override protected def runTimeout: Duration = 30.seconds

  override def run {
    Output.printLn("Will timeout after 30 seconds")
  }
}
```

### Custom Execution Context

Override `executionContext` for custom thread pool:

```scala 3
import scala.concurrent.ExecutionContext

object CustomThreadPoolApp extends YaesApp {
  override protected given executionContext: ExecutionContext = 
    ExecutionContext.fromExecutorService(
      java.util.concurrent.Executors.newFixedThreadPool(4)
    )
  
  override def run {
    Output.printLn("Using custom thread pool")
  }
}
```

### Custom Clock

Override `logClock` for custom timestamp generation:

```scala 3
import java.time.{Clock => JClock}

object CustomClockApp extends YaesApp {
  override protected given logClock: JClock = JClock.systemUTC()
  
  override def run {
    val logger = Log.getLogger("App")
    logger.info("Using UTC timestamps")
  }
}
```

### Custom Exit Behavior

Override `exit` to prevent actual system exit (useful for testing):

```scala 3
object NoExitApp extends YaesApp {
  override protected def exit(code: Int): Unit = {
    Output.run {
      Output.printLn(s"Would exit with code: $code")
    }
    // Don't actually exit
  }
  
  override def run {
    Output.printLn("This app won't actually exit")
  }
}
```

## Best Practices

1. **Single Responsibility**: Keep your `run` block focused and organized
2. **Exception Handling**: Throw exceptions for unexpected errors - the IO effect will catch them automatically
3. **Typed Error Handling**: Use `Raise[E]` explicitly when you need type-safe error handling for domain-specific errors (e.g., parsing, validation)
4. **Logging**: Use structured logging instead of `Output.printLn` for production code
5. **Configuration**: Read configuration from environment variables and system properties, wrapping typed operations with `Raise.run`
6. **Testing**: Override `exit` and error handling methods for testing
7. **Timeout**: Set appropriate timeouts for production applications
8. **Separation of Concerns**: Keep business logic in separate functions, use `run` block for wiring

## Comparison with Manual Effect Handling

### Without YaesApp

```scala 3
object ManualApp {
  def main(args: Array[String]): Unit = {
    val result = IO.runBlocking(Duration.Inf) {
      Output.run {
        Random.run {
          Clock.run {
            System.run {
              Log.run {
                // Application logic here
                Output.printLn("Hello")
              }
            }
          }
        }
      }
    }
    
    result match {
      case Success(_) => ()
      case Failure(ex) =>
        System.err.println(s"Error: ${ex.getMessage}")
        ex.printStackTrace()
        sys.exit(1)
    }
  }
}
```

### With YaesApp

```scala 3
object SimpleApp extends YaesApp {
  override def run {
    Output.printLn("Hello")
  }
}
```

Much cleaner and less error-prone!

## See Also

- [YAES Effects Documentation](effects/index.md)
- [IO Effect](effects/io.md)
- [Raise Effect](effects/raise.md)
- [Output Effect](effects/io-effects.md)
- [Log Effect](effects/log.md)
