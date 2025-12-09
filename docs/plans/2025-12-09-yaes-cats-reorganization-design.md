# yaes-cats Reorganization Design

**Date:** 2025-12-09
**Status:** Design Complete
**Goal:** Reorganize yaes-cats module to follow Cats and Cats Effect naming conventions and organizational patterns

## Overview

This design reorganizes the yaes-cats integration module to align with Cats ecosystem conventions, improving:
- **Consistency** with Cats/Cats Effect naming patterns
- **Module organization** following Cats-style package structure
- **API ergonomics** through granular imports and clear discoverability

## Design Principles

1. **Follow Cats conventions** - Match how Cats Effect organizes code
2. **Granular imports** - Users import only what they need
3. **Clear separation** - Distinguish interop (bidirectional conversion) from utilities (new capabilities using Cats types)
4. **Keep YAES patterns** - Maintain `.value` naming for consistency with YAES's existing conventions

## Package Structure

```
yaes-cats/src/main/scala/in/rcard/yaes/
├── interop/
│   └── catseffect.scala          # Bidirectional YAES ↔ Cats Effect IO
├── cats/
│   ├── validated.scala            # Raise → Validated conversions
│   └── accumulate.scala           # Error accumulation utilities
├── instances/
│   ├── raise.scala                # MonadError instance for Raise
│   └── package.scala              # Package object if needed
├── syntax/
│   ├── catseffect.scala           # CatsIO extension methods
│   ├── validated.scala            # Validated extension methods
│   ├── accumulate.scala           # Iterable/NEL extension methods
│   ├── all.scala                  # Combined syntax
│   └── package.scala              # Package object if needed
├── all.scala                      # Convenience unified imports (optional)
└── package.scala                  # Type aliases, etc.
```

### Rationale

**`interop` package** - Following fs2's pattern (`fs2.interop.flow`), this package contains bidirectional conversions between effect systems. Since we're converting between YAES IO and Cats Effect IO, this clearly signals "integration/interoperability code."

**`cats` package** - Contains utilities that provide new YAES capabilities using Cats types (Validated, Semigroup, NonEmptyList). These aren't pure interop; they're YAES utilities enhanced by Cats.

**`instances` package** - Unchanged. Contains typeclass instances following Cats convention.

**`syntax` package** - Unchanged structure. Extension methods organized by concern, following Cats pattern.

## API Design

### 1. interop.catseffect - Bidirectional IO Conversions

**File:** `yaes-cats/src/main/scala/in/rcard/yaes/interop/catseffect.scala`

```scala
package in.rcard.yaes.interop

import in.rcard.yaes.{IO => YaesIO}
import in.rcard.yaes.Raise
import cats.effect.{IO => CatsIO}
import cats.effect.Sync
import scala.concurrent.duration.Duration

/** Bidirectional conversion between YAES IO and Cats Effect IO.
  *
  * This object provides methods to convert between YAES's context function-based IO
  * effect and Cats Effect's monadic IO.
  *
  * For extension methods on Cats Effect IO, import the syntax:
  * {{{
  * import in.rcard.yaes.syntax.catseffect.given
  * }}}
  */
object catseffect {

  // YAES → Cats Effect conversions

  def blocking[F[_]: Sync, A](yaesProgram: (YaesIO, Raise[Throwable]) ?=> A): F[A]

  def delay[F[_]: Sync, A](yaesProgram: (YaesIO, Raise[Throwable]) ?=> A): F[A]

  def blockingIO[A](yaesProgram: (YaesIO, Raise[Throwable]) ?=> A): CatsIO[A]

  def delayIO[A](yaesProgram: (YaesIO, Raise[Throwable]) ?=> A): CatsIO[A]

  // Cats Effect → YAES conversions

  def value[A](catsIO: CatsIO[A]): (YaesIO, Raise[Throwable]) ?=> A

  def value[A](catsIO: CatsIO[A], timeout: Duration): (YaesIO, Raise[Throwable]) ?=> A
}
```

**Key naming decisions:**
- Keep `blocking`/`delay` - matches Cats Effect's `Sync[F].blocking`/`Sync[F].delay`
- Keep `value` - consistent with YAES pattern (`Either.value`, `Option.value`, etc.)
- Descriptive suffixes (`blockingIO`, `delayIO`) for convenience methods

### 2. cats.validated - Validated Conversions

**File:** `yaes-cats/src/main/scala/in/rcard/yaes/cats/validated.scala`

```scala
package in.rcard.yaes.cats

import cats.data.{Validated, ValidatedNec, ValidatedNel}
import in.rcard.yaes.Raise

/** Conversion utilities between YAES Raise effect and Cats Validated types.
  *
  * This object provides functions to convert between YAES's Raise effect and Cats Validated,
  * ValidatedNec, and ValidatedNel types, enabling interoperability with Cats-based validation.
  *
  * For extension methods on Validated types, import the syntax:
  * {{{
  * import in.rcard.yaes.syntax.validated.given
  * }}}
  */
object validated {

  def validated[E, A](block: Raise[E] ?=> A): Validated[E, A]

  def validatedNec[E, A](block: Raise[E] ?=> A): ValidatedNec[E, A]

  def validatedNel[E, A](block: Raise[E] ?=> A): ValidatedNel[E, A]
}
```

**Key naming decisions:**
- Remove `Cats` prefix - redundant inside `yaes-cats` module
- Object name matches the primary type it works with (`validated`)
- No `cats.` prefix in package to avoid confusion with `cats.data.Validated`

### 3. cats.accumulate - Error Accumulation

**File:** `yaes-cats/src/main/scala/in/rcard/yaes/cats/accumulate.scala`

```scala
package in.rcard.yaes.cats

import cats.Semigroup
import cats.data.NonEmptyList
import in.rcard.yaes.Raise

/** Error accumulation utilities using Cats Semigroup typeclass and NonEmptyList.
  *
  * This object provides functions to accumulate errors using either:
  * - Cats' Semigroup typeclass for flexible error combining strategies (methods ending with 'S')
  * - NonEmptyList for collecting individual errors
  *
  * For extension methods like `combineErrors` and `combineErrorsS`, import:
  * {{{
  * import in.rcard.yaes.syntax.accumulate.given
  * }}}
  */
object accumulate {

  // Semigroup-based accumulation

  def mapAccumulatingS[E: Semigroup, A, B](iterable: Iterable[A])
    (transform: A => (Raise[E] ?=> B))(using Raise[E]): List[B]

  def mapAccumulatingS[E: Semigroup, A, B](nonEmptyList: NonEmptyList[A])
    (transform: A => (Raise[E] ?=> B))(using Raise[E]): NonEmptyList[B]

  // NonEmptyList-based accumulation

  def mapAccumulating[E, A, B](iterable: Iterable[A])
    (transform: A => (Raise[E] ?=> B))(using Raise[NonEmptyList[E]]): List[B]

  def mapAccumulating[E, A, B](nonEmptyList: NonEmptyList[A])
    (transform: A => (Raise[E] ?=> B))(using Raise[NonEmptyList[E]]): NonEmptyList[B]
}
```

**Key naming decisions:**
- Remove `Cats` prefix
- Keep `mapAccumulatingS` suffix ('S' for Semigroup) - clear and established
- Object name describes the capability (`accumulate`)

### 4. instances.raise - Typeclass Instances

**File:** `yaes-cats/src/main/scala/in/rcard/yaes/instances/raise.scala`

```scala
package in.rcard.yaes.instances

import cats.MonadError
import in.rcard.yaes.Raise

/** Provides Cats typeclass instances for YAES Raise effect. */
object raise extends RaiseInstances

/** Typeclass instances for Raise effect.
  *
  * This trait provides MonadError instances that allow Raise computations to work seamlessly
  * with Cats libraries and combinators.
  */
trait RaiseInstances {

  given catsRaiseInstancesForMonadError[E]: MonadError[[A] =>> Raise[E] ?=> A, E] =
    new RaiseMonadError[E]

  class RaiseMonadError[E] extends MonadError[[A] =>> Raise[E] ?=> A, E] {
    // Implementation...
  }
}
```

**Key changes:**
- Trait becomes an object extending the trait (standard Cats pattern)
- Allows `import in.rcard.yaes.instances.raise.given`

### 5. syntax.* - Extension Methods

**File:** `yaes-cats/src/main/scala/in/rcard/yaes/syntax/catseffect.scala`

```scala
package in.rcard.yaes.syntax

import cats.effect.{IO => CatsIO}
import in.rcard.yaes.{IO => YaesIO}
import in.rcard.yaes.Raise
import scala.concurrent.duration.Duration

/** Syntax extensions for Cats Effect IO to integrate with YAES IO effect. */
object catseffect extends CatsEffectSyntax

trait CatsEffectSyntax {

  extension [A](io: CatsIO[A])
    /** Converts this Cats Effect IO to a YAES IO program.
      *
      * Exceptions are raised via `Raise[Throwable]`.
      */
    def value: (YaesIO, Raise[Throwable]) ?=> A

    /** Converts this Cats Effect IO to a YAES IO program with a timeout. */
    def value(timeout: Duration): (YaesIO, Raise[Throwable]) ?=> A
}
```

**File:** `yaes-cats/src/main/scala/in/rcard/yaes/syntax/validated.scala`

```scala
package in.rcard.yaes.syntax

import cats.data.Validated
import in.rcard.yaes.Raise

/** Syntax extensions for Cats Validated types to integrate with YAES Raise effect. */
object validated extends ValidatedSyntax

trait ValidatedSyntax {

  extension [E, A](validated: Validated[E, A])
    /** Extracts the value from a Validated or raises the error. */
    def value(using Raise[E]): A
}
```

**File:** `yaes-cats/src/main/scala/in/rcard/yaes/syntax/accumulate.scala`

```scala
package in.rcard.yaes.syntax

import cats.Semigroup
import cats.data.NonEmptyList
import in.rcard.yaes.Raise

/** Syntax extensions for error accumulation with Cats types. */
object accumulate extends AccumulateSyntax

trait AccumulateSyntax {

  extension [E, A](iterable: Iterable[Raise[E] ?=> A])
    def combineErrorsS(using Semigroup[E], Raise[E]): List[A]
    def combineErrors(using Raise[NonEmptyList[E]]): List[A]

  extension [E, A](nonEmptyList: NonEmptyList[Raise[E] ?=> A])
    def combineErrorsS(using Semigroup[E], Raise[E]): NonEmptyList[A]
    def combineErrors(using Raise[NonEmptyList[E]]): NonEmptyList[A]
}
```

**File:** `yaes-cats/src/main/scala/in/rcard/yaes/syntax/all.scala`

```scala
package in.rcard.yaes.syntax

/** Combined syntax extensions for all YAES Cats integrations. */
object all extends AllSyntax

trait AllSyntax extends CatsEffectSyntax
                    with ValidatedSyntax
                    with AccumulateSyntax
```

**Key changes:**
- `catsIO` → `catseffect` for consistency with object naming
- Keep trait + object pattern (established Cats convention)
- `AllSyntax` combines all syntax traits

### 6. all - Convenience Unified Imports (Optional)

**File:** `yaes-cats/src/main/scala/in/rcard/yaes/all.scala`

```scala
package in.rcard.yaes

/** Unified imports for all YAES Cats integrations.
  *
  * For production code, prefer granular imports:
  * {{{
  * import in.rcard.yaes.interop.catseffect.*
  * import in.rcard.yaes.syntax.catseffect.given
  * import in.rcard.yaes.instances.raise.given
  * }}}
  *
  * This object is provided for convenience and quick exploration.
  */
object all {
  // Re-export all object methods
  export interop.catseffect.*
  export cats.validated.*
  export cats.accumulate.*

  // Re-export all syntax (for .given imports)
  export syntax.all.*

  // Re-export instances (for .given imports)
  export instances.raise.given
}
```

**Usage:**
```scala
// Quick start (everything)
import in.rcard.yaes.all.{given, *}

// But documentation encourages granular imports instead
```

## Import Patterns

### Granular Imports (Recommended)

**Scenario 1: Converting YAES → Cats Effect**
```scala
import in.rcard.yaes.interop.catseffect.{blocking, blockingIO}
import cats.effect.{IO => CatsIO}

val yaesProgram: YaesIO ?=> Int = YaesIO { 42 }
val catsIO: CatsIO[Int] = blockingIO(yaesProgram)
```

**Scenario 2: Converting Cats Effect → YAES**
```scala
import in.rcard.yaes.interop.catseffect.value
import in.rcard.yaes.syntax.catseffect.given  // for .value extension
import in.rcard.yaes.{IO => YaesIO, Raise}
import cats.effect.{IO => CatsIO}

val catsIO: CatsIO[Int] = CatsIO.pure(42)

// Object method
val result1 = YaesIO.run {
  Raise.either { value(catsIO) }
}

// Extension method (preferred)
val result2 = YaesIO.run {
  Raise.either { catsIO.value }
}
```

**Scenario 3: Error accumulation with Cats types**
```scala
import in.rcard.yaes.cats.accumulate.mapAccumulatingS
import in.rcard.yaes.syntax.accumulate.given
import cats.Semigroup

given Semigroup[String] = Semigroup.instance(_ + _)

// Object method
val results1: List[Int] raises String =
  mapAccumulatingS(List(1,2,3))(validate)

// Extension method
val results2: List[Int] raises String =
  List(comp1, comp2, comp3).combineErrorsS
```

**Scenario 4: Working with Validated**
```scala
import in.rcard.yaes.cats.validated.{validated, validatedNel}
import in.rcard.yaes.syntax.validated.given
import cats.data.Validated

val v: Validated[String, Int] = validated { Raise.raise("error") }

val extracted: Int raises String = v.value  // extension method
```

**Scenario 5: Using MonadError instance**
```scala
import in.rcard.yaes.instances.raise.given
import cats.syntax.all.*

def comp1: Int raises String = Raise.raise("error")
def comp2: Int raises String = 42

val result: Int raises String = comp1.handleError(_ => comp2)
```

**Scenario 6: All syntax at once**
```scala
import in.rcard.yaes.syntax.all.given
// Now have access to all extension methods
```

## Migration Guide

### Breaking Changes from 0.9.0

| Old (0.9.0) | New | Migration |
|-------------|-----|-----------|
| `import in.rcard.yaes.Cats.*` | `import in.rcard.yaes.interop.catseffect.*` | Replace object name |
| `import in.rcard.yaes.CatsValidated.*` | `import in.rcard.yaes.cats.validated.*` | Replace object name |
| `import in.rcard.yaes.CatsAccumulate.*` | `import in.rcard.yaes.cats.accumulate.*` | Replace object name |
| `import in.rcard.yaes.syntax.catsIO.*` | `import in.rcard.yaes.syntax.catseffect.given` | Replace object name, use `.given` |
| `import in.rcard.yaes.instances.RaiseInstances` | `import in.rcard.yaes.instances.raise.given` | Use object, not trait |
| `import in.rcard.yaes.all.given; import in.rcard.yaes.all.*` | Granular imports preferred | See import patterns above |

### Migration Strategy

**No deprecation period - clean break**
- Simpler codebase
- Clear structure
- Right time to establish conventions (module is relatively new)

**Version bump:**
- This is a breaking change → bump to 0.10.0

**Documentation updates needed:**
- README.md with new import patterns
- Scaladoc updates for all changed objects
- Migration guide in release notes

## Benefits

### 1. Consistency with Cats Ecosystem

**Before:**
```scala
import in.rcard.yaes.Cats.*
import in.rcard.yaes.CatsValidated.*
```

**After:**
```scala
import in.rcard.yaes.interop.catseffect.*
import in.rcard.yaes.cats.validated.*
```

Aligns with Cats patterns like `cats.effect.std.*`, `cats.data.*`

### 2. Clear Separation of Concerns

- **`interop.*`** = Bidirectional conversions (integration code)
- **`cats.*`** = YAES utilities enhanced by Cats types
- **`instances.*`** = Typeclass instances
- **`syntax.*`** = Extension methods

Each package has a clear purpose.

### 3. Granular Control

Users import only what they need:
```scala
// Just IO conversion
import in.rcard.yaes.interop.catseffect.blockingIO

// Just extension method
import in.rcard.yaes.syntax.catseffect.given

// Just MonadError instance
import in.rcard.yaes.instances.raise.given
```

### 4. Better Discoverability

- Package names signal intent (`interop` = integration, `cats` = utilities)
- Object names match primary types (`validated`, `accumulate`)
- Syntax matches object names (`syntax.catseffect` for `interop.catseffect`)

### 5. Maintains YAES Philosophy

- Keeps `.value` naming for consistency with YAES patterns
- Extension methods follow established YAES conventions
- Doesn't sacrifice YAES idioms for Cats conventions

## Implementation Checklist

### Phase 1: Package Restructuring
- [ ] Create `in.rcard.yaes.interop` package
- [ ] Create `in.rcard.yaes.cats` package
- [ ] Move and rename `Cats.scala` → `interop/catseffect.scala`
- [ ] Move and rename `CatsValidated.scala` → `cats/validated.scala`
- [ ] Move and rename `CatsAccumulate.scala` → `cats/accumulate.scala`

### Phase 2: Instances Refactoring
- [ ] Convert `instances/RaiseInstances.scala` trait to object pattern
- [ ] Update imports in client code

### Phase 3: Syntax Refactoring
- [ ] Rename `syntax/catsIO.scala` → `syntax/catseffect.scala`
- [ ] Update trait name `CatsIOSyntax` → `CatsEffectSyntax`
- [ ] Update `syntax/all.scala` to extend all syntax traits

### Phase 4: Unified Imports (Optional)
- [ ] Update `all.scala` to export from new locations
- [ ] Add documentation recommending granular imports

### Phase 5: Testing
- [ ] Update all test imports
- [ ] Verify all tests pass
- [ ] Add tests for new import patterns

### Phase 6: Documentation
- [ ] Update README.md with new import patterns
- [ ] Update Scaladoc for all changed objects
- [ ] Add migration guide
- [ ] Update examples in code comments

### Phase 7: Build & Publish
- [ ] Verify compilation
- [ ] Update version to 0.10.0
- [ ] Publish to Maven Central
- [ ] Tag release with migration notes

## Open Questions

None - design is complete and approved.

## References

- Cats Effect source: https://github.com/typelevel/cats-effect
- fs2 interop patterns: https://github.com/typelevel/fs2
- Cats organization: https://github.com/typelevel/cats
- YAES conventions: Existing codebase patterns
