## Project Overview

λÆS (Yet Another Effect System) is an experimental effect system for Scala 3 inspired by Algebraic Effects. It uses context parameters and context functions for modular, composable effect management with deferred execution.

**Scala Version:** 3.8.1 | **Java Requirement:** Java 25+ (Virtual Threads, Structured Concurrency)

## Build and Test

Use SBT with `-batch --no-colors` flags for tests. Prefer testing a single class or module for faster feedback.

## Error Handling Philosophy

As an effect system, λÆS must never throw untracked exceptions (e.g., `require`, `IllegalArgumentException`). Invalid inputs must be handled without escaping the effect system. Two acceptable strategies:

1. **Default values**: Clamp or coerce invalid inputs to a sensible default (e.g., negative delay becomes `Duration.Zero`, `NaN` factor becomes `0.0`).
2. **Make wrong state unrepresentable**: Use validated opaque types so invalid values cannot be constructed at all (e.g., an opaque `PositiveDuration` type that only admits valid values).

Never use `require`, `assert`, or throw exceptions in public API surfaces.

## References

- Architecture and module structure: `ARCHITECTURE.md`
- Code style and documentation standards: `CONVENTIONS.md`
- Module-specific guidance: `<module>/CLAUDE.md`
