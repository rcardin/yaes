---
title: Basic Effects
description: Learn the foundational effects in λÆS — Sync, Random, I/O (Input/Output), and Clock/System effects for side-effecting operations.
sidebar:
  label: "3. Basic Effects"
  order: 3
---

This step introduces the foundational effects you'll use in almost every λÆS program: **Sync** for exception-safe computations, **Random** for deterministic randomness, **Input/Output** for console I/O, and **Clock/System** for time and configuration access.

Each effect makes a specific kind of side effect explicit in your function signatures — enabling composition, testability, and safe execution.

---

## Sync Effect

The `Sync` effect allows for running side-effecting operations while maintaining referential transparency. It provides a guard rail to uncontrolled exceptions by lifting functions into the world of effectful computations.

### Basic Usage

```scala
import in.rcard.yaes.Sync.*

case class User(name: String)

def saveUser(user: User)(using Sync): Long =
  throw new RuntimeException("Read timed out")
```

### Handlers

**Non-blocking Handler** — returns a `Future` without blocking the current thread:

```scala
import in.rcard.yaes.Sync.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val result: Future[Long] = Sync.run {
  saveUser(User("John"))
}
```

**Blocking Handler** — blocks the current thread until completion:

```scala
import in.rcard.yaes.Sync.*
import scala.concurrent.ExecutionContext.Implicits.global

val result: Long = Sync.blockingRun {
  saveUser(User("John"))
}
```

### Implementation Details

- Uses Java Virtual Threads for execution
- Each effectful computation runs in a new virtual thread
- Handlers break referential transparency and should be used only at application edges

### Best Practices

- Use `Sync` for any operation that might throw exceptions
- Combine with other effects like `Raise` for better error handling
- Keep handlers at the application boundary

---

## Random Effect

The `Random` effect provides a set of operations to generate random content in a functional and testable way. It wraps random number generation, making it explicit in your function signatures and enabling deterministic testing.

### Basic Usage

```scala
import in.rcard.yaes.Random.*

def flipCoin(using Random): Boolean = Random.nextBoolean

def rollDice(using Random): Int = Random.nextInt(6) + 1

def randomInRange(min: Int, max: Int)(using Random): Int =
  Random.nextInt(max - min + 1) + min
```

### Available Operations

```scala
import in.rcard.yaes.Random.*

val randomBoolean: Random ?=> Boolean = Random.nextBoolean
val randomInt: Random ?=> Int = Random.nextInt
val randomIntInRange: Random ?=> Int = Random.nextInt(100) // 0 to 99
val randomLong: Random ?=> Long = Random.nextLong
val randomDouble: Random ?=> Double = Random.nextDouble // 0.0 to 1.0
```

### Running Random Effects

Use the `Random.run` handler:

```scala
import in.rcard.yaes.Random.*

val result: Boolean = Random.run {
  flipCoin
}
```

### Practical Examples

**Game Mechanics:**

```scala
import in.rcard.yaes.Random.*
import in.rcard.yaes.Output.*

def playGame(using Random, Output): String = {
  val playerRoll = Random.nextInt(6) + 1
  val computerRoll = Random.nextInt(6) + 1

  Output.printLn(s"Player rolled: $playerRoll")
  Output.printLn(s"Computer rolled: $computerRoll")

  if (playerRoll > computerRoll) "Player wins!"
  else if (computerRoll > playerRoll) "Computer wins!"
  else "It's a tie!"
}
```

**Random Data Generation:**

```scala
import in.rcard.yaes.Random.*

case class TestUser(id: Int, name: String, age: Int)

def generateTestUser(using Random): TestUser = {
  val id = Random.nextInt(10000)
  val names = List("Alice", "Bob", "Charlie", "Diana", "Eve")
  val name = names(Random.nextInt(names.length))
  val age = Random.nextInt(50) + 18 // 18 to 67

  TestUser(id, name, age)
}

def generateTestData(count: Int)(using Random): List[TestUser] =
  List.fill(count)(generateTestUser)
```

**Probabilistic Algorithms:**

```scala
import in.rcard.yaes.Random.*

def monteCarloEstimatePi(samples: Int)(using Random): Double = {
  val pointsInCircle = (1 to samples).count { _ =>
    val x = Random.nextDouble * 2 - 1 // -1 to 1
    val y = Random.nextDouble * 2 - 1 // -1 to 1
    x * x + y * y <= 1
  }

  4.0 * pointsInCircle / samples
}
```

### Combining with Other Effects

```scala
import in.rcard.yaes.Random.*
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Output.*

def riskyGame(using Random, Raise[String], Output): Int = {
  val luck = Random.nextDouble
  Output.printLn(s"Luck factor: $luck")

  if (luck < 0.1) {
    Raise.raise("Critical failure!")
  }

  (luck * 100).toInt
}

val result = Raise.either {
  Output.run {
    Random.run {
      riskyGame
    }
  }
}
```

### Best Practices

- Use `Random` effect instead of directly calling `scala.util.Random`
- Combine with other effects for comprehensive application logic
- Test random behavior by checking properties rather than exact values
- Uses `scala.util.Random` under the hood; thread-safe with proper handlers

---

## Input & Output Effects

The `Input` and `Output` effects provide console I/O operations in a functional and testable manner.

### Output Effect

The `Output` effect handles writing to the console.

```scala
import in.rcard.yaes.Output.*

val program: Output ?=> Unit = {
  Output.printLn("Hello, world!")
  Output.printLn("How are you today?")
}

// Write to standard error
val errorProgram: Output ?=> Unit = {
  Output.printErr("Error: Something went wrong!")
}

// Run the effect
Output.run {
  Output.printLn("This will be printed to console")
}
```

### Input Effect

The `Input` effect handles reading from the console. Reading can throw `IOException`, so it requires a `Raise[IOException]` context.

```scala
import in.rcard.yaes.Input.*
import in.rcard.yaes.Raise.*
import java.io.IOException

val userInput: (Input, Raise[IOException]) ?=> String = Input.readLn()

val result: Either[IOException, String] = Raise.either {
  Input.run {
    Input.readLn()
  }
}
```

### Combining Input and Output

**Interactive Programs:**

```scala
import in.rcard.yaes.Input.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Raise.*
import java.io.IOException

def greetUser(using Input, Output, Raise[IOException]): Unit = {
  Output.printLn("What's your name?")
  val name = Input.readLn()
  Output.printLn(s"Hello, $name! Nice to meet you.")
}

val result: Either[IOException, Unit] = Raise.either {
  Output.run {
    Input.run {
      greetUser
    }
  }
}
```

**Form Input with Validation:**

```scala
import in.rcard.yaes.Input.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Raise.*
import java.io.IOException

case class UserInfo(name: String, email: String, age: Int)

def readUserInfo(using Input, Output, Raise[String | IOException]): UserInfo = {
  Output.printLn("Enter your name:")
  val name = Input.readLn()
  if (name.trim.isEmpty) Raise.raise("Name cannot be empty")

  Output.printLn("Enter your email:")
  val email = Input.readLn()
  if (!email.contains("@")) Raise.raise("Invalid email format")

  Output.printLn("Enter your age:")
  val ageStr = Input.readLn()
  val age = try {
    ageStr.toInt
  } catch {
    case _: NumberFormatException => Raise.raise("Age must be a number")
  }

  if (age < 0 || age > 120) Raise.raise("Age must be between 0 and 120")

  UserInfo(name.trim, email.trim, age)
}
```

**Console-based Guessing Game:**

```scala
import in.rcard.yaes.Input.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Random.*
import in.rcard.yaes.Raise.*
import java.io.IOException

def guessingGame(using Input, Output, Random, Raise[IOException]): Unit = {
  val secret = Random.nextInt(100) + 1
  var attempts = 0
  var won = false

  Output.printLn("I'm thinking of a number between 1 and 100!")

  while (!won && attempts < 7) {
    Output.printLn(s"Attempt ${attempts + 1}/7 - Enter your guess:")
    val guessStr = Input.readLn()

    try {
      val guess = guessStr.toInt
      attempts += 1

      if (guess == secret) {
        Output.printLn(s"Congratulations! You guessed it in $attempts attempts!")
        won = true
      } else if (guess < secret) {
        Output.printLn("Too low!")
      } else {
        Output.printLn("Too high!")
      }
    } catch {
      case _: NumberFormatException =>
        Output.printLn("Please enter a valid number!")
    }
  }

  if (!won) {
    Output.printLn(s"Sorry! The number was $secret. Better luck next time!")
  }
}

Raise.either {
  Random.run {
    Output.run {
      Input.run {
        guessingGame
      }
    }
  }
}
```

### Best Practices

- Always handle `IOException` when using `Input`
- Use `Output` for user feedback and debugging
- Combine with `Raise` for robust error handling
- Keep I/O operations at application boundaries

---

## System & Clock Effects

These effects provide access to system-level information and time management.

### System Effect

The `System` effect provides type-safe access to system properties and environment variables.

**Environment Variables:**

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

**System Properties:**

```scala
import in.rcard.yaes.System.*
import in.rcard.yaes.Raise.*

val serverPort: (System, Raise[NumberFormatException]) ?=> Option[Int] =
  System.property[Int]("server.port")

val serverHost: System ?=> String =
  System.property[String]("server.host", "localhost")
```

**Supported Types:** `String`, `Int`, `Long`, `Double`, `Boolean`, `Float`, `Short`, `Byte`, `Char`

**Configuration Example:**

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

### Clock Effect

The `Clock` effect provides time management operations.

```scala
import in.rcard.yaes.Clock.*
import java.time.{Instant, Duration}

// Wall-clock time
val currentTime: Clock ?=> Instant = Clock.now()

// Monotonic time for measuring durations
val monotonicTime: Clock ?=> Duration = Clock.nowMonotonic()
```

**Time Measurement:**

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

**Timestamped Logging:**

```scala
import in.rcard.yaes.Clock.*
import in.rcard.yaes.Output.*

def logWithTimestamp(message: String)(using Clock, Output): Unit = {
  val timestamp = Clock.now()
  Output.printLn(s"[$timestamp] $message")
}
```

### Combined Usage

**Application Bootstrap:**

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

### Running Effects

```scala
import in.rcard.yaes.System.*
import in.rcard.yaes.Clock.*

val systemResult = System.run { /* system operations */ }
val clockResult = Clock.run { /* time operations */ }

// Combined
val combinedResult = Clock.run {
  System.run {
    // operations using both effects
  }
}
```

### Best Practices

- Use environment variables for deployment-specific configuration
- Use system properties for application-specific settings
- Handle missing required configuration with `Raise`
- Use monotonic time for measuring durations; wall-clock time for logging
- Combine with `Log` effect for configuration logging
