package io.yaes.unsafe

import io.yaes.JvmUnscoped
import io.yaes.Unscoped

/** Grants the [[io.yaes.Unscoped]] capability.
  *
  * Unlike every other handler in λÆS, this one does NOT contain what it grants: work spawned via
  * [[io.yaes.Unscoped.spawn]] inside `block` keeps running after this method returns, and after
  * every enclosing scope -- including this one -- has exited. Every other handler in the library
  * (`Async.run`, `Resource.run`, `Raise.either`, ...) bounds the effect it grants to the lifetime
  * of the block it runs; `allowUnscoped` deliberately does not, because that is the entire point of
  * the [[io.yaes.Unscoped]] effect.
  *
  * There is no `Unscoped.run`. Importing this function from `io.yaes.unsafe` is the only way to
  * obtain the [[io.yaes.Unscoped]] capability, so every authorization site in a codebase is
  * greppable (`grep -rn "io.yaes.unsafe"`) and visible in a diff.
  *
  * Example:
  * {{{
  * import io.yaes.unsafe.allowUnscoped
  * import io.yaes.{Async, Unscoped}
  *
  * allowUnscoped {
  *   Async.run {
  *     val strand = Unscoped.spawn {
  *       sendTelemetry() // keeps running even after Async.run and allowUnscoped both return
  *     }
  *     strand.onComplete(_ => println("telemetry sent"))
  *   }
  * }
  * }}}
  *
  * @param block
  *   the computation to run with the [[io.yaes.Unscoped]] capability granted
  * @tparam A
  *   the result type of the computation
  * @return
  *   the result of `block`
  */
def allowUnscoped[A](block: Unscoped ?=> A): A = block(using JvmUnscoped)
