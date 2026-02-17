## Project Overview

λÆS (Yet Another Effect System) is an experimental effect system for Scala 3 inspired by Algebraic Effects. It uses Scala 3 context parameters and context functions to provide modular, composable effect management with deferred execution.

**Key Concepts:**
- Effects describe side effects in a type-safe way (e.g., `Random`, `Raise[E]`, `Sync`, `Async`)
- Effects are managed via **context parameters** (`using` clauses)
- Execution is **deferred** until handlers run the effects
- Effects can be handled **one at a time** in any order, allowing fine-grained control

**Scala Version:** 3.8.1
**Java Requirement:** Java 25+ (for Virtual Threads and Structured Concurrency)

## Common Development Commands

### Build and Compilation

Use SBT for building and testing the project. Use the `-batch --no-colors --error` flags during tests.
If you're searching for an error, use the `-batch --no-colors` flags.
Prefer testing a single class or module during development for faster feedback.

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

## Architecture and Design

If you need to understand the architecture and design of λÆS, refer to the `ARCHITECTURE.md` file for detailed explanations of the core components, design decisions, and how effects are implemented and managed in the system.