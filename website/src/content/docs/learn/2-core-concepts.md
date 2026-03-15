---
title: Core Concepts
description: Understand the mental model behind λÆS — what effects are, how deferred execution works, and how handlers compose.
sidebar:
  label: "2. Core Concepts"
  order: 2
---

# Core Concepts

This page explains the ideas that everything in λÆS is built on. Understanding these concepts will make the rest of the documentation click into place.

## What is an Effect?

In λÆS, an **Effect** is a managed side effect. To understand what that means, start from the more familiar idea:

- A **Side Effect** is an unpredictable interaction, usually with an external system — reading from a file, generating a random number, or throwing an exception.
- An **Effect System** manages side effects by tracking and wrapping them. Instead of letting side effects happen implicitly, the system makes them explicit in function signatures.
- An **Effect** describes _what kind_ of side effect a function needs, and _what type_ of value it produces.

In λÆS, types like `Random`, `Raise[E]`, `Async`, and `State[S]` are effect types. When a function declares `using Random`, it is saying: "I need access to randomness, and that requirement must be satisfied by a caller."

```scala
import in.rcard.yaes.Random.*

// This function requires the Random effect
def rollDie(using Random): Int =
  Random.nextInt(6) + 1
```

The key insight: effects appear in the **type signature**. There are no hidden surprises — you can read a function's requirements directly from its parameters.

## Side Effects vs Effects

| Side Effect (bad) | Effect (good) |
|---|---|
| Happens implicitly | Declared in function signature |
| Cannot be composed | Composes with other effects |
| Hard to test | Can be swapped for a test handler |
| Untracked | Tracked by the type system |

λÆS does not eliminate side effects — it _manages_ them. Under the hood, a handler runs the real operation. But the function itself is pure from the caller's perspective.

## Deferred Execution

Calling an effectful function does **not** immediately execute it. Instead, it returns a value representing a computation that can be run later. Execution only happens when a handler is applied.

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

// Nothing runs until here:
val result: Either[String, String] = Raise.run {
  Random.run {
    drunkFlip
  }
}
```

Deferred execution means:
- **Composability**: Multiple effects can be combined before any runs.
- **Testability**: You can substitute a test handler without changing the function.
- **Safety**: The type system ensures every effect is handled before the program runs.

## Handlers

A **handler** is the mechanism that executes an effect. Every effect type in λÆS has a corresponding handler. Handlers:

1. Provide the capability the effect requires (e.g., a random number generator)
2. Interpret the effect's operations (e.g., translate `Raise.raise` into `Left(...)`)
3. Return a result

Handlers are applied by calling `EffectType.run { ... }`:

```scala
import in.rcard.yaes.Output.*

val result: Unit = Output.run {
  Output.printLn("Hello, λÆS!")
}
```

Handlers can be **composed** — you wrap one inside another. The innermost handler runs first:

```scala
import in.rcard.yaes.Random.*
import in.rcard.yaes.Output.*

val result: Unit = Output.run {
  Random.run {
    val n = Random.nextInt(10)
    Output.printLn(s"Random number: $n")
  }
}
```

The order of handler application can matter for effects that interact (e.g., `Raise` inside `Async`).

## Effect Composition

Effects are declared as multiple `using` parameters and composed naturally:

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

You can handle effects one at a time or all at once. Effects that are not yet handled are propagated to callers — the type system tracks them.

## Available Effects

λÆS provides a comprehensive set of effects:

**Core Effects**
- `Sync` — wraps arbitrary side-effecting code
- `Async` — structured concurrency and fibers
- `Raise[E]` — typed error handling
- `State[S]` — stateful computations

**Resource Management**
- `Resource` — acquire/release with guaranteed cleanup
- `Shutdown` — graceful termination with hooks

**I/O and Utilities**
- `Input` / `Output` — console I/O
- `Random` — random number generation
- `SystemClock` / `SystemEnv` / `SystemProperties` — clock and environment access
- `Log` — structured logging (via SLF4J backend)

**Retry**
- `Retry` — retry failing blocks with configurable schedules

Continue to [Basic Effects](/yaes/learn/3-basic-effects/) to see these in action.
