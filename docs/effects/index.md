---
layout: default
title: "Available Effects"
---

# Available Effects

λÆS provides a comprehensive set of effects for different use cases:

## Core Effects

### [IO Effect](io.html)
Manages side-effecting operations with referential transparency.

### [Async Effect](async.html) 
Enables asynchronous computations and structured concurrency with fibers.

### [Raise Effect](raise.html)
Provides typed error handling and propagation.

### [State Effect](state.html)
Enables stateful computations and mutable state management in a functional way.

## Resource Management

### [Resource Effect](resource.html)
Automatic resource management with guaranteed cleanup in LIFO order.

## I/O Operations

### [Input & Output Effects](io-effects.html)
Console input and output operations with error handling.

## Utility Effects

### [Random Effect](random.html)
Random content generation for testing and games.

### [System & Clock Effects](system-clock.html)
System properties, environment variables, and time management.

### [Log Effect](log.html)
Structured logging at different levels with filtering.

## Effect Composition

Effects can be composed freely. You can handle effects one at a time or combine multiple effects in a single computation:

```scala
import in.rcard.yaes.Random.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Raise.*

def gameRound(using Random, Output, Raise[String]): Int = {
  val dice1 = Random.nextInt(6) + 1
  val dice2 = Random.nextInt(6) + 1
  val total = dice1 + dice2
  
  Output.printLn(s"Rolled: $dice1 + $dice2 = $total")
  
  if (total == 7) Raise.raise("Lucky seven!")
  total
}
```
