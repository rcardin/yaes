---
title: Escaping Structured Concurrency with Unscoped
description: Start genuine fire-and-forget background work with the Unscoped effect, deliberately outside the Async effect and its structured-concurrency guarantee.
sidebar:
  label: Unscoped Effect
  order: 1
---

Every concurrency primitive in the [Async effect](/learn/5-concurrency/) — `Async.fork`, `Async.run`, `Async.unsupervised` — binds the work it starts to a structured scope, so nothing they start can outlive that scope. `Unscoped` is the deliberate, and only, exception in λÆS. It exists for one narrow case: background work that must keep running after its spawning scope has already exited, such as best-effort telemetry or logging.

Because that guarantee is exactly what `Async` promises everywhere else, `Unscoped` is not part of `Async` at all. It is its own effect, gated behind a separate, clearly named import, so an escape hatch never hides inside an otherwise trustworthy capability.

---

## Unscoped Effect

### Granting the Capability

Unlike every other handler in λÆS, obtaining `Unscoped` does not run or contain anything: there is no `Unscoped.run`. The only way to obtain the capability is importing `allowUnscoped` from `io.yaes.unsafe`:

```scala
import io.yaes.unsafe.allowUnscoped

allowUnscoped {
  // Unscoped is available here
}
```

This is intentional. Every other handler in the library contains what it grants — `Async.run` waits for its fibers, `Resource.run` releases its resources, `Raise.either` catches its own errors. `allowUnscoped` cannot make that promise, since the entire point of `Unscoped` is work that outlives the block that started it. Segregating the grant behind a dedicated function name means every place a codebase opts into that risk is a single grep away — as long as you grep for the function, not the package:

```bash
grep -rn "allowUnscoped" --include="*.scala"
```

Grepping for `io.yaes.unsafe` instead undercounts: `unsafe` is a subpackage of `io.yaes`, so a file with a wildcard `import io.yaes.*` can call `unsafe.allowUnscoped { ... }` without the literal string `io.yaes.unsafe` ever appearing.

Grant it once, near the top of an application:

```scala
import io.yaes.unsafe.allowUnscoped

object MyApp extends YaesApp {
  override def run: (Sync, Output, Input, Random, Clock, System) ?=> Unit =
    allowUnscoped {
      Async.run { server.serve() }
    }
}
```

and thread `Unscoped` through `using` clauses to whatever call site actually needs it:

```scala
def handleRequest(req: Request)(using Unscoped): Response = {
  Unscoped.spawn { sendTelemetry(req) }
  Response.ok
}
```

The `using Unscoped` in a signature is the deliverable: every layer between `main` and the spawn site declares, in its own type, that it may start work outliving its caller.

### Spawning Detached Work

`Unscoped.spawn` starts a computation on its own background daemon virtual thread, completely outside any structured concurrency scope, and returns immediately without waiting for it:

```scala
import io.yaes.unsafe.allowUnscoped

allowUnscoped {
  val strand = Unscoped.spawn {
    sendTelemetry() // keeps running even after this call returns
  }
  strand.onComplete(_ => println("telemetry sent"))
  strand.onFailure(err => println(s"telemetry failed: ${err.getMessage}"))
  "done"
} // returns "done" immediately; the strand is not waited on
```

`spawn` gives the computation its own, freshly created `Async` capability with its own structured scope, so a failure inside it is contained there: it is captured for observers but never rethrown into the caller, so it can neither fail nor cancel the spawning scope. Because it runs on a virtual thread, the background thread is a daemon by construction — spawned work left running never keeps the JVM alive on its own.

Unlike every `Async` operation, `spawn` requires no ambient `Async` capability at all — only `Unscoped`. A call site that holds `Sync` but no `Async` can still spawn detached work:

```scala
def handleRequest(req: Request)(using Sync, Unscoped): Response = {
  Unscoped.spawn { sendTelemetry(req) } // no Async needed here
  Response.ok
}
```

The returned `Strand` is fire-and-forget: it has no `join` or `cancel`, only `onComplete` and `onFailure` to observe the eventual outcome. Those callbacks are plain functions — they do not require an ambient `Async` — because the spawning scope may already be gone by the time they run. Registering an observer after the spawned computation has already finished still fires it immediately.

:::caution
**`Unscoped.spawn` escapes structured concurrency.** Unlike `Async.fork`, `Async.run`, and `Async.unsupervised`, the computation it starts is not cancelled when the spawning scope exits, its failure is never surfaced to that scope, and there is no way to join it from the caller. Reach for `Async.fork` (inside `Async.run` or `Async.unsupervised`) for anything that should still respect structured concurrency; reach for `Unscoped.spawn` only for genuine fire-and-forget background work, such as best-effort telemetry or logging, that must outlive the scope that started it.
:::

:::caution
**The spawned block must be self-terminating.** `spawn` offers no `cancel`. A block that never completes — for example one that calls `Async.never` with nothing left to eventually interrupt it, since the fresh scope `spawn` opens for it has nothing to cancel it either — leaves the returned `Strand` permanently unsettled (no observer ever fires) and leaks its parked background thread for the lifetime of the JVM, with no way to reclaim it.
:::

### Why Not Part of Async?

A capability whose guarantee has an exception must be read alongside its documentation rather than reasoned about from its type. Keeping `Unscoped` separate restores `Async`'s unqualified guarantee: a function declared `(using Async): Unit` can once again be trusted to bound, fail into, and cancel with the caller's scope, no exceptions. Fire-and-forget background work is real and occasionally necessary, but it deserves its own name and its own capability, not a corner case bolted onto the one abstraction whose entire value is not having any.

There is also no drain policy, registry, or deadline for spawned work: waiting on it, even with a bound, would reintroduce the coupling `Unscoped` exists to avoid. A spawned block that must eventually stop should apply its own timeout to whatever it is doing, exactly like any other piece of self-terminating code.
