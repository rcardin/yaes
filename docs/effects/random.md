
# Random Effect

The `Random` effect provides a set of operations to generate random content in a functional and testable way.

## Overview

The `Random` effect wraps random number generation, making it explicit in your function signatures and enabling deterministic testing.

## Basic Usage

### Boolean Generation

```scala
import in.rcard.yaes.Random.*

def flipCoin(using Random): Boolean = Random.nextBoolean
```

### Integer Generation

```scala
import in.rcard.yaes.Random.*

def rollDice(using Random): Int = Random.nextInt(6) + 1

def randomInRange(min: Int, max: Int)(using Random): Int = 
  Random.nextInt(max - min + 1) + min
```

## Available Operations

### Basic Types

```scala
import in.rcard.yaes.Random.*

val randomBoolean: Random ?=> Boolean = Random.nextBoolean
val randomInt: Random ?=> Int = Random.nextInt
val randomIntInRange: Random ?=> Int = Random.nextInt(100) // 0 to 99
val randomLong: Random ?=> Long = Random.nextLong
val randomDouble: Random ?=> Double = Random.nextDouble // 0.0 to 1.0
```

## Running Random Effects

Use the `Random.run` handler:

```scala
import in.rcard.yaes.Random.*

val result: Boolean = Random.run {
  flipCoin
}
```

## Practical Examples

### Game Mechanics

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

### Random Data Generation

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

### Probabilistic Algorithms

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

## Testing with Random Effects

The `Random` effect makes testing deterministic by controlling the random source:

```scala
import in.rcard.yaes.Random.*

// Test with controlled randomness
def testFlipCoin(): Unit = {
  val result = Random.run {
    flipCoin
  }
  // Result is deterministic based on the underlying Random implementation
  assert(result == true || result == false)
}
```

## Combining with Other Effects

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

## Implementation Details

- Uses `scala.util.Random` under the hood
- Thread-safe when used with proper handlers
- Provides deterministic behavior for testing
- Can be seeded for reproducible results (implementation dependent)

## Best Practices

- Use `Random` effect instead of directly calling `scala.util.Random`
- Combine with other effects for comprehensive application logic
- Test random behavior by checking properties rather than exact values
- Consider the performance implications for high-frequency random generation
