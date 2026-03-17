---
title: Building Applications
description: Build complete applications with λÆS using YaesApp and practical examples
sidebar:
  label: "8. Building Applications"
  order: 8
---

> **Step 8 of 8** — The capstone of the learning path. You'll learn how `YaesApp` ties everything together and see complete, real-world examples that combine multiple effects.

By now you've learned each effect individually. This step shows how to wire them into complete applications using the `YaesApp` entry point, and walks through several practical examples that demonstrate idiomatic λÆS patterns.

---

## YaesApp: Common Entry Point

`YaesApp` is an abstract trait that provides a unified entry point for λÆS applications. It is inspired by similar abstractions in other effect systems (like Kyo's `KyoApp`) and eliminates the boilerplate of manually stacking and running effects.

### Basic Example

```scala 3
import in.rcard.yaes.*

object MyApp extends YaesApp {
  override def run(using Sync, Output, Input, Random, Clock, System): Unit = {
    Output.printLn("Hello, λÆS!")

    val currentTime = Clock.now
    Output.printLn(s"Current time: $currentTime")

    val randomNumber = Random.nextInt
    Output.printLn(s"Random number: $randomNumber")
  }
}
```

Run it:

```bash
sbt "runMain in.rcard.yaes.MyApp"
```

`YaesApp`:
- Provides a `main` method entry point
- Automatically handles common λÆS effects in the correct order
- Supports a single `run` block that defines your application logic
- Includes built-in error handling with customization points
- Offers access to command-line arguments

---

## Available Effects Inside `run`

Within a `run` block, the following effects are automatically available:

### Sync Effect

The `Sync` effect is available inside `run`, allowing you to explicitly track side-effecting computations:

```scala 3
override def run(using Sync, Output, Input, Random, Clock, System): Unit = {
  val user = Sync(findUserById(1))  // Track side-effecting calls
  Output.printLn(s"Found user: $user")
}
```

Wrapping external side-effecting code with `Sync(...)` makes effectful boundaries explicit at the type level. While exceptions thrown inside `run` are already caught by the outer `Sync.runBlocking`, having `Sync` in scope lets you be intentional about where side effects occur.

### Output Effect

```scala 3
override def run(using Sync, Output, Input, Random, Clock, System): Unit = {
  Output.print("Hello ")
  Output.printLn("World!")
}
```

### Random Effect

```scala 3
override def run {
  val randomInt = Random.nextInt
  val randomBool = Random.nextBoolean
  val randomDouble = Random.nextDouble
  Output.printLn(s"Random int: $randomInt")
}
```

### Clock Effect

```scala 3
override def run {
  val now = Clock.now              // Current instant
  val monotonic = Clock.nowMonotonic // Monotonic duration
  Output.printLn(s"Current time: $now")
}
```

### System Effect

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

---

## Exception Handling

`YaesApp` automatically catches all exceptions thrown during execution via the `Sync` effect. `Sync.runBlocking` returns a `Try[A]`, so any unhandled exceptions are captured and passed to the `handleError` method.

```scala 3
override def run {
  // Any exception thrown here will be caught by Sync
  if (someCondition) {
    throw new RuntimeException("Something went wrong")
  }

  Output.printLn("This won't execute if exception is thrown")
}
```

For **typed error handling** of domain-specific errors (not exceptions), use the `Raise` effect explicitly:

```scala 3
override def run {
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

> **Note**: Unlike some effect systems (like Kyo), `YaesApp` does not include `Raise[Throwable]` in the automatic effect stack because `Sync.runBlocking` already returns `Try[A]`. Use `Raise[E]` explicitly when you need typed error handling for domain-specific errors.

---

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

```bash
sbt "runMain in.rcard.yaes.ArgsApp arg1 arg2 arg3"
```

---

## Effect Handler Order

`YaesApp` applies effect handlers in the following order (outermost to innermost):

| Order | Effect   | Purpose                                            |
|-------|----------|----------------------------------------------------|
| 1     | Sync     | Side effects, async, catches all exceptions        |
| 2     | Output   | Console output                                     |
| 3     | Input    | Console input                                      |
| 4     | Random   | Random generation                                  |
| 5     | Clock    | Time operations                                    |
| 6     | System   | System properties / environment variables          |

All six effects are available as context parameters inside the `run` block, including `Sync`. The `Sync` handler (`Sync.runBlocking`) wraps the entire execution, providing the `Sync` context to the inner effects.

Logging is intentionally excluded so applications can choose their own backend (`Log.run` or `Slf4jLog.run`).

---

## Customization

### Custom Error Handling

Override `handleError` to customize what happens when an exception escapes:

```scala 3
object CustomErrorApp extends YaesApp {
  override protected def handleError(error: Throwable): Unit = {
    Output.run {
      Output.printLn(s"Custom error: ${error.getMessage}")
    }
    // Custom logic here
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

Override `executionContext` for a custom thread pool:

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

---

## YaesApp vs Manual Wiring

Without `YaesApp`, you manually stack all effect handlers:

```scala 3
object ManualApp {
  def main(args: Array[String]): Unit = {
    val result = Sync.runBlocking(Duration.Inf) {
      Output.run {
        Random.run {
          Clock.run {
            System.run {
              Output.printLn("Hello")
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

With `YaesApp`, the same is:

```scala 3
object SimpleApp extends YaesApp {
  override def run {
    Output.printLn("Hello")
  }
}
```

---

## Practical Examples

The following examples combine multiple effects to show how λÆS handles realistic scenarios.

### Coin Flip Game

Combines `Random`, `Output`, `Input`, and `Raise`:

```scala 3
import in.rcard.yaes.Random.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Input.*
import in.rcard.yaes.Raise.*
import java.io.IOException

def coinFlipGame(using Random, Output, Input, Raise[IOException]): String = {
  Output.printLn("Welcome to the Coin Flip Game!")
  Output.printLn("Guess: heads or tails?")

  val guess = Input.readLn()
  val flip = if (Random.nextBoolean) "heads" else "tails"

  Output.printLn(s"The coin landed on: $flip")

  if (guess.toLowerCase == flip) {
    Output.printLn("You won!")
    "win"
  } else {
    Output.printLn("You lost!")
    "lose"
  }
}

// Run the game
val result: Either[IOException, String] = Raise.either {
  Output.run {
    Input.run {
      Random.run {
        coinFlipGame
      }
    }
  }
}
```

### File Processing with Resource Management

Combines `Resource` and `IO` with safe acquire/release:

```scala 3
import in.rcard.yaes.Resource.*
import in.rcard.yaes.IO.*
import java.io.{FileInputStream, FileOutputStream}

def processFiles(inputPath: String, outputPath: String)(using Resource, IO): Unit = {
  val input = Resource.acquire(new FileInputStream(inputPath))
  val output = Resource.acquire(new FileOutputStream(outputPath))

  Resource.ensuring {
    println("File processing completed")
  }

  val buffer = new Array[Byte](1024)
  var bytesRead = input.read(buffer)
  while (bytesRead != -1) {
    output.write(buffer, 0, bytesRead)
    bytesRead = input.read(buffer)
  }
}

// Resources are released automatically even if an exception is thrown
Resource.run {
  IO.run {
    processFiles("input.txt", "output.txt")
  }
}
```

### Concurrent Web Scraping

Uses `Async` to fetch multiple URLs in parallel:

```scala 3
import in.rcard.yaes.Async.*
import in.rcard.yaes.IO.*
import in.rcard.yaes.Log.*
import in.rcard.yaes.Log.given

def fetchUrl(url: String)(using IO, Log): String = {
  val logger = Log.getLogger("WebScraper")
  logger.info(s"Fetching: $url")

  Thread.sleep(1000) // Simulate HTTP request
  s"Content from $url"
}

def scrapeUrls(urls: List[String])(using Async, IO, Log): List[String] = {
  val fibers = urls.map { url =>
    Async.fork(s"fetch-$url") {
      fetchUrl(url)
    }
  }

  fibers.map(_.join())
}

val results = Log.run() {
  IO.run {
    Async.run {
      scrapeUrls(List(
        "https://example.com",
        "https://scala-lang.org",
        "https://github.com"
      ))
    }
  }
}
```

### Configuration Loading

Combines `System`, `Raise`, and `Log` for safe configuration parsing:

```scala 3
import in.rcard.yaes.System.*
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Log.*
import in.rcard.yaes.Log.given

case class AppConfig(
  host: String,
  port: Int,
  dbUrl: String,
  logLevel: String
)

def loadConfig(using System, Raise[String], Log): AppConfig = {
  val logger = Log.getLogger("Config")
  logger.info("Loading application configuration")

  val host = System.env[String]("HOST", "localhost")
  val port = System.env[Int]("PORT").getOrElse {
    Raise.raise("PORT environment variable is required")
  }
  val dbUrl = System.property[String]("db.url").getOrElse {
    Raise.raise("db.url system property is required")
  }
  val logLevel = System.env[String]("LOG_LEVEL", "INFO")

  AppConfig(host, port, dbUrl, logLevel)
}

val config = Raise.either {
  Log.run() {
    System.run {
      loadConfig
    }
  }
}
```

### Error Handling Pipeline

Typed validation pipeline using `Raise`:

```scala 3
import in.rcard.yaes.Raise.*
import in.rcard.yaes.IO.*

sealed trait ValidationError
case object InvalidEmail extends ValidationError
case object InvalidAge extends ValidationError
case class DatabaseError(msg: String) extends ValidationError

case class User(email: String, age: Int)

def validateEmail(email: String)(using Raise[ValidationError]): String =
  if (email.contains("@")) email
  else Raise.raise(InvalidEmail)

def validateAge(age: Int)(using Raise[ValidationError]): Int =
  if (age >= 0 && age <= 120) age
  else Raise.raise(InvalidAge)

def saveUser(user: User)(using IO, Raise[ValidationError]): Long =
  if (user.email.endsWith("@spam.com"))
    Raise.raise(DatabaseError("Spam domain not allowed"))
  else
    42L // User ID

def createUser(email: String, age: Int)(using IO, Raise[ValidationError]): Long =
  saveUser(User(validateEmail(email), validateAge(age)))

val result = Raise.either {
  IO.run {
    createUser("john@example.com", 25)
  }
}

result match {
  case Right(userId)            => println(s"User created with ID: $userId")
  case Left(InvalidEmail)       => println("Invalid email format")
  case Left(InvalidAge)         => println("Invalid age")
  case Left(DatabaseError(msg)) => println(s"Database error: $msg")
}
```

### Sensor Data Processing with Flow

Demonstrates stream processing using `Flow` from the `yaes-data` module:

```scala 3
import in.rcard.yaes.Flow
import in.rcard.yaes.Random.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Log.*
import in.rcard.yaes.Log.given

case class SensorReading(id: Int, temperature: Double, humidity: Double)

def processSensorData(readings: List[SensorReading])(using Log, Output): List[String] = {
  val logger = Log.getLogger("SensorProcessor")
  val results = scala.collection.mutable.ArrayBuffer[String]()

  readings.asFlow()
    .onStart {
      logger.info("Starting sensor data processing")
      Output.printLn("Processing sensor readings...")
    }
    .filter(_.temperature > 25.0)
    .filter(_.humidity < 60.0)
    .map { reading =>
      s"Alert: Sensor ${reading.id} - Temp: ${reading.temperature}°C, Humidity: ${reading.humidity}%"
    }
    .onEach(Output.printLn)
    .take(5)
    .collect { alert => results += alert }

  logger.info(s"Generated ${results.length} alerts")
  results.toList
}

def generateSensorReadings(using Random): List[SensorReading] =
  (1 to 20).map { id =>
    SensorReading(
      id          = id,
      temperature = Random.nextDouble * 40.0,
      humidity    = Random.nextDouble * 100.0
    )
  }.toList

val alerts = Log.run() {
  Output.run {
    Random.run {
      val readings = generateSensorReadings
      processSensorData(readings)
    }
  }
}

println(s"Total alerts generated: ${alerts.length}")
```

---

## Best Practices

1. **Single Responsibility** — Keep your `run` block focused; delegate logic to pure functions
2. **Exception Handling** — Throw exceptions for unexpected errors; `Sync` (via `YaesApp`) catches them automatically
3. **Typed Error Handling** — Use `Raise[E]` for domain-specific errors (parsing, validation, business rules)
4. **Logging** — Use the `Log` effect with your preferred handler (`Log.run` or `Slf4jLog.run`) inside the `run` block
5. **Configuration** — Read from environment variables and system properties; wrap typed operations with `Raise.run`
6. **Testing** — Override `handleError` to prevent `sys.exit` during tests
7. **Timeout** — Set a sensible `runTimeout` for production applications
8. **Separation of Concerns** — Keep business logic in separate functions; use `run` for wiring only

---

## What's Next?

You've completed the λÆS learning path! Here are good places to go next:

- **[HTTP Module](/yaes/http/server/)** — Build HTTP servers and clients with λÆS
- **[Integrations](/yaes/integrations/cats-effect/)** — Use λÆS alongside Cats Effect or SLF4J
- **[Community](/yaes/community/contributing/)** — Contribute to the project
