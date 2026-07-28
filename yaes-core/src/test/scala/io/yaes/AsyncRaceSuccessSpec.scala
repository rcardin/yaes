package io.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AsyncRaceSuccessSpec extends AnyFlatSpec with Matchers {

  "Async.raceSuccess" should "return the fast success and cancel the slow branch" in {
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

  it should "return the slow success and ignore the fast failure (plain exception)" in {
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

  it should "return the slow success and ignore the fast failure (Raise.raise)" in {
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

  it should "fail when both branches fail, surfacing the last observed failure" in {
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

  it should "fail through Raise.run when both branches fail with a raised error" in {
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

  it should "observably cancel the loser once the winner succeeds" in {
    val loserRan     = new java.util.concurrent.atomic.AtomicBoolean(false)
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

  it should "not throw a FailedException from Async.run when one branch failed but the other succeeded" in {
    // Regression guard: a naive fork of a throwing block would poison the enclosing
    // StructuredTaskScope and make Async.run throw, even though raceSuccess itself
    // should succeed because the other branch completed successfully.
    noException should be thrownBy {
      Async.run {
        Async.raceSuccess(
          {
            Async.delay(100.millis)
            throw new RuntimeException("ignored failure")
          }, {
            Async.delay(300.millis)
            43
          }
        )
      }
    }
  }

  it should "return either winner when both branches succeed quickly" in {
    val actualResult = Async.run {
      Async.raceSuccess(
        {
          1
        }, {
          2
        }
      )
    }

    actualResult should (be(1) or be(2))
  }
}
