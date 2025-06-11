---
layout: default
title: "Getting Started"
---

# Getting Started with YÆS

## Installation

The library is available on Maven Central. Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-core" % "0.2.0"
```

The library is only available for **Scala 3** and is currently in an experimental stage. The API is subject to change.

## Your First Effect

Let's start with a simple example using the `Random` and `Raise` effects:

```scala
import in.rcard.yaes.Random.*
import in.rcard.yaes.Raise.*

def flipCoin(using Random, Raise[String]): String = {
  val result = Random.nextBoolean
  if (result) "Heads" else "Tails"
}
```

## Running Effects

To execute the effectful computation, use handlers:

```scala
import in.rcard.yaes.Random.*
import in.rcard.yaes.Raise.*

val result: String = Raise.run { 
  Random.run { 
    flipCoin
  }
}
```

## Key Concepts

### Effects vs Side Effects

- **Side Effect**: An unpredictable interaction, usually with an external system
- **Effect**: A managed side effect wrapped in the YÆS system

### Deferred Execution

Calling effectful functions doesn't immediately execute them. Instead, they return a value representing a computation that can be run later.

### Handlers

Handlers are the tools that execute deferred effects. They can be composed and applied selectively.

## Next Steps

- Explore the [available effects](effects/)
- Check out [practical examples](examples.html)
- Read about [contributing](contributing.html)
