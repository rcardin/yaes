package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}

import in.rcard.yaes.Var.*

class VarSpec extends AnyFlatSpec with Matchers {

  "The Var effect (isolated)" should "return the initial value with get" in {
    val result = Var.runIsolated(42) {
      get[Int]
    }
    result shouldBe 42
  }

  it should "return the old value on set and update the state" in {
    val (oldValue, newValue) = Var.runIsolated("foo") {
      val old = set[String]("bar")
      val current = get[String]
      (old, current)
    }
    oldValue shouldBe "foo"
    newValue shouldBe "bar"
  }

  it should "apply update function and return the new state" in {
    val result = Var.runIsolated(10) {
      update[Int](_ + 5)
    }
    result shouldBe 15
  }

  it should "compose multiple operations correctly" in {
    val finalState = Var.runIsolated(0) {
      set(5)                    // state: 0 -> 5
      update[Int](_ * 3)        // state: 5 -> 15
      update[Int](_ - 4)        // state: 15 -> 11
      get[Int]
    }
    finalState shouldBe 11
  }

  it should "handle interleaved get, set, and update on a List[String]" in {
    val (afterSet, afterUpdate, afterGet) = Var.runIsolated(List.empty[String]) {
      val old    = set[List[String]](List("first"))
      val upd    = update[List[String]](_ :+ "second")
      val cur    = get[List[String]]
      (old, upd, cur)
    }
    afterSet shouldBe Nil
    afterUpdate shouldBe List("first", "second")
    afterGet shouldBe List("first", "second")
  }

  it should "isolate state per thread in runIsolated" in {
    val latch   = new CountDownLatch(2)
    val results = new ConcurrentLinkedQueue[String]()

    Var.runIsolated("init") {
      new Thread(() => {
        set("A"); results.add(get[String]); latch.countDown()
      }).start()
      new Thread(() => {
        set("B"); results.add(get[String]); latch.countDown()
      }).start()

      latch.await()
      val threadResults = List(results.poll(), results.poll()).sorted
      val mainState      = get[String]

      threadResults should contain allOf ("A", "B")
      mainState shouldBe "init"
    }
  }

  "The Var effect (shared)" should "share state between threads in runShared" in {
    val latch   = new CountDownLatch(2)
    val results = new ConcurrentLinkedQueue[Int]()

    Var.runShared(0) {
      new Thread(() => {
        update[Int](_ + 1);
        results.add(get[Int]);
        latch.countDown()
      }).start()

      new Thread(() => {
        update[Int](_ + 2);
        results.add(get[Int]);
        latch.countDown()
      }).start()

      latch.await()
      val values = List(results.poll(), results.poll()).sorted

      values shouldNot be (empty)
    }
  }
}
