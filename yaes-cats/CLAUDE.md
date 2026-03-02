## yaes-cats

Cats/Cats Effect integration module. Depends on `yaes-core`.

### Package Structure (following Cats conventions)

- `cats/` — Utility functions and operations (e.g., `accumulate`, `validated`)
- `instances/` — Typeclass instances (e.g., `raise.given` for MonadError, `accumulate.given` for AccumulateCollector)
- `syntax/` — Extension methods and syntax enhancements
- `interop/` — Interop with other libraries (e.g., `catseffect` for Cats Effect conversions)

### Test Structure

Tests follow the same package structure (e.g., `instances/AccumulateInstancesSpec.scala`).
