# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

λÆS (Yet Another Effect System) is an experimental effect system for Scala 3 inspired by Algebraic Effects. It uses Scala 3 context parameters and context functions to provide modular, composable effect management with deferred execution.

**Key Concepts:**
- Effects describe side effects in a type-safe way (e.g., `Random`, `Raise[E]`, `IO`, `Async`)
- Effects are managed via **context parameters** (`using` clauses)
- Execution is **deferred** until handlers run the effects
- Effects can be handled **one at a time** in any order, allowing fine-grained control

**Current Version:** 0.11.0
**Scala Version:** 3.7.4
**Java Requirement:** Java 24+ (for Virtual Threads and Structured Concurrency)

## Common Development Commands

### Build and Compilation
```bash
# Compile all modules
sbt compile

# Compile specific module
sbt yaes-core/compile
sbt yaes-data/compile
sbt yaes-cats/compile
sbt server/compile

# Clean build artifacts
sbt clean
```

### Testing
```bash
# Run all tests
sbt test

# Run tests for specific module
sbt yaes-core/test
sbt yaes-data/test
sbt yaes-cats/test
sbt server/test

# Run a single test class
sbt "yaes-core/testOnly in.rcard.yaes.RaiseSpec"
sbt "yaes-data/testOnly in.rcard.yaes.FlowSpec"
sbt "server/testOnly in.rcard.yaes.http.server.RoutesSpec"

# Run a single test within a class
sbt "yaes-core/testOnly in.rcard.yaes.RaiseSpec -- -z \"should handle errors\""
sbt "server/testOnly in.rcard.yaes.http.server.ServerShutdownSpec -- -z \"graceful shutdown\""
```

### Publishing
```bash
# Publish locally
sbt publishLocal

# Publish to Maven Central (requires credentials)
sbt publishSigned
```

### Documentation
```bash
# Generate Scaladoc
sbt doc
```

## Architecture

### Module Structure

The project consists of several modules:

1. **yaes-core** (`yaes-core/src/main/scala/in/rcard/yaes/`)
   - Contains all effect implementations
   - Depends on `yaes-data`
   - Main files: `Async.scala`, `Channel.scala`, `Clock.scala`, `IO.scala`, `Input.scala`, `Log.scala`, `Output.scala`, `Raise.scala`, `Random.scala`, `Resource.scala`, `State.scala`, `System.scala`, `Yaes.scala`, `YaesApp.scala`

2. **yaes-data** (`yaes-data/src/main/scala/in/rcard/yaes/`)
   - Contains data structures for use with effects
   - Main file: `Flow.scala` (cold asynchronous data streams)
   - Includes `FlowPublisher.scala` for Reactive Streams integration

3. **yaes-cats** (`yaes-cats/src/main/scala/in/rcard/yaes/`)
   - Cats/Cats Effect integration module
   - **Package structure** (following Cats conventions):
     - `cats/` - Utility functions and operations (e.g., `accumulate`, `validated`)
     - `instances/` - Typeclass instances (e.g., `raise.given` for MonadError, `accumulate.given` for AccumulateCollector)
     - `syntax/` - Extension methods and syntax enhancements
     - `interop/` - Interop with other libraries (e.g., `catseffect` for Cats Effect conversions)
   - **Test structure**: Tests follow the same package structure (e.g., `instances/AccumulateInstancesSpec.scala`)

4. **yaes-http-server** (`yaes-http/server/src/main/scala/in/rcard/yaes/http/server/`)
   - HTTP server built on YAES effects and JDK HttpServer
   - Uses virtual threads for request handling (each request gets its own fiber via `Async.fork`)
   - **Key components**:
     - `YaesServer.scala` - Server builder and lifecycle management
     - `Routes.scala` - Router with efficient exact/parameterized route matching
     - `Request.scala`/`Response.scala` - HTTP request/response models
     - `PathDSL.scala`/`MethodDSL.scala` - Type-safe route definition DSL
     - `QueryParams.scala`/`PathParams.scala` - Parameter extraction with type-safe parsing
     - `BodyCodec.scala` - Request/response body encoding/decoding
     - `Server.scala` - Running server instance with graceful shutdown
   - **Features**:
     - Graceful shutdown with in-flight request tracking
     - Type-safe path/query parameter extraction
     - Route optimization (O(1) exact matches, sequential parameterized routes)
     - Shutdown hooks for cleanup
     - Built on structured concurrency principles

### Core Effect System Design

**The `Yaes[F]` wrapper:**
```scala
class Yaes[+F](val unsafe: F)
```
- Wraps effect implementations in a type-safe container
- The `unsafe` field contains the actual effect implementation
- Should only be accessed through handlers

**Effect Pattern:**
```scala
type EffectName = Yaes[EffectName.Unsafe]

object EffectName {
  // DSL methods using context parameters
  def operation(using eff: EffectName): Result =
    eff.unsafe.operationImpl(...)

  // Handler to run effects
  def run[A](program: EffectName ?=> A): Result = {
    Yaes.handle(program)(using handler)
  }

  trait Unsafe {
    // Actual implementation
  }
}
```

**Effect Composition:**
- Effects are composed via context parameters: `(Effect1, Effect2) ?=> Result`
- The infix type `raises` provides syntactic sugar: `A raises E` ≡ `Raise[E] ?=> A`
- Handlers eliminate effects one at a time, maintaining referential transparency until the final handler

**Handler Order Matters:**
- When composing multiple effects, handlers must be applied in the correct nesting order
- Example in `YaesApp`: IO (outermost) → Output → Input → Random → Clock → System → Log (innermost)
- Each handler removes one effect from the context, unwrapping the computation step by step

### Key Implementation Details

**Virtual Threads (IO Effect):**
- The `IO` effect uses Java's Virtual Thread machinery via `Executors.newVirtualThreadPerTaskExecutor()`
- Creates a new virtual thread for each effectful computation
- Provides both non-blocking (`IO.run`) and blocking (`IO.runBlocking`) handlers

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
- Three acquisition methods:
  - `acquire` for `Closeable` resources
  - `install` for custom acquisition/release
  - `ensuring` for cleanup actions
- Cleanup occurs even on exceptions

**Channels (Communication Primitive):**
- Based on `java.util.concurrent` blocking queues with suspending operations
- Three types: Unbounded, Bounded (with overflow strategies), Rendezvous
- Overflow strategies for bounded channels: SUSPEND (default), DROP_OLDEST, DROP_LATEST
- Closing vs. Canceling: `close()` allows draining remaining elements, `cancel()` clears immediately
- Producer DSL: `produce` and `produceWith` for convenient channel creation
- Channel-Flow bridge: `channelFlow` creates Flows backed by channels for concurrent emission
- **Core operations** (`send`, `receive`, `cancel`, `foreach`) **don't require Async context** - they only use ReentrantLock/Condition which work with all thread types
- **Builder functions** (`produce`, `produceWith`, `channelFlow`) still require Async for `Async.fork()` and structured concurrency

**Flows (yaes-data):**
- Cold asynchronous data streams (similar to iterators but async)
- Terminal operation: `collect(collector)`
- Operators: `map`, `filter`, `take`, `drop`, etc.
- `buffer` operator enables concurrent producer/consumer via channels
- `channelFlow` creates Flows with concurrent emission capabilities

### Testing Conventions

Tests are located in:
- `yaes-core/src/test/scala/in/rcard/yaes/`
- `yaes-data/src/test/scala/in/rcard/yaes/`
- `yaes-cats/src/test/scala/in/rcard/yaes/`
- `yaes-http/server/src/test/scala/in/rcard/yaes/http/server/`

Tests use ScalaTest with the following structure:
```scala
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EffectNameSpec extends AnyFlatSpec with Matchers {
  "EffectName" should "do something" in {
    // Test implementation
  }
}
```

**HTTP Server Testing:**
- Integration tests use actual HTTP requests via `YaesServer.run`
- Tests verify graceful shutdown, route matching, parameter parsing, and body encoding/decoding
- Example test pattern:
```scala
import in.rcard.yaes.http.server.*

"YaesServer" should "handle GET requests" in {
  Async.run {
    IO.run {
      val routes = Routes(GET / "users" -> { req => Response.ok("Users list") })
      val server = YaesServer(routes).run(port = 8080)
      // Test request handling
      server.shutdown()
    }
  }
}
```

## Important Constraints and Gotchas

### Polymorphic Accumulate API
The `Raise.accumulate` function is polymorphic over the error collection type `M[_]`:
```scala
def accumulate[M[_], Error, A](
  block: AccumulateScope[Error] ?=> A
)(using collector: AccumulateCollector[M]): Raise[M[Error]] ?=> A
```

- **Built-in collectors**: `List` (always available in yaes-core)
- **Cats collectors**: `NonEmptyList`, `NonEmptyChain` (in yaes-cats `instances.accumulate`)
- **Type aliases** (in yaes-cats `package.scala`):
  - `RaiseNel[E]` = `Raise[NonEmptyList[E]]`
  - `RaiseNec[E]` = `Raise[NonEmptyChain[E]]`
- **Location**:
  - Core typeclass and List collector: `yaes-core/src/main/scala/in/rcard/yaes/Raise.scala`
  - Cats collectors: `yaes-cats/src/main/scala/in/rcard/yaes/instances/accumulate.scala`
  - Type aliases: `yaes-cats/src/main/scala/in/rcard/yaes/package.scala`
  - Tests: `yaes-cats/src/test/scala/in/rcard/yaes/instances/AccumulateInstancesSpec.scala`

**Usage examples**:
```scala
// List (default)
Raise.accumulate[List, String, A] { ... }

// NonEmptyList (requires: import in.rcard.yaes.instances.accumulate.given)
Raise.accumulate[NonEmptyList, String, A] { ... }

// NonEmptyChain (requires: import in.rcard.yaes.instances.accumulate.given)
Raise.accumulate[NonEmptyChain, String, A] { ... }

// Using type aliases for cleaner signatures
import in.rcard.yaes.{RaiseNel, RaiseNec}

def validate(x: Int): RaiseNel[String] ?=> Int = { ... }
def process(data: List[Int]): RaiseNec[String] ?=> Result = { ... }
```

### Error Accumulation Warning
When using `Raise.accumulate` with lists or collections, **ALWAYS** assign the result to a variable before returning:
```scala
// ✅ CORRECT
val result = Raise.accumulate[List, String, List[Int]] {
  val items = list.map(i => accumulating { validate(i) })
  items  // Return the variable
}

// ❌ INCORRECT - May not work
val result = Raise.accumulate[List, String, List[Int]] {
  list.map(i => accumulating { validate(i) })  // Direct return
}
```

### Async Effect is Not Thread-Safe
The `State` effect is not thread-safe. Use appropriate synchronization when accessing state from multiple fibers.

### Cancellation is Cooperative
Canceling a fiber via `fiber.cancel()` does not immediately terminate it. The fiber must reach an interruptible operation (like `Async.delay`) to be canceled.

### Handler Execution Breaks Referential Transparency
Running handlers (`IO.run`, `Raise.run`, etc.) executes effects and breaks referential transparency. Handlers should only be used at the edges of the application (e.g., in `main` or `YaesApp`).

### Java 24 Requirement
The library requires Java 24+ for Virtual Threads and Structured Concurrency features. Ensure your development environment has Java 24 or higher.

### HTTP Server Shutdown Behavior
The HTTP server provides graceful shutdown with the following guarantees:
- `server.shutdown()` is idempotent - safe to call multiple times
- Shutdown waits for all in-flight requests to complete before cleanup
- New requests during shutdown receive 503 Service Unavailable
- Shutdown hooks run during `Resource` cleanup, before server stops
- Structured concurrency ensures all request handler fibers complete via `Async.run`'s `StructuredTaskScope.join()`

## Code Style and Patterns

### Effect Declaration
When declaring functions that use effects, prefer explicit `using` clauses for clarity:
```scala
def operation(param: Type)(using Effect1, Effect2): Result = ...
```

For `Raise` effect, you can use the infix type for conciseness:
```scala
def operation(param: Type): Result raises ErrorType = ...
```

### Handler Composition
When multiple effects are involved, handle them from outermost to innermost:
```scala
val result = OuterEffect.run {
  MiddleEffect.run {
    InnerEffect.run {
      computation
    }
  }
}
```

### Naming Conventions
- Effect types use PascalCase: `IO`, `Async`, `Raise[E]`
- Effect DSL methods use camelCase: `Random.nextInt`, `Raise.raise`, `Async.fork`
- Handlers are typically named `run`, with variants like `runBlocking`, `either`, `option`

### HTTP Server Route Definition
When defining HTTP server routes, use the type-safe DSL:
```scala
import in.rcard.yaes.http.server.*

// Simple route
val route1 = GET / "users" -> { req => Response.ok("Users") }

// Route with path parameters (use *: for type-safe extraction)
val route2 = GET / "users" / *:[Int] -> { (req, userId) =>
  Response.ok(s"User $userId")
}

// Route with query parameters
val route3 = GET / "search" ? "q" *: StringParam -> { (req, query) =>
  Response.ok(s"Searching for: $query")
}

// Route with both path and query parameters
val route4 = GET / "users" / *:[Int] ? "include" *: StringParam -> { (req, userId, include) =>
  Response.ok(s"User $userId with $include")
}

// Combine routes and run server
val routes = Routes(route1, route2, route3, route4)
val server = YaesServer(routes)
  .onShutdown(() => println("Cleanup"))
  .run(port = 8080)
```

**Route Matching Order:**
- Exact routes (no parameters) are checked first via map lookup (O(1))
- Parameterized routes are checked sequentially in definition order
- First matching route wins
- Returns 404 if no route matches

## Documentation Standards

The project uses comprehensive Scaladoc with examples. When adding or modifying code:

1. Include Scaladoc for all public APIs
2. Provide usage examples in `{{{ }}}` blocks
3. Document parameters with `@param`, return values with `@return`, type parameters with `@tparam`
4. Cross-reference related functions and effects

Example:
```scala
/** Brief description of what this does.
  *
  * Detailed explanation if needed.
  *
  * Example:
  * {{{
  * val result = Effect.operation(param)
  * // result will be ...
  * }}}
  *
  * @param param description
  * @return description
  * @tparam A description
  */
def operation[A](param: Type): A = ...
```

## Related Resources

- Main Documentation: https://rcardin.github.io/yaes/
- README: Comprehensive usage guide with examples for all effects
- Talks: Scalar 2025 presentation on YouTube (see README)
