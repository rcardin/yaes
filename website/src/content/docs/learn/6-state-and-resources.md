---
title: State, Writer, Reader & Resources
description: Learn stateful computations with State, pure value accumulation with Writer, read-only environment access with Reader, and safe resource lifecycle management with Resource.
sidebar:
  label: "6. State, Writer, Reader & Resources"
  order: 6
---

λÆS provides four complementary effects for managing mutable data, environment access, and external resources: the `State` effect for functional stateful computations, the `Writer` effect for pure append-only value accumulation, the `Reader` effect for read-only access to environment values, and the `Resource` effect for guaranteed cleanup of acquired resources.

---

## State Effect

The `State[S]` effect enables stateful computations in a purely functional manner. It provides operations to get, set, update, and use state values within a controlled scope, allowing you to work with mutable state without compromising functional programming principles.

### Overview

The `State` effect manages a single piece of state of type `S` throughout a computation. All state operations are performed within the context of a `State.run` block, which provides isolation and ensures the state is properly managed.

> **Note:** The State effect is not thread-safe. Use appropriate synchronization mechanisms when accessing state from multiple threads.

### Getting and Setting State

```scala
import in.rcard.yaes.State.*

val (finalState, result) = State.run(0) {
  val current = State.get[Int]
  State.set(current + 5)
  State.get[Int]
}
// finalState = 5, result = 5
```

### Updating State

The `update` operation applies a transformation function to the current state and returns the new value:

```scala
import in.rcard.yaes.State.*

val (finalState, result) = State.run(10) {
  val doubled = State.update[Int](_ * 2)
  val tripled = State.update[Int](_ * 3)
  tripled
}
// finalState = 60, result = 60
```

### Reading Without Modification

Use `State.use` when you only need to derive a value from state without changing it:

```scala
import in.rcard.yaes.State.*

case class User(name: String, age: Int)

val (finalState, nameLength) = State.run(User("Alice", 30)) {
  State.use[User, Int](_.name.length)
}
// finalState = User("Alice", 30), nameLength = 5
```

### Advanced: Counter with Operations

You can build composable state operations using context parameters:

```scala
import in.rcard.yaes.State.*

def increment(using State[Int]): Int = State.update(_ + 1)
def decrement(using State[Int]): Int = State.update(_ - 1)
def multiply(factor: Int)(using State[Int]): Int = State.update(_ * factor)

val (finalState, result) = State.run(5) {
  increment
  increment
  multiply(3)
  decrement
}
// finalState = 20, result = 20
```

### Advanced: Complex State Types

State works well with case classes for modeling domain objects:

```scala
import in.rcard.yaes.State.*

case class GameState(score: Int, lives: Int, level: Int)

def addScore(points: Int)(using State[GameState]): GameState =
  State.update(state => state.copy(score = state.score + points))

def loseLife(using State[GameState]): GameState =
  State.update(state => state.copy(lives = state.lives - 1))

def nextLevel(using State[GameState]): GameState =
  State.update(state => state.copy(level = state.level + 1))

val (finalState, _) = State.run(GameState(0, 3, 1)) {
  addScore(100)
  addScore(250)
  loseLife
  nextLevel
  State.get[GameState]
}
// finalState = GameState(350, 2, 2)
```

### Combining State with Other Effects

`State` composes naturally with other λÆS effects:

```scala
import in.rcard.yaes.State.*
import in.rcard.yaes.Random.*

def randomWalk(steps: Int)(using State[Int], Random): Int = {
  if (steps <= 0) State.get[Int]
  else {
    val direction = if (Random.nextBoolean) 1 else -1
    State.update(_ + direction)
    randomWalk(steps - 1)
  }
}

val (finalPosition, result) = State.run(0) {
  Random.run {
    randomWalk(10)
  }
}
```

### State API Reference

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `State.get` | `State[S] ?=> S` | Retrieve current state value |
| `State.set` | `(S) => State[S] ?=> S` | Set new value, return previous |
| `State.update` | `(S => S) => State[S] ?=> S` | Transform state, return new value |
| `State.use` | `(S => A) => State[S] ?=> A` | Read-only projection, state unchanged |
| `State.run` | `(S) => (State[S] ?=> A) => (S, A)` | Run computation with initial state |

### Thread Safety

The State effect is not thread-safe. For concurrent scenarios:

- Use separate `State.run` blocks per thread — each gets isolated state
- Implement synchronization or use atomic types if shared state is required

```scala
// Safe: each thread has its own isolated state
val thread1Result = Future {
  State.run(0) { /* stateful computation */ }
}

val thread2Result = Future {
  State.run(0) { /* stateful computation */ }
}
```

---

## Writer Effect

The `Writer[W]` effect enables pure, append-only value accumulation during computations. Values are collected into a `Vector[W]` and returned alongside the computation result as a tuple `(Vector[W], A)`. This is useful for collecting logs, events, metrics, or any other data during a computation.

> **Note:** The Writer effect is not thread-safe. Use appropriate synchronization mechanisms when accessing from multiple threads.

### Writing Values

Use `Writer.write` to append a single value and `Writer.writeAll` to append multiple values at once:

```scala
import in.rcard.yaes.Writer.*

val (log, result) = Writer.run[String, Int] {
  Writer.write("starting")
  Writer.write("computing")
  42
}
// log = Vector("starting", "computing"), result = 42
```

```scala
import in.rcard.yaes.Writer.*

val (log, _) = Writer.run[Int, Unit] {
  Writer.write(1)
  Writer.writeAll(List(2, 3, 4))
  Writer.write(5)
}
// log = Vector(1, 2, 3, 4, 5)
```

### The `writes` Infix Type

For more concise syntax, you can use the `writes` infix type, similar to `raises` for the Raise effect:

```scala
import in.rcard.yaes.{Writer, writes}

def computation: Int writes String = {
  Writer.write("log entry")
  42
}

val (log, result) = Writer.run[String, Int] {
  computation
}
// log = Vector("log entry"), result = 42
```

### Capturing Writes

The `capture` operation records writes from a block, returning them alongside the block's result. Writes are also forwarded to the outer scope:

```scala
import in.rcard.yaes.Writer.*

val (outerLog, (innerLog, result)) = Writer.run[String, (Vector[String], Int)] {
  Writer.write("before")
  val captured = Writer.capture[String, Int] {
    Writer.write("inside")
    99
  }
  Writer.write("after")
  captured
}
// outerLog = Vector("before", "inside", "after")
// innerLog = Vector("inside"), result = 99
```

Captures can be nested — each level records its own writes while forwarding to the outer scope:

```scala
import in.rcard.yaes.Writer.*

val (outerLog, (middleLog, (innerLog, result))) =
  Writer.run[String, (Vector[String], (Vector[String], Int))] {
    Writer.write("outer")
    Writer.capture[String, (Vector[String], Int)] {
      Writer.write("middle")
      Writer.capture[String, Int] {
        Writer.write("inner")
        42
      }
    }
  }
// outerLog  = Vector("outer", "middle", "inner")
// middleLog = Vector("middle", "inner")
// innerLog  = Vector("inner"), result = 42
```

### Combining Writer with Other Effects

`Writer` composes naturally with other λÆS effects:

```scala
import in.rcard.yaes.Writer.*
import in.rcard.yaes.State.*

val (state, (log, result)) = State.run(0) {
  Writer.run[String, Int] {
    Writer.write("start")
    State.update[Int](_ + 1)
    Writer.write(s"count=${State.get[Int]}")
    State.get[Int]
  }
}
// state = 1, log = Vector("start", "count=1"), result = 1
```

```scala
import in.rcard.yaes.Writer.*
import in.rcard.yaes.Raise.*

val (log, result) = Writer.run[String, Either[String, Int]] {
  Writer.write("before")
  val either = Raise.either[String, Int] {
    Writer.write("inside-raise")
    Raise.raise("error")
  }
  Writer.write("after")
  either
}
// result = Left("error")
// log = Vector("before", "inside-raise", "after")
```

### Writer API Reference

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `Writer.write` | `(W) => Writer[W] ?=> Unit` | Append a single value |
| `Writer.writeAll` | `(IterableOnce[W]) => Writer[W] ?=> Unit` | Append multiple values at once |
| `Writer.capture` | `(Writer[W] ?=> A) => Writer[W] ?=> (Vector[W], A)` | Capture writes from a block, forwarding to outer scope |
| `Writer.run` | `(Writer[W] ?=> A) => (Vector[W], A)` | Run computation, return accumulated values and result |

### Thread Safety

The Writer effect is not thread-safe. For concurrent scenarios:

- Use separate `Writer.run` blocks per thread — each gets isolated accumulation
- Implement synchronization if shared accumulation is required

---

## Reader Effect

The `Reader[R]` effect provides read-only access to an environment value of type `R`. It allows computations to read a shared value and temporarily override it via `local`, completing the classic Reader/Writer/State trio.

### Overview

The `Reader` effect manages a single immutable environment value of type `R`. Computations can read this value with `Reader.read` and temporarily modify it for a sub-scope with `Reader.local`. The original value is automatically restored after the block completes.

> **Thread-safe by construction:** `local` creates a fresh `Reader` instance for the inner block rather than mutating shared state, so concurrent fibers each see their own values.

### Reading Values

Use `Reader.read` to access the current environment value:

```scala
import in.rcard.yaes.Reader

case class Config(maxRetries: Int, timeout: Int)

val result = Reader.run(Config(3, 5000)) {
  val retries = Reader.read[Config].maxRetries
  val timeout = Reader.read[Config].timeout
  (retries, timeout)
}
// result = (3, 5000)
```

### The `reads` Infix Type

For more concise syntax, use the `reads` infix type, similar to `raises` and `writes`:

```scala
import in.rcard.yaes.{Reader, reads}

case class Config(maxRetries: Int, timeout: Int)

def getRetries: Int reads Config =
  Reader.read[Config].maxRetries

val result = Reader.run(Config(3, 5000)) {
  getRetries
}
// result = 3
```

### Local Overrides

The `local` operation runs a block with a modified environment value. The original value is restored after the block completes:

```scala
import in.rcard.yaes.Reader

case class Config(maxRetries: Int, timeout: Int)

val result = Reader.run(Config(3, 5000)) {
  val before = Reader.read[Config].maxRetries        // 3
  val during = Reader.local(_.copy(maxRetries = 10)) {
    Reader.read[Config].maxRetries                    // 10
  }
  val after = Reader.read[Config].maxRetries          // 3 (restored)
  (before, during, after)
}
// result = (3, 10, 3)
```

Local overrides can be nested — each scope sees its own value, and all restore correctly:

```scala
import in.rcard.yaes.Reader

val result = Reader.run(1) {
  val a = Reader.read[Int]                        // 1
  val b = Reader.local[Int, Int](_ + 10) {
    val inner = Reader.read[Int]                  // 11
    val deeper = Reader.local[Int, Int](_ + 100) {
      Reader.read[Int]                            // 111
    }
    Reader.read[Int]                              // 11 (restored)
  }
  Reader.read[Int]                                // 1 (restored)
}
```

### Combining Reader with Other Effects

`Reader` composes naturally with other λÆS effects:

```scala
import in.rcard.yaes.{Raise, Reader, raises, reads}

case class Config(maxRetries: Int)

def validate(value: Int): Unit raises String reads Config = {
  val max = Reader.read[Config].maxRetries
  if (value > max) Raise.raise(s"$value exceeds max $max")
}

val result = Reader.run(Config(5)) {
  Raise.either[String, Unit] {
    validate(10)
  }
}
// result = Left("10 exceeds max 5")
```

```scala
import in.rcard.yaes.{Reader, Writer, reads, writes}

case class Config(prefix: String)

def logWithPrefix(msg: String): Unit writes String reads Config =
  Writer.write(s"${Reader.read[Config].prefix}: $msg")

val (log, _) = Reader.run(Config("[APP]")) {
  Writer.run[String, Unit] {
    logWithPrefix("starting")
    logWithPrefix("done")
  }
}
// log = Vector("[APP]: starting", "[APP]: done")
```

### Reader API Reference

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `Reader.read` | `Reader[R] ?=> R` | Read the current environment value |
| `Reader.local` | `(R => R) => (Reader[R] ?=> A) => Reader[R] ?=> A` | Run block with modified value, restore after |
| `Reader.run` | `(R) => (Reader[R] ?=> A) => A` | Run computation with environment value, return `A` |

---

## Resource Effect

The `Resource` effect provides automatic resource management with guaranteed cleanup. It ensures that all acquired resources are properly released in LIFO (Last In, First Out) order, even when exceptions occur.

This is essential for managing files, database connections, network connections, and any other resource that needs explicit cleanup.

### Auto-Closeable Resources

For resources implementing `java.io.Closeable`, use `Resource.acquire`:

```scala
import in.rcard.yaes.Resource.*
import java.io.{FileInputStream, FileOutputStream}

def copyFile(source: String, target: String)(using Resource): Unit = {
  val input = Resource.acquire(new FileInputStream(source))
  val output = Resource.acquire(new FileOutputStream(target))

  // Copy file contents
  val buffer = new Array[Byte](1024)
  var bytesRead = input.read(buffer)
  while (bytesRead != -1) {
    output.write(buffer, 0, bytesRead)
    bytesRead = input.read(buffer)
  }
  // Both streams automatically closed here
}
```

### Custom Acquisition and Release

Use `Resource.install` for resources with custom cleanup logic:

```scala
import in.rcard.yaes.Resource.*

def processWithConnection()(using Resource): String = {
  val connection = Resource.install(openDatabaseConnection()) { conn =>
    conn.close()
    println("Database connection closed")
  }

  // Use connection safely — cleanup is guaranteed
  connection.executeQuery("SELECT * FROM users")
}
```

### Registering Cleanup Actions

Register standalone cleanup actions with `Resource.ensuring`:

```scala
import in.rcard.yaes.Resource.*

def processData()(using Resource): Unit = {
  Resource.ensuring {
    println("Processing completed")
  }

  Resource.ensuring {
    println("Cleanup temporary files")
  }

  // Main processing logic here
  // Cleanup actions run in reverse order (LIFO)
}
```

### Running Resource-Managed Code

Wrap resource-using code in `Resource.run` to trigger cleanup:

```scala
import in.rcard.yaes.Resource.*

val result = Resource.run {
  copyFile("source.txt", "target.txt")
  processWithConnection()
}
// All resources automatically cleaned up here
```

### LIFO Cleanup Order

Resources are always released in the reverse order they were acquired:

```scala
import in.rcard.yaes.Resource.*

def nestedResourceExample()(using Resource): Unit = {
  val outer = Resource.acquire(new FileInputStream("outer.txt"))
  println("Outer resource acquired")

  Resource.ensuring { println("Outer cleanup") }

  val inner = Resource.acquire(new FileInputStream("inner.txt"))
  println("Inner resource acquired")

  Resource.ensuring { println("Inner cleanup") }
}

Resource.run { nestedResourceExample() }
// Output:
// Outer resource acquired
// Inner resource acquired
// Inner cleanup
// Outer cleanup  ← LIFO: last acquired, first released
```

### Error Safety

Resources are cleaned up even when exceptions or raised errors occur:

```scala
import in.rcard.yaes.Resource.*
import in.rcard.yaes.Raise.*

def riskyOperation()(using Resource, Raise[String]): String = {
  val resource = Resource.acquire(new FileInputStream("data.txt"))

  if (Math.random() > 0.5) {
    Raise.raise("Random failure!")
  }

  "Success"
  // File is closed regardless of whether an error was raised
}
```

### Advanced: Multiple Resource Types

Resources of different types compose cleanly in a single scope:

```scala
import in.rcard.yaes.Resource.*
import java.io.*
import java.net.*

def downloadAndProcess(url: String, outputFile: String)(using Resource): Unit = {
  // Network connection with custom disconnect logic
  val connection = Resource.install(new URL(url).openConnection()) { conn =>
    conn.asInstanceOf[HttpURLConnection].disconnect()
  }

  // Input and output streams (auto-closeable)
  val input = Resource.acquire(connection.getInputStream())
  val output = Resource.acquire(new FileOutputStream(outputFile))

  // Completion notification
  Resource.ensuring {
    println(s"Download of $url completed")
  }

  input.transferTo(output)
}
```

### Resource API Summary

| Method | Description |
|--------|-------------|
| `Resource.acquire(r)` | Acquire a `Closeable` resource; auto-close on scope exit |
| `Resource.install(r)(cleanup)` | Acquire any resource with a custom cleanup function |
| `Resource.ensuring(block)` | Register a cleanup action to run on scope exit |
| `Resource.run { ... }` | Execute a block and release all acquired resources |

**Key guarantees:**
- Cleanup always runs, even on exceptions or raised errors
- Resources are released in LIFO order
- Composable with all other λÆS effects

---

With State, Writer, Reader, and Resource in your toolkit, you have everything you need for managing mutable data, value accumulation, environment access, and external dependencies safely. Next, we'll look at the most powerful data-flow primitives: **Streams & Channels**.
