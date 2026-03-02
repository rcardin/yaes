## Code Style and Patterns

Always prefer readability and maintainability to smartness. Keep It Simple, Stupid (KISS) MUST be your mantra.

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
