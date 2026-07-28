package io.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AsyncParTraverseLimitSpec extends AnyFlatSpec with Matchers {

  /** Tracks how many concurrent invocations of a body are in flight at once, returning the
    * high-water mark observed. Used to verify that `parTraverseLimit` never runs more than
    * `concurrency` invocations of `f` at the same time.
    */
  private def trackConcurrency(running: AtomicInteger, maxSeen: AtomicInteger): Unit = {
    val current = running.incrementAndGet()
    maxSeen.updateAndGet(prev => math.max(prev, current))
  }

  "Async.parTraverseLimit" should "process all elements and return results in input order" in {
    val result = Async.run {
      Async.parTraverseLimit(List(1, 2, 3, 4, 5), concurrency = 2)(x => x * 2)
    }

    result shouldBe Seq(2, 4, 6, 8, 10)
  }

  it should "never run more invocations of f at once than the given concurrency" in {
    val running = new AtomicInteger(0)
    val maxSeen = new AtomicInteger(0)

    val result = Async.run {
      Async.parTraverseLimit((1 to 6).toList, concurrency = 2) { x =>
        trackConcurrency(running, maxSeen)
        Async.delay(300.millis)
        running.decrementAndGet()
        x
      }
    }

    result shouldBe (1 to 6).toList
    maxSeen.get() should be <= 2
    // With 6 items and generous overlap, the bound should actually be reached.
    maxSeen.get() shouldBe 2
  }

  it should "return results in input order regardless of completion order" in {
    val result = Async.run {
      Async.parTraverseLimit(Seq(3, 1, 2), concurrency = 2) { x =>
        Async.delay((x * 150).millis)
        x
      }
    }

    result shouldBe Seq(3, 1, 2)
  }

  it should "behave like unbounded parTraverse when concurrency >= items.size" in {
    val executionOrder = new ConcurrentLinkedQueue[Int]()

    val result = Async.run {
      Async.parTraverseLimit(Seq(1, 2, 3), concurrency = 3) { x =>
        // Element 1 is slowest, element 3 is fastest.
        Async.delay((500 * (4 - x)).millis)
        executionOrder.add(x)
        x * 10
      }
    }

    result shouldBe Seq(10, 20, 30)
    // Fastest element finishes first, proving all three ran concurrently rather than
    // being serialized behind a concurrency bound.
    executionOrder.toArray should contain theSameElementsInOrderAs List(3, 2, 1)
  }

  it should "clamp a zero concurrency to 1, running fully sequentially, without throwing" in {
    val running = new AtomicInteger(0)
    val maxSeen = new AtomicInteger(0)

    val result = Async.run {
      Async.parTraverseLimit(List(1, 2, 3, 4), concurrency = 0) { x =>
        trackConcurrency(running, maxSeen)
        Async.delay(20.millis)
        running.decrementAndGet()
        x
      }
    }

    result shouldBe Seq(1, 2, 3, 4)
    maxSeen.get() shouldBe 1
  }

  it should "clamp a negative concurrency to 1, running fully sequentially, without throwing" in {
    val running = new AtomicInteger(0)
    val maxSeen = new AtomicInteger(0)

    val result = Async.run {
      Async.parTraverseLimit(List(1, 2, 3), concurrency = -5) { x =>
        trackConcurrency(running, maxSeen)
        Async.delay(20.millis)
        running.decrementAndGet()
        x
      }
    }

    result shouldBe Seq(1, 2, 3)
    maxSeen.get() shouldBe 1
  }

  it should "cancel remaining and still-queued fibers when one computation fails with an exception" in {
    val entered = new ConcurrentLinkedQueue[Int]()

    val tryResult = scala.util.Try {
      Async.run {
        // concurrency = 1 forces elements 2, 3 and 4 to queue behind element 1 waiting for a
        // worker; element 1 fails quickly, and the queued ones must never run f. Entry is
        // recorded on the very first line of f, before any delay, so even a queued element
        // that merely starts f (without completing it) would still be caught here.
        Async.parTraverseLimit(Seq(1, 2, 3, 4), concurrency = 1) { x =>
          entered.add(x)
          if (x == 1) {
            Async.delay(50.millis)
            throw new RuntimeException("boom")
          }
          Async.delay(1.second)
          x
        }
      }
    }

    tryResult.isFailure shouldBe true
    tryResult.failed.get shouldBe a[RuntimeException]
    tryResult.failed.get.getMessage shouldBe "boom"
    entered.toArray should contain only 1
  }

  it should "stop workers from claiming new elements once a failure has been observed, " +
    "under concurrency > 1" in {
      // Reproduces the exact scenario that exposed the bug: with only one worker
      // (concurrency = 1), the stop check was already correct, so that shape never exercised
      // the race between a failing worker and its still-looping siblings. This uses a large
      // number of essentially free elements so that, without the abort flag, sibling workers
      // can race through many more of them in the brief window between the failure and the
      // cooperative interrupt actually landing.
      val itemsCount = 20000
      val entries    = new ConcurrentLinkedQueue[(Int, Long)]()
      val failedAtNs = new AtomicLong(-1L)

      val tryResult = scala.util.Try {
        Async.run {
          Async.parTraverseLimit((1 to itemsCount).toList, concurrency = 4) { x =>
            entries.add((x, java.lang.System.nanoTime()))
            if (x == 1) {
              failedAtNs.set(java.lang.System.nanoTime())
              throw new RuntimeException("boom")
            }
            x
          }
        }
      }

      tryResult.isFailure shouldBe true
      val cutoff = failedAtNs.get()
      cutoff should be > 0L

      // A worker that has already read the abort flag as false, a few nanoseconds before it
      // flips, is still allowed to enter f once more for the element it had already claimed;
      // the guarantee is only that no *unclaimed* element starts. That leaves room for some
      // stragglers within the bounded window between the failure and the abort flag actually
      // being observed, but nowhere near the pre-fix bug, where sibling workers kept looping
      // until the much slower interrupt-based cancellation eventually landed and drained the
      // whole collection. This threshold discriminates "bounded window" from "drained the
      // whole collection"; it is not meant to pin an exact count.
      val enteredAfterFailure = entries.asScala.count { case (x, ts) => x != 1 && ts > cutoff }
      enteredAfterFailure should be < 200
    }

  it should "stop queued elements even when f swallows InterruptedException, " +
    "the standard Java interop idiom" in {
      // A worker that catches its own InterruptedException and just swallows it (returning
      // normally, as if nothing happened) clears its own interrupt flag; relying solely on
      // `Thread.currentThread().isInterrupted()` to stop the loop would then never see a stop
      // signal again for that worker, so it would race through every remaining element. This
      // needs concurrency > 1: elements 2, 3 and 4 are claimed and already sleeping when
      // element 1 fails, get interrupted out of that sleep by the scope's own auto-cancel,
      // swallow it, and must still stop instead of going on to claim elements 5 onward.
      val entered = new ConcurrentLinkedQueue[Int]()

      val tryResult = scala.util.Try {
        Async.run {
          Async.parTraverseLimit((1 to 30).toList, concurrency = 4) { x =>
            entered.add(x)
            if (x == 1) {
              Async.delay(50.millis)
              throw new RuntimeException("boom")
            } else if (x <= 4) {
              try {
                Async.delay(1.second)
              } catch {
                case _: InterruptedException =>
                // Swallow and clear the interrupt flag, then return normally, mirroring a
                // common (if questionable) Java interop idiom.
              }
              x
            } else {
              // Nothing left to interrupt here: if a worker whose flag got cleared above is
              // the one that goes on to claim this element, it runs unimpeded.
              x
            }
          }
        }
      }

      tryResult.isFailure shouldBe true
      // Only the four elements already claimed by the time element 1 failed may ever have
      // entered f; none of elements 5 through 30 should, regardless of what happens to the
      // interrupt flag inside f.
      entered.size should be <= 4
    }

  it should "throw rather than silently return a Seq of nulls when a sibling fiber fails in the same scope" in {
    // Reproduces the second, distinct hole: a sibling fiber (not part of the traversal at all)
    // fails inside the same Async.run scope. Its failure cancels the whole scope, interrupting
    // the parTraverseLimit workers without any of them throwing (JvmFiber.join swallows
    // CancellationException), so the owner must detect the incomplete traversal itself rather
    // than silently handing back a Seq padded with unwritten (null) slots. `observed` is set
    // from inside the block, before it returns and before the enclosing scope's own join()
    // later surfaces the sibling's failure, so it captures whatever parTraverseLimit itself
    // handed back, independent of that later, unrelated failure.
    val observed = new java.util.concurrent.atomic.AtomicReference[AnyRef](null)

    val tryResult = scala.util.Try {
      Async.run {
        Async.fork {
          Async.delay(20.millis)
          throw new RuntimeException("sibling failure")
        }
        val r = Async.parTraverseLimit((1 to 6).toList, concurrency = 2) { x =>
          Async.delay(1.second)
          x
        }
        observed.set(r)
        r
      }
    }

    tryResult.isFailure shouldBe true
    observed.get() shouldBe null
  }

  it should "propagate Raise errors and cancel remaining fibers" in {
    val executed = new ConcurrentLinkedQueue[Int]()

    val result = Raise.run {
      Async.run {
        Async.parTraverseLimit(Seq(1, 2, 3), concurrency = 2) { x =>
          if (x == 2) {
            Async.delay(50.millis)
            executed.add(x)
            Raise.raise("Error from element 2")
          }
          Async.delay(1.second)
          executed.add(x)
          x
        }
      }
    }

    result shouldBe "Error from element 2"
    executed.toArray should contain only 2
  }

  it should "return an empty sequence for an empty input without invoking f" in {
    val invocations = new AtomicInteger(0)

    val result = Async.run {
      Async.parTraverseLimit(Seq.empty[Int], concurrency = 4) { x =>
        invocations.incrementAndGet()
        x * 2
      }
    }

    result shouldBe Seq.empty
    invocations.get() shouldBe 0
  }

  it should "handle a single element" in {
    val result = Async.run {
      Async.parTraverseLimit(Seq(42), concurrency = 4)(x => x.toString)
    }

    result shouldBe Seq("42")
  }

  it should "work with different input and output types" in {
    val result = Async.run {
      Async.parTraverseLimit(Seq("hello", "world", "test"), concurrency = 2)(_.length)
    }

    result shouldBe Seq(5, 5, 4)
  }

  it should "handle a large collection under a tight concurrency bound" in {
    val items   = (1 to 50).toList
    val running = new AtomicInteger(0)
    val maxSeen = new AtomicInteger(0)

    val result = Async.run {
      Async.parTraverseLimit(items, concurrency = 5) { x =>
        trackConcurrency(running, maxSeen)
        // A generous delay between the increment and the decrement gives every worker a
        // real chance to overlap with the others; without it, the whole body can complete
        // faster than the scheduler starts the next worker, making the high-water mark
        // vacuously low regardless of whether the bound is actually enforced.
        Async.delay(100.millis)
        running.decrementAndGet()
        x * x
      }
    }

    result shouldBe items.map(x => x * x)
    // With 50 items and only 5 workers, the bound must actually be reached, not merely
    // never exceeded.
    maxSeen.get() shouldBe 5
  }

  it should "not overflow and behave like unbounded parTraverse when concurrency = Int.MaxValue" in {
    val result = Async.run {
      Async.parTraverseLimit(List(1, 2, 3, 4, 5), concurrency = Int.MaxValue)(x => x * 2)
    }

    result shouldBe Seq(2, 4, 6, 8, 10)
  }

  it should "clamp concurrency = Int.MinValue to 1 without overflowing or throwing" in {
    val running = new AtomicInteger(0)
    val maxSeen = new AtomicInteger(0)

    val result = Async.run {
      Async.parTraverseLimit(List(1, 2, 3), concurrency = Int.MinValue) { x =>
        trackConcurrency(running, maxSeen)
        Async.delay(20.millis)
        running.decrementAndGet()
        x
      }
    }

    result shouldBe Seq(1, 2, 3)
    maxSeen.get() shouldBe 1
  }

  it should "fail and cancel remaining workers when every element fails" in {
    val tryResult = scala.util.Try {
      Async.run {
        Async.parTraverseLimit(Seq(1, 2, 3), concurrency = 2) { x =>
          Async.delay((x * 10).millis)
          throw new RuntimeException(s"boom-$x")
        }
      }
    }

    tryResult.isFailure shouldBe true
    tryResult.failed.get shouldBe a[RuntimeException]
  }

  it should "return an empty sequence for empty input even with a negative concurrency" in {
    val invocations = new AtomicInteger(0)

    val result = Async.run {
      Async.parTraverseLimit(Seq.empty[Int], concurrency = -3) { x =>
        invocations.incrementAndGet()
        x
      }
    }

    result shouldBe Seq.empty
    invocations.get() shouldBe 0
  }

  it should "work with Raise effect for typed error handling" in {
    val result: Either[String, Seq[Int]] = Raise.either {
      Async.run {
        Async.parTraverseLimit(Seq(2, 3, 6), concurrency = 2) { x =>
          if (x % 2 != 0) Raise.raise(s"$x is odd")
          x / 2
        }
      }
    }

    result shouldBe Left("3 is odd")
  }

  it should "raise the first error when multiple elements would fail (typed Raise)" in {
    val result: Either[String, Seq[Int]] = Raise.either {
      Async.run {
        Async.parTraverseLimit(Seq(1, 3, 5), concurrency = 3) { x =>
          Async.delay((50 * x).millis)
          Raise.raise(s"$x failed")
          x
        }
      }
    }

    // The fastest failing element (element 1) should win.
    result shouldBe Left("1 failed")
  }

  it should "not serialize the traversal when items is a lazily-evaluated Seq such as LazyList" in {
    val forced             = new AtomicInteger(0)
    val running            = new AtomicInteger(0)
    val maxSeen            = new AtomicInteger(0)
    val forcedWhenFStarted = new AtomicInteger(-1)
    val fStartedOnce       = new java.util.concurrent.atomic.AtomicBoolean(false)

    // A LazyList that records how many of its elements have actually been forced (evaluated)
    // as it is traversed. `items` must be materialized once, up front, before any worker is
    // forked, so by the time `f` starts running, every element should already be forced.
    def lazyItems(n: Int): LazyList[Int] =
      LazyList.range(0, n).map { x =>
        forced.incrementAndGet()
        x
      }

    val result = Async.run {
      Async.parTraverseLimit(lazyItems(6), concurrency = 3) { x =>
        // Captured only on the very first entry into f, before any element completes, so a
        // lazy element-by-element re-walk (which would also force all 6 eventually, just not
        // yet at this point) cannot pass this assertion the way a check at the end could.
        if (fStartedOnce.compareAndSet(false, true)) forcedWhenFStarted.set(forced.get())
        trackConcurrency(running, maxSeen)
        // If parTraverseLimit had serialized the traversal by re-walking a lazy `Seq` element
        // by element instead of materializing it up front, workers would only ever see one
        // element forced ahead of them at a time, and this bound would never be reached.
        Async.delay(50.millis)
        running.decrementAndGet()
        x * x
      }
    }

    result shouldBe (0 until 6).map(x => x * x)
    forced.get() shouldBe 6
    // Pins the "materialized once, up front, before any worker is forked" claim: every
    // element was already forced by the time the very first worker entered f.
    forcedWhenFStarted.get() shouldBe 6
    maxSeen.get() shouldBe 3
  }

  it should "work when B is Unit" in {
    val sideEffects = new ConcurrentLinkedQueue[Int]()

    val result = Async.run {
      Async.parTraverseLimit(Seq(1, 2, 3), concurrency = 2) { x =>
        sideEffects.add(x)
        (): Unit
      }
    }

    result shouldBe Seq((), (), ())
    sideEffects.toArray should contain theSameElementsAs List(1, 2, 3)
  }

  it should "round-trip a null result from f at the position it was produced" in {
    val result = Async.run {
      Async.parTraverseLimit(Seq("v1", null, "v3"), concurrency = 2) { x =>
        x
      }
    }

    result shouldBe Seq("v1", null, "v3")
  }
}
