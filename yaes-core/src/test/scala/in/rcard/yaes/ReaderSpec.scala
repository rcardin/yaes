package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ReaderSpec extends AnyFlatSpec with Matchers {

  case class Config(maxRetries: Int, timeout: Int)

  // --- Basic read ---

  "Reader.read" should "return the provided value" in {
    val result = Reader.run(42) {
      Reader.read[Int]
    }

    result shouldBe 42
  }

  it should "return a case class value" in {
    val config = Config(3, 5000)
    val result = Reader.run(config) {
      Reader.read[Config]
    }

    result shouldBe Config(3, 5000)
  }

  it should "return different types" in {
    val result = Reader.run("hello") {
      Reader.read[String]
    }

    result shouldBe "hello"
  }

  it should "allow field access on the result" in {
    val result = Reader.run(Config(3, 5000)) {
      Reader.read[Config].maxRetries
    }

    result shouldBe 3
  }

  // --- local ---

  "Reader.local" should "override value for inner block" in {
    val result = Reader.run(42) {
      Reader.local[Int, Int](_ + 10) {
        Reader.read[Int]
      }
    }

    result shouldBe 52
  }

  it should "restore value after inner block" in {
    val result = Reader.run(42) {
      val before = Reader.read[Int]
      val during = Reader.local[Int, Int](_ * 2) {
        Reader.read[Int]
      }
      val after = Reader.read[Int]
      (before, during, after)
    }

    result shouldBe (42, 84, 42)
  }

  it should "work with case class copy" in {
    val result = Reader.run(Config(3, 5000)) {
      val before = Reader.read[Config].maxRetries
      val during = Reader.local[Config, Int](_.copy(maxRetries = 10)) {
        Reader.read[Config].maxRetries
      }
      val after = Reader.read[Config].maxRetries
      (before, during, after)
    }

    result shouldBe (3, 10, 3)
  }

  it should "support nested local scopes" in {
    val result = Reader.run(1) {
      val a = Reader.read[Int]
      val b = Reader.local[Int, (Int, Int, Int)](_ + 10) {
        val inner1 = Reader.read[Int]
        val inner2 = Reader.local[Int, Int](_ + 100) {
          Reader.read[Int]
        }
        val inner1After = Reader.read[Int]
        (inner1, inner2, inner1After)
      }
      val c = Reader.read[Int]
      (a, b, c)
    }

    result shouldBe (1, (11, 111, 11), 1)
  }

  it should "be a no-op with identity function" in {
    val result = Reader.run(42) {
      Reader.local[Int, Int](identity) {
        Reader.read[Int]
      }
    }

    result shouldBe 42
  }

  // --- Nested run handlers ---

  "Reader.run" should "allow nested handlers where inner shadows outer" in {
    val result = Reader.run(42) {
      val outer = Reader.read[Int]
      val inner = Reader.run(99) {
        Reader.read[Int]
      }
      val outerAgain = Reader.read[Int]
      (outer, inner, outerAgain)
    }

    result shouldBe (42, 99, 42)
  }

  it should "return A directly (not a tuple)" in {
    val result: String = Reader.run("hello") {
      Reader.read[String] + " world"
    }

    result shouldBe "hello world"
  }

  // --- Infix type alias ---

  "reads infix type" should "work as a context function type" in {
    def getRetries: Int reads Config =
      Reader.read[Config].maxRetries

    val result = Reader.run(Config(3, 5000)) {
      getRetries
    }

    result shouldBe 3
  }
}
