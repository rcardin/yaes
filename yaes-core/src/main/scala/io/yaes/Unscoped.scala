package io.yaes

import java.util as ju
import scala.util.control.NonFatal

import ju.concurrent.CompletableFuture

/** The capability to start work that outlives every structured concurrency scope.
  *
  * Obtained only by importing [[io.yaes.unsafe.allowUnscoped]]; there is deliberately no
  * `Unscoped.run`. See the [[Unscoped]] companion object for the full design rationale.
  *
  * Example:
  * {{{
  * import io.yaes.unsafe.allowUnscoped
  *
  * allowUnscoped {
  *   Unscoped.spawn {
  *     sendTelemetry() // keeps running even after this call returns
  *   }
  * }
  * }}}
  */
type Unscoped = Unscoped.Unsafe

/** JVM implementation of [[Unscoped.Strand]], backed by a plain
  * [[java.util.concurrent.CompletableFuture]].
  *
  * Unlike [[JvmFiber]], there is no `forkedThread` handshake and no cancellation: the underlying
  * daemon thread is not owned by any `StructuredTaskScope`, so there is nothing structured to
  * cancel it from.
  *
  * @param promise
  *   the CompletableFuture holding the detached computation's result
  * @tparam A
  *   the type of value produced by the detached computation
  */
class JvmStrand[A](private val promise: CompletableFuture[A]) extends Unscoped.Strand[A] {

  override def onComplete(fn: A => Unit): Unit =
    promise.thenAccept(result => fn(result))

  override def onFailure(handler: Throwable => Unit): Unit =
    promise.whenComplete { (_, ex) =>
      if (ex != null) handler(ex)
    }
}

/** JVM implementation of [[Unscoped]], starting each spawned computation on its own daemon virtual
  * thread.
  *
  * `private[yaes]`, not merely undocumented: this is the ready-made backend instance that
  * [[io.yaes.unsafe.allowUnscoped]] hands out as the [[Unscoped]] capability. If it were public,
  * anyone could write `given Unscoped = io.yaes.JvmUnscoped` and obtain the capability without
  * ever calling `allowUnscoped`, defeating the entire point of gating the grant behind that
  * function (every authorization site must be greppable via `grep -rn "allowUnscoped"` -- not
  * `grep -rn "io.yaes.unsafe"`, since a wildcard `import io.yaes.*` lets `unsafe.allowUnscoped` be
  * called without that literal string ever appearing at the call site).
  *
  * `private[yaes]` blocks ordinary downstream code, which is the threat model this defends
  * against, but it is defence in depth rather than an absolute guarantee: Scala's qualified-private
  * is a compile-time package-path check with no module boundary, so code in a wholly separate
  * compilation unit that declares its own `package io.yaes` can still see this member. Stateless,
  * so it is an `object` rather than a `class`: there is only ever one JVM backend.
  */
private[yaes] object JvmUnscoped extends Unscoped.Unsafe {

  override def spawn[A](block: Async ?=> A): Unscoped.Strand[A] = {
    val promise = CompletableFuture[A]()
    Thread
      .ofVirtual()
      .name(s"yaes-unscoped-${java.util.UUID.randomUUID()}")
      .start(() => {
        try {
          val result = Async.run(block)
          promise.complete(result)
        } catch {
          case ie: InterruptedException =>
            // Treated as an ordinary failure, not a cancellation: this thread belongs to no
            // StructuredTaskScope whose cancellation semantics `promise.cancel(true)` could
            // mirror (that would also destroy the original exception, contradicting onFailure's
            // documented contract of receiving the exception the computation failed with).
            // Deliberately NOT restoring the interrupt status here (unlike standard JVM practice
            // for a thread that keeps running): `CompletableFuture` runs any dependents already
            // registered via `onComplete`/`onFailure` synchronously, on this very thread, as part
            // of `completeExceptionally` below. Setting the interrupt flag first would make an
            // observer's own interruptible blocking calls fail spuriously. This thread is about to
            // terminate right after, so no later code could ever observe the flag anyway -- there
            // is nothing for restoring it to protect.
            promise.completeExceptionally(ie)
          case NonFatal(t) =>
            // Contained, not rethrown: `block`'s failure must never propagate anywhere, since
            // this thread belongs to no structured scope that could observe it.
            promise.completeExceptionally(t)
          case fatal: Throwable =>
            // Everything NonFatal does not match: genuine fatal errors (VirtualMachineError,
            // LinkageError, ...) as well as scala.util.control.ControlThrowable (e.g. Raise's
            // own private AccumulationError, see Raise.scala). Still recorded for observers, but
            // also rethrown so it reaches this thread's default uncaught-exception handler,
            // exactly like an equivalent failure would on any other unmanaged thread. This
            // thread is not part of any StructuredTaskScope, so rethrowing here cannot reach the
            // spawning scope or caller either way. Mirrors JvmAsync.forkImpl's identical
            // treatment of this case.
            promise.completeExceptionally(fatal)
            throw fatal
        }
      })
    new JvmStrand[A](promise)
  }
}

/** Companion object for [[Unscoped]], the capability to start work that outlives every structured
  * scope.
  *
  * Every other operation in λÆS — [[Async.fork]], [[Async.run]], [[Async.unsupervised]],
  * [[Resource.run]], [[Raise.either]] — contains what it grants: the work it starts is bound to the
  * handler that started it and cannot outlive it. `Unscoped` is the deliberate exception. There is
  * intentionally no `Unscoped.run`: the only way to obtain the [[Unscoped]] capability is importing
  * [[io.yaes.unsafe.allowUnscoped]], which makes every authorization site greppable and visible in
  * a diff (`grep -rn "allowUnscoped"` -- not `grep -rn "io.yaes.unsafe"`, since a wildcard
  * `import io.yaes.*` lets the call resolve without that literal substring ever appearing).
  *
  * Example:
  * {{{
  * import io.yaes.unsafe.allowUnscoped
  *
  * allowUnscoped {
  *   val strand = Unscoped.spawn {
  *     sendTelemetry() // keeps running even after this call returns
  *   }
  *   strand.onComplete(_ => println("telemetry sent"))
  *   strand.onFailure(err => println(s"telemetry failed: ${err.getMessage}"))
  * }
  * }}}
  */
object Unscoped {

  /** Starts `block` on its own background daemon virtual thread, completely outside any structured
    * concurrency scope.
    *
    * Every entry point on [[Async]] — [[Async.fork]], [[Async.run]], [[Async.unsupervised]] — binds
    * the computation it starts to a `StructuredTaskScope`, so it cannot outlive the scope that
    * started it. `spawn` is the deliberate exception: it starts `block` on a brand new daemon
    * virtual thread that belongs to no scope at all, gives that thread its own fresh,
    * self-contained [[Async]] capability (via an internal [[Async.run]]), and returns immediately
    * without waiting for it. A failure inside `block` is contained on that background thread; it is
    * captured for observers but never rethrown into the caller, so it can neither fail nor cancel
    * the spawning scope. The one exception is a genuinely fatal error (`VirtualMachineError`,
    * `LinkageError`, ...): it is still captured for observers first, but is then also rethrown on
    * the detached background thread itself, reaching that thread's default uncaught-exception
    * handler -- never the caller's scope either way, since that thread belongs to no structured
    * scope the caller could observe a rethrow through.
    *
    * Being a virtual thread, the background thread is a daemon by construction (the JVM's virtual
    * threads are always daemons), so a spawned computation left running never keeps the JVM alive
    * on its own.
    *
    * The returned [[Strand]] is fire-and-forget: it exposes no `join`/`cancel`, only
    * [[Strand.onComplete]] / [[Strand.onFailure]] to observe the eventual outcome.
    *
    * @note
    *   '''This escapes structured concurrency.''' The computation started by `spawn` is not
    *   cancelled when the spawning scope exits, its failure is never surfaced to that scope, and
    *   there is no way to join it from the caller. Reach for [[Async.fork]] (inside [[Async.run]]
    *   or [[Async.unsupervised]]) for anything that should still respect structured concurrency;
    *   reach for `spawn` only for genuine fire-and-forget background work (e.g. best-effort
    *   telemetry or logging) that must outlive the scope that started it. Unlike every `Async`
    *   operation, `spawn` requires no ambient [[Async]] capability at all: it requires only the
    *   [[Unscoped]] capability, obtained by importing [[io.yaes.unsafe.allowUnscoped]]. That
    *   capability is what marks a call site as one that may start work outliving its caller — the
    *   `block` it starts receives its own, entirely separate [[Async]] capability.
    * @note
    *   `block` must be self-terminating. `spawn` offers no `cancel`, so a block that never
    *   completes (for example one that calls [[Async.never]] without anything to eventually
    *   interrupt it, since the fresh scope `spawn` opens for `block` has nothing left to cancel it
    *   either) leaves the returned [[Strand]] permanently unsettled — no observer ever fires — and
    *   leaks its parked background thread for the lifetime of the JVM, with no way to reclaim it.
    * @note
    *   An `InterruptedException` thrown by `block` is treated as an ordinary failure, not a
    *   cancellation: it is reported to [[Strand.onFailure]] with its original type and message
    *   intact. The background thread's interrupt status is deliberately NOT restored: observers
    *   registered before completion run synchronously on this same, about-to-terminate thread, and
    *   setting the flag would make an observer's own interruptible blocking calls fail spuriously;
    *   nothing downstream ever gets a chance to observe the flag anyway. There is no structured
    *   scope here for a cancellation signal to have come from, so, unlike [[Async.fork]], there is
    *   nothing to distinguish it from any other failure.
    * @note
    *   Observer callbacks passed to [[Strand.onComplete]] / [[Strand.onFailure]] are plain
    *   `A => Unit` / `Throwable => Unit` functions: they do not receive an ambient [[Async]], since
    *   the spawning scope (and the [[Async]] it provided) may already be gone by the time they run.
    *   A callback that itself needs an [[Async]] capability can open its own with [[Async.run]] or
    *   [[Async.unsupervised]].
    * @note
    *   `block` runs inside its own, freshly opened [[Async.run]] scope, not bare. That scope
    *   applies [[Async.run]]'s usual rule: it waits for every fiber [[Async.fork]]ed inside
    *   `block`, joined or not, and if any of them fails, that failure wins over whatever `block`
    *   itself returned or threw. So `Unscoped.spawn { Async.fork { throw Boom() }; 42 }` settles
    *   the returned [[Strand]] exceptionally with `Boom()` -- the `42` is discarded -- even though
    *   `block` itself neither threw nor was cancelled. Fibers forked inside `block` should be
    *   joined (or their failure otherwise handled) before `block` returns, exactly as with a plain
    *   [[Async.run]] call.
    * @note
    *   A [[Raise]] capability captured from the spawning scope and then used inside `block` does
    *   not deliver its typed error at all: `Raise.raise` implements control flow via
    *   `scala.util.boundary`/`break`, and the `boundary` frame that captured capability closed over
    *   lives on the spawning thread's stack, not on this detached background thread. Using it here
    *   throws a bare `scala.util.boundary.Break` instead, which is delivered to
    *   [[Strand.onFailure]] like any other `NonFatal` failure rather than propagating the typed
    *   error. Open a fresh [[Raise]] handler inside `block` instead.
    *
    * Example:
    * {{{
    * import io.yaes.unsafe.allowUnscoped
    *
    * allowUnscoped {
    *   val strand = Unscoped.spawn {
    *     sendTelemetry() // keeps running even after this call returns
    *   }
    *   strand.onComplete(_ => println("telemetry sent"))
    *   strand.onFailure(err => println(s"telemetry failed: ${err.getMessage}"))
    *   "done"
    * } // returns "done" immediately; the spawned strand is not waited on
    * }}}
    *
    * @param block
    *   the computation to run detached; it receives its own freshly created [[Async]] capability,
    *   with its own structured scope, independent of the caller's
    * @param u
    *   the unscoped context; delegates to [[Unscoped.Unsafe.spawn]] for the actual spawning
    *   implementation
    * @tparam A
    *   the type of value produced by the detached computation
    * @return
    *   a [[Strand]] handle for attaching completion/failure observers; it offers no
    *   `join`/`cancel`, by design (see the note above)
    * @see
    *   [[Async.unsupervised]], [[Async.fork]] for the structured alternatives that remain bound to
    *   the spawning scope
    */
  def spawn[A](block: Async ?=> A)(using u: Unscoped): Strand[A] = u.spawn(block)

  /** A handle to a detached background computation started by [[Unscoped.spawn]].
    *
    * Unlike [[Fiber]], a `Strand` is not bound to any [[Async]] scope and offers no
    * `join`/`cancel`/`value`: the computation it represents already runs on its own background
    * daemon thread, independent of any `StructuredTaskScope`, so there is no scope left to join or
    * cancel it from. The only supported operation is attaching an observer that runs once the
    * detached computation completes, successfully or not.
    *
    * Example:
    * {{{
    * import io.yaes.unsafe.allowUnscoped
    *
    * allowUnscoped {
    *   val strand: Unscoped.Strand[Int] = Unscoped.spawn {
    *     42
    *   }
    *   strand.onComplete(value => println(s"got $value"))
    *   strand.onFailure(err => println(s"failed: ${err.getMessage}"))
    * }
    * }}}
    *
    * @tparam A
    *   the type of value produced by the detached computation
    */
  trait Strand[A] {

    /** Registers a callback to run once the detached computation completes successfully.
      *
      * The callback does not require an ambient [[Async]] capability. If the detached computation
      * has not completed yet, the callback runs on the detached background thread itself once it
      * does. If the computation has ALREADY completed successfully by the time this is called, the
      * callback instead runs synchronously and inline, on whatever thread called `onComplete` — so
      * a slow or blocking callback registered late blocks the registering thread until it returns.
      * Either way, the callback never runs on a thread of its own.
      *
      * An observer that throws is not propagated anywhere and does not prevent other observers
      * registered on the same handle from running: the exception is silently discarded. Since
      * observers are the only feedback channel for detached work, a throwing observer's failure is
      * otherwise invisible.
      *
      * @param result
      *   the callback function, receiving the computed value
      */
    def onComplete(result: A => Unit): Unit

    /** Registers a callback to run if the detached computation fails with an exception.
      *
      * As with [[onComplete]], the callback does not require an ambient [[Async]] capability, runs
      * either on the detached background thread or, if the computation has already failed,
      * synchronously and inline on the registering thread, and an observer that throws is silently
      * discarded without affecting other observers. It may also run well after the spawning scope
      * has already exited.
      *
      * @param handler
      *   the callback function, receiving the exception the computation failed with
      */
    def onFailure(handler: Throwable => Unit): Unit
  }

  /** The backend seam for [[Unscoped]].
    *
    * Example:
    * {{{
    * import io.yaes.unsafe.allowUnscoped
    *
    * allowUnscoped {
    *   Unscoped.spawn { 42 }
    * }
    * }}}
    */
  trait Unsafe {

    /** Starts `block` on its own background computation, detached from any structured concurrency
      * scope, and returns a handle for observing its eventual outcome.
      *
      * This is the backend seam behind [[Unscoped.spawn]]. The JVM backend (`JvmUnscoped`) starts
      * `block` on a brand new daemon virtual thread, gives it its own fresh [[Async]] capability
      * via an internal [[Async.run]], and reports its outcome on a plain
      * [[java.util.concurrent.CompletableFuture]] wrapped in a [[JvmStrand]]. `block`'s failure is
      * always contained here (captured for observers, never rethrown to any caller), since the
      * background computation this method starts belongs to no structured scope that could observe
      * a rethrow. The one exception is a genuinely fatal error: it is still captured for observers
      * first, but is then also rethrown on the detached background thread itself, so it still
      * reaches that thread's own default uncaught-exception handler -- it just never reaches a
      * caller, since there is no caller scope to reach.
      *
      * This method has no default implementation; every backend must supply its own, so that
      * spawning genuinely detached work is not hard-coded to a single backend's concurrency
      * primitive. Implement this by starting `block` on whatever background execution primitive the
      * backend uses for concurrent work (a daemon thread, an unmanaged fiber, ...), running it
      * fully outside of any structured scope the backend maintains, and reporting its outcome
      * through a [[Unscoped.Strand]] implementation appropriate to the backend.
      *
      * @param block
      *   the computation to run detached; it receives its own freshly created [[Async]] capability
      * @tparam A
      *   the type of value produced by the detached computation
      * @return
      *   a [[Unscoped.Strand]] handle for attaching completion/failure observers
      */
    def spawn[A](block: Async ?=> A): Strand[A]
  }
}
