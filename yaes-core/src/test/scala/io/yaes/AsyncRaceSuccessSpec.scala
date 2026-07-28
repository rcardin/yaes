package io.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.TimeLimits
import org.scalatest.concurrent.Signaler
import org.scalatest.concurrent.ThreadSignaler
import org.scalatest.time.Seconds
import org.scalatest.time.Span

import scala.concurrent.duration.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AsyncRaceSuccessSpec extends AnyFlatSpec with Matchers with TimeLimits {

  /** Interrupts the test thread when a `failAfter` limit expires.
    *
    * The default `Signaler` is `DoNotSignal`, which only reports the timeout once the block returns
    * on its own. A regression in `raceSuccess` (e.g. a swallowed `InterruptedException`, see
    * CRITICAL finding 2) would park a forked fiber forever and `Async.run`'s `join()` would never
    * return, hanging the whole suite instead of failing it. Interrupting the thread unwinds that
    * `join()`, whose `close()` then cancels the still-parked fiber, turning the regression into a
    * test failure instead of an unbounded hang.
    */
  private given Signaler = ThreadSignaler

  /** Upper bound for every test in this spec. Generous enough to absorb CI jitter, since the
    * guarded blocks only ever sleep a few seconds at most.
    */
  private val unblockTimeLimit: Span = Span(30, Seconds)

  "Async.raceSuccess" should "return the fast success and cancel the slow branch" in failAfter(
    unblockTimeLimit
  ) {
    val actualQueue  = new ConcurrentLinkedQueue[String]()
    val actualResult = Async.run {
      Async.raceSuccess(
        {
          Async.delay(100.millis)
          actualQueue.add("fast")
          42
        }, {
          Async.delay(2.seconds)
          actualQueue.add("slow")
          43
        }
      )
    }

    actualResult shouldBe 42
    // Give the loser a bounded chance to run (it should not, since it was cancelled).
    Thread.sleep(300)
    actualQueue.toArray should contain theSameElementsInOrderAs List("fast")
  }

  it should "return the fast success and cancel the slow branch (block2 wins)" in failAfter(
    unblockTimeLimit
  ) {
    val actualQueue  = new ConcurrentLinkedQueue[String]()
    val actualResult = Async.run {
      Async.raceSuccess(
        {
          Async.delay(2.seconds)
          actualQueue.add("slow")
          43
        }, {
          Async.delay(100.millis)
          actualQueue.add("fast")
          42
        }
      )
    }

    actualResult shouldBe 42
    Thread.sleep(300)
    actualQueue.toArray should contain theSameElementsInOrderAs List("fast")
  }

  it should "return the slow success and ignore the fast failure (plain exception)" in failAfter(
    unblockTimeLimit
  ) {
    val actualQueue  = new ConcurrentLinkedQueue[String]()
    val actualResult = Async.run {
      Async.raceSuccess(
        {
          Async.delay(100.millis)
          actualQueue.add("fast-failure")
          throw new RuntimeException("boom")
        }, {
          Async.delay(500.millis)
          actualQueue.add("slow-success")
          43
        }
      )
    }

    actualResult shouldBe 43
    actualQueue.toArray should contain theSameElementsInOrderAs List("fast-failure", "slow-success")
  }

  it should "return the slow success and ignore the fast failure (block2 fails fast)" in failAfter(
    unblockTimeLimit
  ) {
    val actualQueue  = new ConcurrentLinkedQueue[String]()
    val actualResult = Async.run {
      Async.raceSuccess(
        {
          Async.delay(500.millis)
          actualQueue.add("slow-success")
          43
        }, {
          Async.delay(100.millis)
          actualQueue.add("fast-failure")
          throw new RuntimeException("boom")
        }
      )
    }

    actualResult shouldBe 43
    actualQueue.toArray should contain theSameElementsInOrderAs List("fast-failure", "slow-success")
  }

  it should "return the slow success and ignore the fast failure (Raise.raise)" in failAfter(
    unblockTimeLimit
  ) {
    val actualQueue                = new ConcurrentLinkedQueue[String]()
    val actualResult: Int | String = Raise.run {
      Async.run {
        Async.raceSuccess(
          {
            Async.delay(100.millis)
            actualQueue.add("fast-failure")
            Raise.raise("Error")
          }, {
            Async.delay(500.millis)
            actualQueue.add("slow-success")
            43
          }
        )
      }
    }

    actualResult shouldBe 43
    actualQueue.toArray should contain theSameElementsInOrderAs List("fast-failure", "slow-success")
  }

  it should "fail when both branches fail, surfacing the last observed failure" in failAfter(
    unblockTimeLimit
  ) {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val tryResult   = scala.util.Try {
      Async.run {
        Async.raceSuccess[Int, Int](
          {
            Async.delay(100.millis)
            actualQueue.add("fast-failure")
            throw new RuntimeException("first failure")
          }, {
            Async.delay(500.millis)
            actualQueue.add("slow-failure")
            throw new RuntimeException("second failure")
          }
        )
      }
    }

    tryResult.isFailure shouldBe true
    tryResult.failed.get shouldBe a[RuntimeException]
    tryResult.failed.get.getMessage shouldBe "second failure"
    actualQueue.toArray should contain theSameElementsInOrderAs List("fast-failure", "slow-failure")
  }

  it should "fail when both branches fail, surfacing the last observed failure (block2 fails first)" in failAfter(
    unblockTimeLimit
  ) {
    val actualQueue = new ConcurrentLinkedQueue[String]()
    val tryResult   = scala.util.Try {
      Async.run {
        Async.raceSuccess[Int, Int](
          {
            Async.delay(500.millis)
            actualQueue.add("slow-failure")
            throw new RuntimeException("second failure")
          }, {
            Async.delay(100.millis)
            actualQueue.add("fast-failure")
            throw new RuntimeException("first failure")
          }
        )
      }
    }

    tryResult.isFailure shouldBe true
    tryResult.failed.get shouldBe a[RuntimeException]
    tryResult.failed.get.getMessage shouldBe "second failure"
    actualQueue.toArray should contain theSameElementsInOrderAs List("fast-failure", "slow-failure")
  }

  it should "fail through Raise.run when both branches fail with a raised error" in failAfter(
    unblockTimeLimit
  ) {
    val actualResult = Raise.run {
      Async.run {
        Async.raceSuccess[Int, Int](
          {
            Async.delay(100.millis)
            Raise.raise("first error")
          }, {
            Async.delay(500.millis)
            Raise.raise("second error")
          }
        )
      }
    }

    actualResult shouldBe "second error"
  }

  it should "fail through Raise.run when both branches fail with a raised error (block2 raises first)" in failAfter(
    unblockTimeLimit
  ) {
    val actualResult = Raise.run {
      Async.run {
        Async.raceSuccess[Int, Int](
          {
            Async.delay(500.millis)
            Raise.raise("second error")
          }, {
            Async.delay(100.millis)
            Raise.raise("first error")
          }
        )
      }
    }

    actualResult shouldBe "second error"
  }

  it should "observably cancel the loser once the winner succeeds" in failAfter(unblockTimeLimit) {
    val loserRan     = new AtomicBoolean(false)
    val latch        = new CountDownLatch(1)
    val actualResult = Async.run {
      Async.raceSuccess(
        {
          42
        }, {
          latch.await(2, TimeUnit.SECONDS)
          Async.delay(1.second)
          loserRan.set(true)
          43
        }
      )
    }

    actualResult shouldBe 42
    latch.countDown()
    Thread.sleep(300)
    loserRan.get() shouldBe false
  }

  it should "cancel the loser's own forked children once the winner succeeds, not just the loser itself" in failAfter(
    unblockTimeLimit
  ) {
    // CRITICAL finding 2: `captured` used to catch `InterruptedException`, clearing the interrupt
    // flag and preventing `JvmAsync.fork`'s cancellation path from ever running. That made the
    // loser's own forked child (here, a 4-second sleep) run to completion instead of being
    // cancelled, even though the loser itself was supposed to be cancelled immediately.
    val start        = java.lang.System.nanoTime()
    val actualResult = Async.run {
      Async.raceSuccess(
        {
          Async.delay(50.millis)
          1
        }, {
          Async.fork {
            Async.delay(4.seconds)
            0
          }
          new CountDownLatch(1).await()
          2
        }
      )
    }
    val elapsedMillis = (java.lang.System.nanoTime() - start) / 1000000L

    actualResult shouldBe 1
    // `race` resolves the equivalent program in well under a second; a regression that fails to
    // cancel the loser's child would take the full 4+ seconds.
    elapsedMillis should be < 2000L
  }

  it should "surface a branch's genuine failure rather than a bare cancellation when an unrelated sibling poisons the scope while raceSuccess is waiting on the loser" in failAfter(
    unblockTimeLimit
  ) {
    // CRITICAL finding 3: while raceSuccess waits on the surviving branch after the other one
    // failed, an unrelated sibling fiber can fail and shut down the enclosing scope, cancelling
    // the branch raceSuccess is waiting on. That cancellation must never be reported as if it
    // were the branch's own genuine failure (nor must it silently hang forever).
    val tryResult = scala.util.Try {
      Async.run {
        Async.fork {
          Async.delay(200.millis)
          throw new RuntimeException("sibling boom")
        }
        Async.raceSuccess[Int, Int](
          {
            Async.delay(50.millis)
            throw new RuntimeException("A fails")
          }, {
            Async.delay(5.seconds)
            2
          }
        )
      }
    }

    tryResult.isFailure shouldBe true
    // The genuine failure observed on the other branch must win over the meaningless
    // cancellation caused by the unrelated sibling.
    tryResult.failed.get.getMessage shouldBe "A fails"
  }

  it should "not let a raced branch's internally forked failing child poison the enclosing scope (failing branch is the loser)" in failAfter(
    unblockTimeLimit
  ) {
    // CRITICAL finding 1, shape 1: the failing branch's own top-level code never throws directly
    // — `Async.fork` returns a `Fiber` without rethrowing. The failure only surfaces later, when
    // the branch's own nested scope is joined deep inside `JvmAsync.fork`/`attemptFork`, a path a
    // naive try/catch around the branch body cannot see.
    val actualResult = Async.run {
      Async.raceSuccess(
        {
          Async.fork {
            Async.delay(10.millis)
            throw new RuntimeException("inner boom")
          }
          Async.delay(200.millis)
          1
        }, {
          Async.delay(500.millis)
          2
        }
      )
    }

    actualResult shouldBe 2
  }

  it should "not let a raced branch's internally forked failing child poison the enclosing scope (failing branch is the winner's loser)" in failAfter(
    unblockTimeLimit
  ) {
    // CRITICAL finding 1, shape 2: the loser is cancelled before it can even observe its own
    // child's failure; that must not poison the scope the winner's value is returned into.
    val actualResult = Async.run {
      Async.raceSuccess(
        {
          Async.delay(50.millis)
          1
        }, {
          Async.fork {
            Async.delay(10.millis)
            throw new RuntimeException("loser inner boom")
          }
          Async.delay(2.seconds)
          2
        }
      )
    }

    actualResult shouldBe 1
  }

  it should "not let Async.par used inside a losing branch poison the enclosing scope when one par side fails" in failAfter(
    unblockTimeLimit
  ) {
    // CRITICAL finding 1, shape 3: same root cause, different vector — the nested failure comes
    // from `Async.par`'s own `racePair` machinery instead of a bare `Async.fork`.
    val actualResult = Async.run {
      Async.raceSuccess(
        {
          Async.par(
            {
              throw new RuntimeException("par boom")
            }, {
              Async.delay(100.millis)
              "unused"
            }
          )
          1
        }, {
          Async.delay(600.millis)
          2
        }
      )
    }

    actualResult shouldBe 2
  }

  it should "genuinely race both branches rather than always favoring one when they tie" in failAfter(
    unblockTimeLimit
  ) {
    // LOW finding 9: `actualResult should (be(1) or be(2))` on a single run passes for an
    // implementation that unconditionally returns block1 (or ignores the race entirely).
    // Running the tie repeatedly and requiring BOTH outcomes to show up proves raceSuccess is
    // actually racing, not hardcoding a winner.
    //
    // Round-2 MEDIUM finding: with instant literal blocks (no work at all), the "race" is
    // decided by fork/callback-registration ordering rather than genuine concurrency, which is
    // heavily biased towards block2 (measured ~88% block2 across repeated trials, making this
    // assertion flaky at roughly 12% of CI runs). Giving both branches identical, tiny real work
    // (an `Async.delay`) lets them genuinely tie instead, which measured at ~54%/46% across 400
    // runs — no observed flake.
    val observedResults = (1 to 50).map { _ =>
      Async.run {
        Async.raceSuccess(
          {
            Async.delay(20.millis)
            1
          }, {
            Async.delay(20.millis)
            2
          }
        )
      }
    }.toSet

    observedResults should contain(1)
    observedResults should contain(2)
  }

  it should "propagate a fatal error thrown by a branch instead of swallowing it" in failAfter(
    unblockTimeLimit
  ) {
    // Round-2 MEDIUM finding: the comment that used to sit here claimed a permanent test was
    // impossible because any forked fiber that lets an Error escape terminates its virtual
    // thread uncaught, aborting the whole shared test JVM. That claim is false: the
    // OutOfMemoryError below is thrown by `JvmAsync.forkImpl`'s own `case t: Throwable` arm
    // (the same fatal-error path both `JvmAsync.fork` and `JvmAsync.attemptFork` delegate to), which
    // rethrows it into the parent scope. It is an ordinary Java exception at that point, catchable
    // with a plain try/catch around `Async.run`, and the JVM (and this suite) keeps running
    // afterwards — this very test executing is itself proof of that.
    var caught: Throwable = null
    try {
      Async.run {
        Async.raceSuccess(
          {
            Async.delay(50.millis)
            throw new OutOfMemoryError("fake oom")
          }, {
            Async.delay(500.millis)
            2
          }
        )
      }
    } catch {
      case t: Throwable => caught = t
    }

    caught shouldBe an[OutOfMemoryError]
    caught.getMessage shouldBe "fake oom"
  }

  it should "not let a cancellation wrapped in a rethrown exception mask a genuine failure from the other branch" in failAfter(
    unblockTimeLimit
  ) {
    // Round-2 MEDIUM finding: residual of round-1 finding 3. When an unrelated sibling poisons
    // the enclosing scope while raceSuccess waits on the surviving branch, that branch is
    // interrupted. If the branch's own code catches `InterruptedException` and rethrows it
    // wrapped in a plain exception (a common Java interop idiom), `attemptFork`'s `NonFatal` arm
    // used to have no way to recognise that as a cancellation, so it overwrote the genuine
    // failure already observed on the other branch.
    val tryResult = scala.util.Try {
      Async.run {
        Async.fork {
          Async.delay(200.millis)
          throw new RuntimeException("sibling boom")
        }
        Async.raceSuccess[Int, Int](
          {
            Async.delay(50.millis)
            throw new RuntimeException("A fails")
          }, {
            try {
              new CountDownLatch(1).await()
              2
            } catch {
              case _: InterruptedException => throw new RuntimeException("wrapped")
            }
          }
        )
      }
    }

    tryResult.isFailure shouldBe true
    // The genuine failure observed on branch A must win over the cancellation that branch B's
    // own code chose to disguise as an ordinary `RuntimeException`.
    tryResult.failed.get.getMessage shouldBe "A fails"
  }

  it should "use the Async capability it is given rather than hard-coding the JVM backend" in failAfter(
    unblockTimeLimit
  ) {
    // HIGH finding (round 2): raceSuccess used to call the static `JvmAsync.attemptFork`
    // directly, bypassing the `async` capability parameter entirely. Any non-`JvmAsync`
    // implementation of `Async.Unsafe` NPE'd (`JvmAsync.attemptFork` reads a `ThreadLocal` only
    // `JvmAsync.run`/`fork` ever populate). `Async.race` already routes through `async.fork`;
    // `raceSuccess` must route through `async.attemptFork` the same way, so a custom `fork`
    // implementation is what actually runs.
    val fake: Async = new Async.Unsafe {
      def delay(d: Duration): Unit                     = ()
      def fork[A](name: String)(block: => A): Fiber[A] =
        throw new UnsupportedOperationException("custom fork should have been used")
    }

    a[UnsupportedOperationException] should be thrownBy {
      Async.raceSuccess(1, 2)(using fake)
    }
  }

  it should "cancel only the branch that actually lost when both branches succeed" in failAfter(
    unblockTimeLimit
  ) {
    // Review finding: `onSuccess` used to call `loser.cancel()` unconditionally, even when its
    // `winner.complete(value)` returned false because the other branch had already won. On a
    // double success that second callback cancels the branch that actually won, interrupting it
    // while it is still running its own completion and scope teardown. Only the branch that wins
    // the `complete` race may cancel the other one.
    //
    // Both fibers below complete the instant their callback is registered, which reproduces the
    // double success deterministically: `fiber1` wins, then `fiber2`'s callback finds the winner
    // already completed.
    val actualCancellations = new ConcurrentLinkedQueue[String]()

    class ImmediateFiber[A](name: String, result: A) extends Fiber[A] {
      override def value(using async: Async): Raise[Async.Cancelled] ?=> A = result
      override def join()(using async: Async): Unit                        = ()
      override def cancel()(using async: Async): Unit                      = {
        actualCancellations.add(name)
        ()
      }
      override def onComplete(fn: A => Unit)(using async: Async): Unit             = fn(result)
      override def onFailure(handler: Throwable => Unit)(using async: Async): Unit = ()
      override private[yaes] def unsafeValue(using async: Async): A                = result
    }

    val fake: Async = new Async.Unsafe {
      def delay(d: Duration): Unit                                     = ()
      def fork[A](name: String)(block: => A): Fiber[A]                 = attemptFork(name)(block)
      override def attemptFork[A](name: String)(block: => A): Fiber[A] =
        new ImmediateFiber[A](name, block)
    }

    val actualResult = Async.raceSuccess(1, 2)(using fake)

    actualResult shouldBe 1
    actualCancellations.toArray should contain theSameElementsInOrderAs List("fiber2")
  }
}
