## yaes-core

Contains all effect implementations — the foundation layer with no yaes dependencies.

### Effect Pattern

The canonical pattern for implementing effects:
```scala
type EffectName = EffectName.Unsafe

object EffectName {
  // DSL methods using context parameters
  def operation(using eff: EffectName): Result =
    eff.operationImpl(...)

  // Handler to run effects
  def run[A](program: EffectName ?=> A): A = {
    program(using unsafeImpl)
  }

  trait Unsafe {
    // Actual implementation
  }
}
```

### Infix Type Aliases Require Separate Imports

The infix types `raises`, `reads`, and `writes` are defined at the **package level** in `in.rcard.yaes`, not inside their companion objects. Importing `Raise.*`, `Reader.*`, or `Writer.*` does **not** bring them into scope:

```scala
// ✅ CORRECT — import the infix type separately
import in.rcard.yaes.raises
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int): Int raises DivisionByZero = ...

// ❌ INCORRECT — Raise.* does not include the `raises` infix type
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int): Int raises DivisionByZero = ... // won't compile
```

When writing documentation or code snippets that use infix types, always include the explicit import (e.g., `import in.rcard.yaes.{raises, reads}`) alongside the companion object import.

### Key Implementation Details

**Virtual Threads (Sync Effect):**
- Uses Java's Virtual Thread machinery via `Executors.newVirtualThreadPerTaskExecutor()`
- Creates a new virtual thread for each effectful computation
- Provides both non-blocking (`Sync.run`) and blocking (`Sync.runBlocking`) handlers

**Structured Concurrency (Async Effect):**
- Built on Java Structured Concurrency (requires Java 21+)
- All fibers created with `Async.fork` are managed in a structured scope
- `Async.run` waits for all forked fibers to complete, even if not explicitly joined
- Cancellation is cooperative and based on JVM interruption
- Canceling a parent fiber cancels all child fibers

**Error Handling (Raise Effect):**
- Uses Scala 3's `boundary`/`break` mechanism for control flow
- Supports typed errors (not just exceptions)
- Provides multiple handlers: `run`, `either`, `option`, `nullable`, `fold`
- Special features:
  - `MapError` for error transformation between layers
  - `accumulate`/`accumulating` for collecting multiple errors
  - `mapAccumulating` for transforming collections with error accumulation
  - `traced` for debugging with stack traces

**Resource Management (Resource Effect):**
- Guarantees cleanup in LIFO order (Last In, First Out)
- Three acquisition methods: `acquire` (for `Closeable`), `install` (custom acquisition/release), `ensuring` (cleanup actions)
- Cleanup occurs even on exceptions

**Shutdown Coordination (Shutdown Effect):**
- Provides graceful shutdown coordination for long-running applications
- Automatically registers JVM shutdown hooks (SIGTERM, SIGINT, Ctrl+C)
- Three main operations: `isShuttingDown()`, `initiateShutdown()`, `onShutdown(hook)`
- Thread-safe state management using `ReentrantLock`
- Idempotent — multiple shutdown calls are safe
- Hooks execute outside locks to prevent deadlock; failures are logged but don't prevent other hooks from running

**Graceful Shutdown (Async + Shutdown integration):**
- `Async.withGracefulShutdown` coordinates graceful shutdown by racing the main block against a timeout fiber using `Async.race()`
- Shutdown coordination is handled via `Shutdown.onShutdown()` hooks
- A `CountDownLatch` tracks completion and ensures timeout logic is applied only once

### Testing Functions with Raise Effect

**Use `Raise.either` (not `Raise.option`) for custom error types:**
```scala
// ✅ CORRECT
val result = Raise.either[HttpParseError, (String, String, String)] {
  HttpParser.parseRequestLine("GET /path HTTP/1.1")
}
result shouldBe Right(("GET", "/path", "HTTP/1.1"))

// ❌ INCORRECT — Raise.option only works with Raise[None.type]
val result = Raise.option { HttpParser.parseRequestLine(line) }
```

**Understanding Raise context in tests:**
Functions declared with `raises ErrorType` require a `Raise[ErrorType]` context parameter. Use `Raise.either`, `Raise.fold`, or other handlers that automatically provide the context:
```scala
val result = Raise.either[ErrorType, ResultType] {
  functionThatRaises(args)
}
result shouldBe Right(expectedValue)
```

### Polymorphic Accumulate API

The `Raise.accumulate` function is polymorphic over the error collection type `M[_]`:
```scala
def accumulate[M[_], Error, A](
  block: AccumulateScope[Error] ?=> A
)(using collector: AccumulateCollector[M]): Raise[M[Error]] ?=> A
```

- **Built-in collectors**: `List` (always available in yaes-core)
- **Cats collectors**: `NonEmptyList`, `NonEmptyChain` (in yaes-cats `instances.accumulate`)

**Warning:** When using `Raise.accumulate`, always assign the result to a variable before returning:
```scala
// ✅ CORRECT
val result = Raise.accumulate[List, String, List[Int]] {
  val items = list.map(i => accumulating { validate(i) })
  items
}

// ❌ INCORRECT — May not work
val result = Raise.accumulate[List, String, List[Int]] {
  list.map(i => accumulating { validate(i) })  // Direct return
}
```
