## Architecture

Always prefer readability and maintainability to smartness. Keep It Simple, Stupid (KISS) MUST be your mantra.

### Module Structure

The project consists of several modules:

1. **yaes-core** (`yaes-core/src/main/scala/in/rcard/yaes/`)
   - Contains all effect implementations (foundation layer with no yaes dependencies)
   - Main files: `Async.scala`, `Clock.scala`, `Sync.scala`, `Input.scala`, `Log.scala`, `Output.scala`, `Raise.scala`, `Random.scala`, `Resource.scala`, `Shutdown.scala`, `State.scala`, `System.scala`, `Yaes.scala`, `YaesApp.scala`

2. **yaes-data** (`yaes-data/src/main/scala/in/rcard/yaes/`)
   - Contains data structures for use with effects
   - Depends on `yaes-core`
   - Main files: `Flow.scala` (cold asynchronous data streams), `Channel.scala` (communication primitive), `FlowPublisher.scala` (Reactive Streams integration)

3. **yaes-cats** (`yaes-cats/src/main/scala/in/rcard/yaes/`)
   - Cats/Cats Effect integration module
   - Depends on `yaes-core`
   - **Package structure** (following Cats conventions):
     - `cats/` - Utility functions and operations (e.g., `accumulate`, `validated`)
     - `instances/` - Typeclass instances (e.g., `raise.given` for MonadError, `accumulate.given` for AccumulateCollector)
     - `syntax/` - Extension methods and syntax enhancements
     - `interop/` - Interop with other libraries (e.g., `catseffect` for Cats Effect conversions)
   - **Test structure**: Tests follow the same package structure (e.g., `instances/AccumulateInstancesSpec.scala`)

### Core Effect System Design

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
- Example in `YaesApp`: Sync (outermost) → Output → Input → Random → Clock → System (innermost)
- Each handler removes one effect from the context, unwrapping the computation step by step

### Key Implementation Details

**Virtual Threads (Sync Effect):**
- The `Sync` effect uses Java's Virtual Thread machinery via `Executors.newVirtualThreadPerTaskExecutor()`
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
- Three acquisition methods:
  - `acquire` for `Closeable` resources
  - `install` for custom acquisition/release
  - `ensuring` for cleanup actions
- Cleanup occurs even on exceptions

**Shutdown Coordination (Shutdown Effect):**
- Provides graceful shutdown coordination for long-running applications
- Automatically registers JVM shutdown hooks (SIGTERM, SIGINT, Ctrl+C)
- Three main operations:
  - `isShuttingDown()` - check if shutdown has been initiated
  - `initiateShutdown()` - manually trigger graceful shutdown
  - `onShutdown(hook)` - register callbacks to execute when shutdown begins
- Thread-safe state management using `ReentrantLock`
- Idempotent - multiple shutdown calls are safe
- Hooks execute outside locks to prevent deadlock
- Hook failures are logged but don't prevent other hooks from running
- Hooks registered after shutdown has started are silently ignored
- Particularly useful with `Async` for daemon processes

**GracefulShutdownScope (Async + Shutdown Integration):**
- Used by `Async.withGracefulShutdown` to coordinate shutdown with timeout enforcement
- Creates a timeout enforcer fiber that waits for shutdown signal, then enforces deadline
- When main task completes after shutdown, scope shuts down immediately and cancels remaining fibers
- **JDK Protection Against Spurious Exceptions:** The JDK's `SubtaskImpl` checks `isShutdown()` after catching exceptions. If the scope is already shutdown when a fiber throws an exception (like the timeout enforcer's `InterruptedException`), the JDK **does not call** `handleComplete`, preventing spurious failure propagation
- This design relies on JDK's structured concurrency implementation details for correct exception handling
- Exception handling in `handleComplete` captures only genuine failures, not interruptions after shutdown
- Key invariant: Timeout enforcer's interruption when scope shuts down early is **not** treated as a failure

**Channels (yaes-data):**
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

### State Effect is Not Thread-Safe
The `State` effect is not thread-safe. Use appropriate synchronization (e.g., `java.util.concurrent` primitives) when accessing state from multiple fibers or threads.

### Cancellation is Cooperative
Canceling a fiber via `fiber.cancel()` does not immediately terminate it. The fiber must reach an interruptible operation (like `Async.delay`) to be canceled.

### Handler Execution Breaks Referential Transparency
Running handlers (`Sync.run`, `Raise.run`, etc.) executes effects and breaks referential transparency. Handlers should only be used at the edges of the application (e.g., in `main` or `YaesApp`).

### Java 25 Requirement
The library requires Java 25+ for Virtual Threads and Structured Concurrency features. Ensure your development environment has Java 25 or higher.

### HTTP Server Shutdown Behavior
The HTTP server provides graceful shutdown with the following guarantees:
- `server.shutdown()` is idempotent - safe to call multiple times
- Shutdown waits for all in-flight requests to complete before cleanup
- New requests during shutdown receive 503 Service Unavailable
- Shutdown hooks run during `Resource` cleanup, before server stops
- Structured concurrency ensures all request handler fibers complete via `Async.run`'s `StructuredTaskScope.join()`

## Common Issues and Resolutions

This section documents issues encountered during development and their solutions, to help future developers avoid the same pitfalls.

### Issue 1: SBT Project Naming for HTTP Server Module

**Problem:**
When trying to compile the HTTP server module with `sbt "yaes-http/server/compile"`, SBT fails with the error:
```
Expected configuration
Not a valid key: server (similar: serverPort, serverHost, serverUseJni)
yaes-http/server/compile
```

**Root Cause:**
The project is defined in `build.sbt` as:
```scala
lazy val server = project.in(file("yaes-http/server"))
```
This creates a project named `server` (not `yaes-http/server`), with sources located in the `yaes-http/server` directory.

**Resolution:**
Use the project name, not the directory path:
```bash
# ✅ CORRECT
sbt "server/compile"
sbt "server/test"
sbt "server/testOnly in.rcard.yaes.http.server.HttpParserSpec"

# ❌ INCORRECT
sbt "yaes-http/server/compile"
```

### Issue 2: Testing Functions with Raise Effect - Raise.option vs Raise.either

**Problem:**
When testing functions that use custom error types with the Raise effect (e.g., `HttpParseError`), using `Raise.option` results in compilation errors:
```scala
// ❌ INCORRECT - Compilation error
val result = Raise.option { HttpParser.parseRequestLine(line) }
// Error: No given instance of type Raise[HttpParseError] was found
```

**Root Cause:**
`Raise.option` is specifically designed to work with `Raise[None.type]` as the error type, not custom error types:
```scala
// From Raise.scala
def option[A](block: Raise[None.type] ?=> A): Option[A] = ...
```

When you call a function requiring `Raise[HttpParseError]` inside a `Raise.option` block, there's a type mismatch: the block provides `Raise[None.type]`, but the function needs `Raise[HttpParseError]`.

**Resolution:**
Use `Raise.either[ErrorType, ResultType]` for testing functions with custom error types:

```scala
// ✅ CORRECT - Success case
val result = Raise.either[HttpParseError, (String, String, String)] {
  HttpParser.parseRequestLine("GET /path HTTP/1.1")
}
result shouldBe Right(("GET", "/path", "HTTP/1.1"))

// ✅ CORRECT - Error case
val error = Raise.either[HttpParseError, (String, String, String)] {
  HttpParser.parseRequestLine("INVALID")
}
error shouldBe Left(HttpParseError.MalformedRequestLine)
error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
```

**When to use each handler:**
- `Raise.option`: Only when the function uses `Raise[None.type]` (rarely used directly)
- `Raise.either[E, A]`: For functions with custom error types, returns `Either[E, A]`
- `Raise.run`: For functions that raise errors you want to catch as the error value

### Issue 3: Understanding Raise Context in Tests

**Problem:**
Compilation errors when calling functions with `raises` syntax inside test blocks:
```
No given instance of type in.rcard.yaes.Raise[ErrorType] was found
```

**Root Cause:**
Functions declared with `raises ErrorType` are syntactic sugar for context parameters:
```scala
// These are equivalent:
def parse(line: String): Result raises HttpParseError
def parse(line: String): Raise[HttpParseError] ?=> Result
```

The function requires a `Raise[HttpParseError]` context parameter to be available in scope. Test code must create this context.

**Resolution:**
Use `Raise.either`, `Raise.fold`, or other handlers that **automatically provide** the Raise context:

```scala
// ✅ CORRECT - Raise.either provides Raise[HttpParseError] context
val result = Raise.either[HttpParseError, Request] {
  HttpParser.parseRequest(inputStream, config)
  // Inside this block, Raise[HttpParseError] is available as an implicit context parameter
}

// ✅ CORRECT - Raise.fold also works
val result = Raise.fold(
  { HttpParser.parseRequest(inputStream, config) }
)(
  onError = error => fail(s"Unexpected error: ${error.message}"),
  onSuccess = request => request
)
```

**Pattern for Test Cases:**

Success assertions:
```scala
val result = Raise.either[ErrorType, ResultType] {
  functionThatRaises(args)
}
result shouldBe Right(expectedValue)
```

Error assertions:
```scala
val error = Raise.either[ErrorType, ResultType] {
  functionThatRaises(args)
}
error shouldBe Left(ExpectedError.SomeCase)
error.left.toOption.map(_.toResponse.status) shouldBe Some(expectedStatusCode)
```

### Issue 4: HTTP Parser Refactoring Pattern - Either to Raise

**Context:**
When refactoring error-handling code from `Either[ErrorResponse, Result]` to the `Raise` effect.

**Pattern Summary:**

**Before (Either-based):**
```scala
def parseRequestLine(line: String): Either[Response, (String, String, String)] = {
  if (condition) {
    return Left(Response(400, body = "Bad Request"))
  }
  Right((method, path, version))
}
```

**After (Raise-based):**
```scala
def parseRequestLine(line: String): (String, String, String) raises HttpParseError = {
  if (condition) {
    Raise.raise(HttpParseError.MalformedRequestLine)
  }
  (method, path, version)  // Direct return
}
```

**Key Changes:**
1. Return type: `Either[Response, T]` → `T raises HttpParseError`
2. Error returns: `Left(Response(...))` → `Raise.raise(ErrorType)`
3. Success returns: `Right(value)` → Direct return of `value`
4. Remove all pattern matching on `Either` results from called functions
5. Create sealed error trait with `toResponse: Response` method for HTTP conversion

**Integration at Call Site:**
```scala
// Use Raise.onError to handle errors at the boundary
Raise.onError {
  val request = HttpParser.parseRequest(inputStream, config)
  // Process request...
} { parseError =>
  // Convert error to HTTP response
  val errorResponse = parseError.toResponse
  HttpWriter.writeResponse(outputStream, errorResponse)
}
```

**Benefits:**
- Cleaner sequential code without nested pattern matching
- Type-safe error handling with specific error types
- Composable with other YAES effects
- Errors as values, not exceptions

### Issue 5: URL Decoding and Path Traversal Security

**Context:**
When implementing HTTP path or query parameter parsing that involves URL decoding.

**Problem:**
URL encoding can be used to bypass naive security checks. For example, path traversal attempts can be hidden using percent-encoding:
- Literal: `/files/../etc/passwd` (might be caught by simple string checks)
- Encoded: `/files/%2e%2e/etc/passwd` (bypasses naive checks)

**Critical Security Pattern:**

**❌ INCORRECT - Validate before decoding:**
```scala
// This can be bypassed with encoded characters!
if (rawPath.contains("..")) {
  Raise.raise(HttpParseError.MalformedPath)
}
val decoded = URLDecoder.decode(rawPath, StandardCharsets.UTF_8)
```

**✅ CORRECT - Decode first, then validate:**
```scala
// Decode the path segment
val decoded = try {
  URLDecoder.decode(segment, StandardCharsets.UTF_8)
} catch {
  case _: IllegalArgumentException =>
    Raise.raise(HttpParseError.MalformedPath)
}

// Now validate the decoded value
if (decoded == ".." || decoded.contains("..")) {
  Raise.raise(HttpParseError.MalformedPath)
}
```

**Implementation Pattern for Path Decoding:**
```scala
private def decodeAndValidatePath(path: String): String raises HttpParseError = {
  try {
    // Split into segments, decode each one
    val segments = path.split("/").filter(_.nonEmpty)

    val decodedSegments = segments.map { segment =>
      val decoded = URLDecoder.decode(segment, StandardCharsets.UTF_8)

      // Security check AFTER decoding
      if (decoded == ".." || decoded.contains("..")) {
        Raise.raise(HttpParseError.MalformedPath)
      }

      decoded
    }

    // Reconstruct path with leading slash if present
    val result = decodedSegments.mkString("/")
    if (path.startsWith("/")) s"/$result" else result

  } catch {
    case _: IllegalArgumentException =>
      Raise.raise(HttpParseError.MalformedPath)
  }
}
```

**Key Principles:**
1. **Always decode at the boundary** - Decode URLs as soon as they enter your system
2. **Validate after decoding** - Security checks must run on decoded values
3. **Never trust encoded input** - Encoding can hide malicious patterns
4. **Consistent error handling** - Use the same error type for both invalid encoding and security violations
5. **Decode once** - Don't decode multiple times or at multiple layers

**Common Encoding Attacks:**
- Path traversal: `%2e%2e` → `..`
- Null bytes: `%00` → `\0` (can truncate strings in some languages)
- Space encoding: `%20` or `+` → ` ` (can break parsing)
- Slash encoding: `%2F` → `/` (can create new path segments)

**Testing Strategy:**
Always test both literal and encoded versions of security-sensitive patterns:
```scala
it should "reject literal path traversal" in {
  // Test: /files/../etc/passwd
}

it should "reject encoded path traversal" in {
  // Test: /files/%2e%2e/etc/passwd
}
```

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
- Effect types use PascalCase: `Sync`, `Async`, `Raise[E]`
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
