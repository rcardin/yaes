![Made for Scala 3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/yaes-io/yaes/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/io.yaes/yaes-core-test-scalatest_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/yaes-io/yaes)
[![javadoc](https://javadoc.io/badge2/io.yaes/yaes-core-test-scalatest_3/javadoc.svg)](https://javadoc.io/doc/io.yaes/yaes-core-test-scalatest_3)
<br/>

# λÆS Test Utilities — ScalaTest

Test utilities for λÆS effect code using ScalaTest. Provides mixin traits that eliminate boilerplate handler wiring in tests for the `Raise`, `Log`, `Random`, and `Sync` effects.

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "io.yaes" %% "yaes-core-test-scalatest" % "0.23.0" % Test
```

Both `yaes-core` and ScalaTest are transitive dependencies — no need to declare them separately.

## Quick Start

### RaiseSpec

Mix `RaiseSpec` into your ScalaTest spec class:

```scala
import io.yaes.{Raise, raises}
import io.yaes.test.scalatest.RaiseSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MySpec extends AnyFlatSpec with Matchers with RaiseSpec {

  def divide(a: Int, b: Int): Int raises String =
    if b == 0 then Raise.raise("division by zero") else a / b

  "divide" should "return the quotient" in {
    val result = failOnRaise[String, Int] { divide(10, 2) }
    result shouldBe 5
  }

  it should "raise for zero divisor" in {
    val error = interceptRaised[String, Int] { divide(10, 0) }
    error shouldBe "division by zero"
  }
}
```

### LogSpec

Mix `LogSpec` into your spec to suppress all logging output. A no-op `Log` given is provided automatically:

```scala
import io.yaes.Log
import io.yaes.test.scalatest.LogSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MySpec extends AnyFlatSpec with Matchers with LogSpec {

  "myFunction" should "run without noisy log output" in {
    val logger = Log.getLogger("test")
    logger.info("silently discarded")  // no output, no exception
  }
}
```

### RandomSpec / RandomStub

Mix `RandomSpec` to get a queue-based `RandomStub` with automatic reset between tests:

```scala
import io.yaes.Random
import io.yaes.test.scalatest.RandomSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MySpec extends AnyFlatSpec with Matchers with RandomSpec {

  "myFunction" should "use a deterministic random int" in {
    rand.nextInts(42)
    val result = Random.nextInt
    result shouldBe 42
  }
}
```

The stub fails the test immediately with a descriptive `TestFailedException` if the code under test consumes more values than were enqueued.

### SyncSpec

Mix `SyncSpec` to run `Sync ?=> A` programs synchronously on the calling thread using `withSync`.
`withSync` supplies a contextual `Sync` whose `apply` is identity, so computations written with
`Sync { expr }` evaluate without virtual threads. Note that `Sync.run` and `Sync.runBlocking`
bypass the contextual `Sync` and always use their internal `JvmExecutor` — if the code under test
calls those methods, virtual threads may still be created.

```scala
import io.yaes.Sync
import io.yaes.test.scalatest.SyncSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MySpec extends AnyFlatSpec with Matchers with SyncSpec {

  "myFunction" should "return the computed value" in {
    val result = withSync { 42 }
    result shouldBe 42
  }

  it should "propagate exceptions" in {
    intercept[RuntimeException] {
      withSync { throw new RuntimeException("boom") }
    }
  }
}
```

## API

### RaiseSpec

| Method | Description |
|--------|-------------|
| `failOnRaise[E, A](body: Raise[E] ?=> A): A` | Runs body; returns result on success. Fails the test with a descriptive message if an error is raised. |
| `interceptRaised[E, A](body: Raise[E] ?=> A): E` | Runs body; returns the raised error for further assertions. Fails the test if the body completes successfully. |

**Failure Messages:**

- `failOnRaise` failure: `Expected the test not to raise any errors but it did with error '<error>'`
- `interceptRaised` failure: `Expected an error to be raised but body evaluated successfully`

### LogSpec

| API | Description |
|-----|-------------|
| `given Log` | A no-op `Log` instance that silently discards all log messages at every severity level. |

### RandomSpec / RandomStub

| API | Description |
|-----|-------------|
| `val rand: RandomStub` | The shared stub instance. Call `nextInts(...)`, `nextLongs(...)`, `nextBooleans(...)`, or `nextDoubles(...)` to enqueue values. |
| `given Random` | The `Random` given backed by `rand`. |
| `rand.reset()` | Clears all queues. Called automatically before each test via `withFixture`. |

### SyncSpec

| Method | Description |
|--------|-------------|
| `withSync[A](program: Sync ?=> A): A` | Runs the program synchronously on the calling thread. Exceptions propagate directly to the caller. For `Sync.apply`-based computations that do not invoke `Sync.run` or `Sync.runBlocking`, no virtual threads are used; code that calls `Sync.run`/`Sync.runBlocking` may still use `Sync`’s internal executor and create virtual threads. |

## Requirements

- **Java 25+**: Required by λÆS for virtual threads and structured concurrency
- **Scala 3.8.1+**: Uses Scala 3 context functions and given instances
- **yaes-core**: Provides the `Raise` effect (included transitively)
- **ScalaTest**: Provides `TestFailedException` (included transitively)

## Contributing

Contributions to the λÆS project are welcome! Please feel free to submit pull requests or open issues if you find bugs or have feature requests.
