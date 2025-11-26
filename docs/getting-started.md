---
layout: default
title: "Getting Started"
---

# Getting Started with λÆS

## Requirements

- **Java 24 or higher** is required to run λÆS due to its use of modern Java features like Virtual Threads and Structured Concurrency.
- **Scala 3** is required, as λÆS leverages Scala 3's context functions and other advanced features.

## Installation

The library is available on Maven Central. Add the following dependencies to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "in.rcard.yaes" %% "yaes-core" % "0.9.0",
  "in.rcard.yaes" %% "yaes-data" % "0.9.0"  // Optional: for Flow and data structures
)
```

The library is only available for **Scala 3** and is currently in an experimental stage. The API is subject to change.

### Modules

- **yaes-core**: Essential effects for functional programming (IO, Async, Raise, etc.)
- **yaes-data**: Functional data structures that work with effects (Flow, etc.)

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
- **Effect**: A managed side effect wrapped in the λÆS system

### Deferred Execution

Calling effectful functions doesn't immediately execute them. Instead, they return a value representing a computation that can be run later.

### Handlers

Handlers are the tools that execute deferred effects. They can be composed and applied selectively.

## Next Steps

- Explore the [available effects](effects/)
- Learn about [communication primitives](communication-primitives.html) for fiber coordination
- Learn about [functional data structures](data-structures.html)
- Check out [practical examples](examples.html)
- Read about [contributing](contributing.html)
