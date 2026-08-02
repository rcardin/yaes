package io.userland

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Proves, from a package other than `io.yaes`, that `JvmUnscoped` -- the ready-made
  * `io.yaes.Unscoped` backend instance that `allowUnscoped` hands out -- cannot be named or used to
  * mint the capability by ordinary downstream code.
  *
  * This has to live in its own file, in a package that is not `io.yaes`, standing in for arbitrary
  * downstream code. `ScalaTest`'s `assertDoesNotCompile` typechecks its argument as a block, so a
  * snippet with its own `package` clause cannot be passed to it inline. A probe compiled as part of
  * `io.yaes.UnscopedSpec` itself would trivially see `private[yaes]` members regardless of whether
  * the guard actually works, since that file is literally inside the `io.yaes` package -- only a
  * probe compiled from a genuinely different package proves anything about what is reachable from
  * outside the library.
  */
class UnscopedCapabilityLeakSpec extends AnyFlatSpec with Matchers {

  // `assertDoesNotCompile`/`assertCompiles` typecheck their argument strings at macro-expansion
  // time, but those trees are discarded before zinc's ExtractDependencies phase runs, so zinc
  // records no source dependency from this spec to `io.yaes` from the string literals above and
  // below. Without a real, non-string reference somewhere in this file, an incremental local
  // build could keep this spec's stale class file green even after `JvmUnscoped`'s visibility is
  // loosened, since zinc would see no reason to recompile it. This reference gives zinc that edge
  // to follow. Do not delete it as dead code.
  private val grantIsReachable: Int = io.yaes.unsafe.allowUnscoped { 42 }

  "JvmUnscoped" should "not be referenceable by name from outside io.yaes" in {
    assertDoesNotCompile("io.yaes.JvmUnscoped")
  }

  it should "not be usable to mint the Unscoped capability from outside io.yaes" in {
    assertDoesNotCompile("object P { given io.yaes.Unscoped = io.yaes.JvmUnscoped }")
  }

  it should "still let ordinary downstream code obtain Unscoped via allowUnscoped" in {
    // Positive control: without this, the two assertDoesNotCompile calls above could pass
    // vacuously for the wrong reason (e.g. a typo or an unrelated compile error breaking basic
    // name resolution in this package), and this file would silently stop proving anything.
    assertCompiles("io.yaes.unsafe.allowUnscoped { 42 }")
    grantIsReachable shouldBe 42
  }
}
