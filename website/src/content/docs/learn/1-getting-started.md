---
title: Getting Started
description: Install λÆS, understand its requirements, and run your first effect.
sidebar:
  label: "1. Getting Started"
  order: 1
---

# Getting Started with λÆS

## Requirements

- **Java 25 or higher** is required to run λÆS due to its use of modern Java features like Virtual Threads and Structured Concurrency.
- **Scala 3** is required, as λÆS leverages Scala 3's context functions and other advanced features.

## Installation

The library is available on Maven Central. Add the following dependencies to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "in.rcard.yaes" %% "yaes-core"        % "0.15.0",
  "in.rcard.yaes" %% "yaes-data"        % "0.15.0", // Optional: Flow and data structures
  "in.rcard.yaes" %% "yaes-cats"        % "0.15.0", // Optional: Cats Effect integration
  "in.rcard.yaes" %% "yaes-slf4j"       % "0.15.0", // Optional: SLF4J logging backend
  "in.rcard.yaes" %% "yaes-http-server" % "0.15.0", // Optional: HTTP server
  "in.rcard.yaes" %% "yaes-http-circe"  % "0.15.0"  // Optional: Circe JSON for HTTP
)
```

The library is only available for **Scala 3** and is currently in an experimental stage. The API is subject to change.

### Modules

- **yaes-core**: Essential effects for functional programming (Sync, Async, Raise, etc.)
- **yaes-data**: Functional data structures that work with effects (Flow, etc.)
- **yaes-cats**: Cats Effect 3 integration for bidirectional interop
- **yaes-slf4j**: SLF4J logging backend for the Log effect
- **yaes-http-server**: HTTP server built on λÆS effects and virtual threads
- **yaes-http-circe**: Circe JSON integration for the HTTP server

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

## Next Steps

- Read [Core Concepts](/yaes/learn/2-core-concepts/) to understand the mental model behind λÆS
- Explore [Basic Effects](/yaes/learn/3-basic-effects/) to learn about Sync, Random, I/O, and Clock effects
- Jump to [Error Handling](/yaes/learn/4-error-handling/) once you are comfortable with the basics
