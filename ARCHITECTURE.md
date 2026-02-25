## Architecture

Always prefer readability and maintainability to smartness. Keep It Simple, Stupid (KISS) MUST be your mantra.

### Module Structure

| Module | Path | Description |
|--------|------|-------------|
| **yaes-core** | `yaes-core/src/main/scala/in/rcard/yaes/` | All effect implementations (foundation layer, no yaes dependencies) |
| **yaes-data** | `yaes-data/src/main/scala/in/rcard/yaes/` | Data structures for use with effects (depends on yaes-core) |
| **yaes-cats** | `yaes-cats/src/main/scala/in/rcard/yaes/` | Cats/Cats Effect integration (depends on yaes-core) |
| **yaes-http** | `yaes-http/` | HTTP module with server subproject |

Each module has its own `CLAUDE.md` with implementation details and gotchas specific to that module.

### Effect Composition Model

- Effects are composed via context parameters: `(Effect1, Effect2) ?=> Result`
- The infix type `raises` provides syntactic sugar: `A raises E` ≡ `Raise[E] ?=> A`
- Handlers eliminate effects one at a time, maintaining referential transparency until the final handler
- Handler order matters — handlers must be applied from outermost to innermost (e.g., in `YaesApp`: Sync → Output → Input → Random → Clock → System)

### Important Constraints and Gotchas

**State Effect is Not Thread-Safe:**
The `State` effect is not thread-safe. Use appropriate synchronization (e.g., `java.util.concurrent` primitives) when accessing state from multiple fibers or threads.

**Cancellation is Cooperative:**
Canceling a fiber via `fiber.cancel()` does not immediately terminate it. The fiber must reach an interruptible operation (like `Async.delay`) to be canceled.

**Handler Execution Breaks Referential Transparency:**
Running handlers (`Sync.run`, `Raise.run`, etc.) executes effects and breaks referential transparency. Handlers should only be used at the edges of the application (e.g., in `main` or `YaesApp`).

**Java 25 Requirement:**
The library requires Java 25+ for Virtual Threads and Structured Concurrency features. Ensure your development environment has Java 25 or higher.

## Related Resources

- Main Documentation: https://rcardin.github.io/yaes/
- Code style and documentation standards: `CONVENTIONS.md`
