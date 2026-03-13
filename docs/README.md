# Yet Another Effect System (λÆS)

![Made for Scala 3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/rcardin/yaes/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/in.rcard.yaes/yaes-core_3)
![GitHub release](https://img.shields.io/github/v/release/rcardin/yaes)
[![javadoc](https://javadoc.io/badge2/in.rcard.yaes/yaes-core_3/javadoc.svg)](https://javadoc.io/doc/in.rcard.yaes/yaes-core_3)

λÆS is an experimental effect system in Scala inspired by the ideas behind Algebraic Effects. Using Scala 3 [context parameters](https://docs.scala-lang.org/scala3/reference/contextual/using-clauses.html) and [context functions](https://docs.scala-lang.org/scala3/reference/contextual/context-functions.html), it provides a way to define and handle effects in a modular and composable manner.

## Featured Talk

Watch the talk from **Scalar 2025** about the main concepts behind the library:

[![Watch the video](https://img.youtube.com/vi/TXUxCsPpZp0/maxresdefault.jpg)](https://youtu.be/TXUxCsPpZp0)

## Modules at a Glance

| Module | Description |
|--------|-------------|
| [**yaes-core**](getting-started.md) | Effects for IO, concurrency, error handling, state, logging, and more |
| [**yaes-data**](data-structures.md) | Functional data structures like Flow for async streams |
| [**yaes-cats**](cats-effect.md) | Cats Effect 3 integration for bidirectional interop |
| [**yaes-slf4j**](effects/log.md) | SLF4J logging backend for the Log effect |
| [**yaes-http-server**](http/server.md) | HTTP server built on λÆS effects and virtual threads |
| [**yaes-http-client**](http/client.md) | HTTP client built on λÆS effects and Java's HttpClient |
| [**yaes-http-circe**](http/circe.md) | Circe JSON integration for HTTP server and client |

## Get Started

λÆS requires **Java 25+** for Virtual Threads and Structured Concurrency. Head over to the [Getting Started](getting-started.md) guide for installation instructions, requirements, and your first program.

## Core Concepts

In λÆS, types like `Random` and `Raise` are **Effects**:

- A **Side Effect** is an unpredictable interaction, usually with an external system
- An **Effect System** manages Side Effects by tracking and wrapping them into Effects
- An **Effect** describes the type of the Side Effect and the return type of an effectful computation

λÆS uses **deferred execution** - calling effectful functions returns a value that represents something that can be run but hasn't yet.

## Quick Example

```scala
import in.rcard.yaes.Random.*
import in.rcard.yaes.Raise.*

def drunkFlip(using Random, Raise[String]): String = {
  val caught = Random.nextBoolean
  if (caught) {
    val heads = Random.nextBoolean
    if (heads) "Heads" else "Tails"
  } else {
    Raise.raise("We dropped the coin")
  }
}
```

## Contributing

Contributions are welcome! Please check our [contributing guidelines](contributing.md) to get started.

## Acknowledgments

Special thanks to all the smart engineers who helped with ideas and suggestions, including Daniel Ciocirlan, Simon Vergauwen, Jon Pretty, Noel Welsh, and Flavio Brasil.
