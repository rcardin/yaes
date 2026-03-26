package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class WriterSpec extends AnyFlatSpec with Matchers {

  // --- Basic write ---

  "Writer.write" should "append a single value" in {
    val (log, result) = Writer.run[String, Int] {
      Writer.write("hello")
      42
    }

    log shouldBe Vector("hello")
    result shouldBe 42
  }

  it should "append multiple values in order" in {
    val (log, _) = Writer.run[String, Unit] {
      Writer.write("a")
      Writer.write("b")
      Writer.write("c")
    }

    log shouldBe Vector("a", "b", "c")
  }

  // --- Basic writeAll ---

  "Writer.writeAll" should "append multiple values at once" in {
    val (log, _) = Writer.run[Int, Unit] {
      Writer.writeAll(List(1, 2, 3))
    }

    log shouldBe Vector(1, 2, 3)
  }

  it should "handle empty iterables" in {
    val (log, _) = Writer.run[Int, Unit] {
      Writer.writeAll(List.empty[Int])
    }

    log shouldBe Vector.empty
  }

  it should "interleave with write correctly" in {
    val (log, _) = Writer.run[Int, Unit] {
      Writer.write(1)
      Writer.writeAll(List(2, 3))
      Writer.write(4)
    }

    log shouldBe Vector(1, 2, 3, 4)
  }

  // --- Empty handler ---

  "Writer.run" should "return empty log when no writes occur" in {
    val (log, result) = Writer.run[String, Int] {
      40 + 2
    }

    log shouldBe Vector.empty
    result shouldBe 42
  }

  // --- Capture ---

  "Writer.capture" should "capture writes from a block and return them alongside the result" in {
    val (outerLog, (innerLog, innerResult)) = Writer.run[String, (Vector[String], Int)] {
      Writer.write("before")
      val captured = Writer.capture[String, Int] {
        Writer.write("inside")
        99
      }
      Writer.write("after")
      captured
    }

    innerLog shouldBe Vector("inside")
    innerResult shouldBe 99
    outerLog shouldBe Vector("before", "inside", "after")
  }

  it should "forward captured writes to outer scope" in {
    val (outerLog, _) = Writer.run[String, Unit] {
      Writer.capture[String, Unit] {
        Writer.write("captured")
      }
    }

    outerLog shouldBe Vector("captured")
  }

  it should "handle empty capture block" in {
    val (outerLog, (innerLog, innerResult)) = Writer.run[String, (Vector[String], Int)] {
      Writer.write("outer")
      val captured = Writer.capture[String, Int] { 42 }
      captured
    }

    innerLog shouldBe Vector.empty
    innerResult shouldBe 42
    outerLog shouldBe Vector("outer")
  }

  it should "handle nested captures" in {
    val (outerLog, result) = Writer.run[String, (Vector[String], (Vector[String], Int))] {
      Writer.write("outer")
      Writer.capture[String, (Vector[String], Int)] {
        Writer.write("middle")
        Writer.capture[String, Int] {
          Writer.write("inner")
          42
        }
      }
    }

    val (middleLog, (innerLog, innerResult)) = result
    innerResult shouldBe 42
    innerLog shouldBe Vector("inner")
    middleLog shouldBe Vector("middle", "inner")
    outerLog shouldBe Vector("outer", "middle", "inner")
  }

  // --- Composition with Raise ---

  "Writer" should "compose with Raise effect" in {
    val (log, result) = Writer.run[String, Either[String, Int]] {
      Writer.write("before")
      val either = Raise.either[String, Int] {
        Writer.write("inside-raise")
        Raise.raise("error")
        Writer.write("after-raise")
        42
      }
      Writer.write("after")
      either
    }

    result shouldBe Left("error")
    log shouldBe Vector("before", "inside-raise", "after")
  }

  // --- Composition with State ---

  it should "compose with State effect" in {
    val (state, (log, result)) = State.run(0) {
      Writer.run[String, Int] {
        Writer.write("start")
        State.update[Int](_ + 1)
        Writer.write(s"count=${State.get[Int]}")
        State.get[Int]
      }
    }

    state shouldBe 1
    log shouldBe Vector("start", "count=1")
    result shouldBe 1
  }

  // --- Large writes ---

  it should "handle large number of writes" in {
    val n = 10_000
    val (log, _) = Writer.run[Int, Unit] {
      (1 to n).foreach(i => Writer.write(i))
    }

    log.size shouldBe n
    log shouldBe (1 to n).toVector
  }

  // --- Different types ---

  it should "work with different value types" in {
    val (log, _) = Writer.run[Double, Unit] {
      Writer.write(1.0)
      Writer.write(2.5)
    }

    log shouldBe Vector(1.0, 2.5)
  }

  // --- Infix type alias ---

  "writes infix type" should "allow tracking writes in a more concise way" in {
    def computation: Int writes String = {
      Writer.write("log entry")
      42
    }

    val (log, result) = Writer.run[String, Int] {
      computation
    }

    log shouldBe Vector("log entry")
    result shouldBe 42
  }
}
