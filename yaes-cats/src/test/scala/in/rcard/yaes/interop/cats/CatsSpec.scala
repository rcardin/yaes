package in.rcard.yaes.interop.cats

import in.rcard.yaes.{IO => YaesIO}
import cats.effect.{IO => CatsIO}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext.Implicits.global // Needed for YaesIO.run
import cats.effect.unsafe.implicits.global as catsRuntime // Needed for CatsIO.unsafeRunSync
import scala.concurrent.Await
import scala.concurrent.duration._

class CatsSpec extends AnyFlatSpec with Matchers {

  "Cats" should "convert simple YAES IO to Cats Effect IO" in {
    val yaesProgram: YaesIO ?=> Int = YaesIO {
      42
    }

    val catsIO = Cats.run(yaesProgram)
    val result = catsIO.unsafeRunSync()

    result shouldBe 42
  }

  it should "convert Cats Effect IO to YAES IO" in {
    val catsIO = CatsIO.pure(42)

    val result = YaesIO.run {
      catsIO.value
    }

    Await.result(result, 5.seconds) shouldBe 42
  }

  it should "preserve errors when converting YAES IO to Cats IO" in {
    val yaesProgram: YaesIO ?=> Int = YaesIO {
      throw new RuntimeException("YAES error")
    }

    val catsIO = Cats.run(yaesProgram)

    val exception = intercept[RuntimeException] {
      catsIO.unsafeRunSync()
    }
    exception.getMessage shouldBe "YAES error"
  }

  it should "preserve errors when converting Cats IO to YAES IO" in {
    val catsIO = CatsIO.raiseError[Int](new RuntimeException("Cats error"))

    val result = YaesIO.run {
      catsIO.value
    }

    val exception = intercept[RuntimeException] {
      Await.result(result, 5.seconds)
    }
    exception.getMessage shouldBe "Cats error"
  }

  it should "handle side effects in YAES to Cats conversion" in {
    var sideEffect = 0

    val yaesProgram: YaesIO ?=> Unit = YaesIO {
      sideEffect += 1
    }

    val catsIO = Cats.run(yaesProgram)
    catsIO.unsafeRunSync()

    sideEffect shouldBe 1
  }

  it should "handle side effects in Cats to YAES conversion" in {
    var sideEffect = 0

    val catsIO = CatsIO {
      sideEffect += 1
    }

    val result = YaesIO.run {
      catsIO.value
    }

    Await.result(result, 5.seconds)
    sideEffect shouldBe 1
  }

  it should "support timeout when converting Cats IO to YAES IO" in {
    val slowCatsIO = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)

    val result = YaesIO.run {
      slowCatsIO.value(100.millis)
    }

    assertThrows[java.util.concurrent.TimeoutException] {
      Await.result(result, 5.seconds)
    }
  }

  it should "compose multiple conversions" in {
    val originalYaes: YaesIO ?=> Int = YaesIO { 21 }

    // YAES -> Cats -> YAES -> Cats
    val catsIO = Cats.run(originalYaes).map(_ * 2)

    val result = YaesIO.run {
      catsIO.value
    }

    Await.result(result, 5.seconds) shouldBe 42
  }

  it should "handle complex computations with both side effects and values" in {
    var counter = 0

    val yaesProgram: YaesIO ?=> String = YaesIO {
      counter += 1
      s"Count: $counter"
    }

    val catsIO = Cats.run(yaesProgram)
      .flatMap { msg =>
        CatsIO {
          counter += 10
          s"$msg, Updated: $counter"
        }
      }

    val result = YaesIO.run {
      catsIO.value
    }

    Await.result(result, 5.seconds) shouldBe "Count: 1, Updated: 11"
    counter shouldBe 11
  }

  it should "defer execution until Cats IO is run (referential transparency)" in {
    var sideEffect = 0

    val yaesProgram: YaesIO ?=> Int = YaesIO {
      sideEffect += 1
      42
    }

    // Creating the Cats IO should NOT execute the YAES program
    val catsIO = Cats.run(yaesProgram)
    sideEffect shouldBe 0 // Side effect should not have happened yet!

    // Only when we run the Cats IO should the side effect occur
    val result = catsIO.unsafeRunSync()
    result shouldBe 42
    sideEffect shouldBe 1
  }

  it should "allow multiple executions of the same Cats IO (referential transparency)" in {
    var counter = 0

    val yaesProgram: YaesIO ?=> Int = YaesIO {
      counter += 1
      counter
    }

    val catsIO = Cats.run(yaesProgram)

    // Each execution should increment the counter
    val result1 = catsIO.unsafeRunSync()
    result1 shouldBe 1

    val result2 = catsIO.unsafeRunSync()
    result2 shouldBe 2

    val result3 = catsIO.unsafeRunSync()
    result3 shouldBe 3
  }

  "Extension methods" should "convert Cats Effect IO to YAES IO using fluent syntax" in {
    val catsIO = CatsIO.pure(42)

    val result = YaesIO.run {
      catsIO.value  // Extension method
    }

    Await.result(result, 5.seconds) shouldBe 42
  }

  it should "support timeout using fluent syntax" in {
    val slowCatsIO = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)

    val result = YaesIO.run {
      slowCatsIO.value(100.millis)  // Extension method
    }

    assertThrows[java.util.concurrent.TimeoutException] {
      Await.result(result, 5.seconds)
    }
  }

  it should "allow fluent chaining of Cats operations before conversion" in {
    val result = YaesIO.run {
      CatsIO.pure(21)
        .map(_ * 2)
        .flatMap(x => CatsIO.pure(x + 1))
        .value  // Fluent conversion at the end
    }

    Await.result(result, 5.seconds) shouldBe 43
  }

  it should "preserve errors when using extension method" in {
    val catsIO = CatsIO.raiseError[Int](new RuntimeException("Extension error"))

    val result = YaesIO.run {
      catsIO.value  // Extension method
    }

    val exception = intercept[RuntimeException] {
      Await.result(result, 5.seconds)
    }
    exception.getMessage shouldBe "Extension error"
  }

  it should "handle side effects correctly with extension method" in {
    var sideEffect = 0

    val catsIO = CatsIO {
      sideEffect += 1
      sideEffect
    }

    val result = YaesIO.run {
      catsIO.value  // Extension method
    }

    Await.result(result, 5.seconds) shouldBe 1
    sideEffect shouldBe 1
  }

  it should "work with complex Cats Effect compositions using extension method" in {
    var counter = 0

    val catsIO = for {
      _ <- CatsIO { counter += 1 }
      _ <- CatsIO { counter += 10 }
      result <- CatsIO.pure(counter)
    } yield result

    val result = YaesIO.run {
      catsIO.value  // Extension method
    }

    Await.result(result, 5.seconds) shouldBe 11
    counter shouldBe 11
  }
}
