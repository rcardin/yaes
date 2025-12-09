package in.rcard.yaes

import in.rcard.yaes.{IO => YaesIO, Raise}
import in.rcard.yaes.interop.catseffect
import in.rcard.yaes.syntax.catseffect.given
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

    val catsIO = catseffect.blockingIO(yaesProgram)
    val result = catsIO.unsafeRunSync()

    result shouldBe 42
  }

  it should "convert Cats Effect IO to YAES IO" in {
    val catsIO = CatsIO.pure(42)

    val result = YaesIO.run {
      Raise.either {
        catsIO.value
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome shouldBe Right(42)
  }

  it should "preserve errors when converting YAES IO to Cats IO" in {
    val yaesProgram: YaesIO ?=> Int = YaesIO {
      throw new RuntimeException("YAES error")
    }

    val catsIO = catseffect.blockingIO(yaesProgram)

    val exception = intercept[RuntimeException] {
      catsIO.unsafeRunSync()
    }
    exception.getMessage shouldBe "YAES error"
  }

  it should "preserve errors when converting Cats IO to YAES IO" in {
    val catsIO = CatsIO.raiseError[Int](new RuntimeException("Cats error"))

    val result = YaesIO.run {
      Raise.either {
        catsIO.value
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome.isLeft shouldBe true
    outcome.left.get.getMessage shouldBe "Cats error"
  }

  it should "handle side effects in YAES to Cats conversion" in {
    var sideEffect = 0

    val yaesProgram: YaesIO ?=> Unit = YaesIO {
      sideEffect += 1
    }

    val catsIO = catseffect.blockingIO(yaesProgram)
    catsIO.unsafeRunSync()

    sideEffect shouldBe 1
  }

  it should "handle side effects in Cats to YAES conversion" in {
    var sideEffect = 0

    val catsIO = CatsIO {
      sideEffect += 1
    }

    val result = YaesIO.run {
      Raise.either {
        catsIO.value
      }
    }

    Await.result(result, 5.seconds)
    sideEffect shouldBe 1
  }

  it should "support timeout when converting Cats IO to YAES IO" in {
    val slowCatsIO = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)

    val result = YaesIO.run {
      Raise.either {
        slowCatsIO.value(100.millis)
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome.isLeft shouldBe true
    outcome.left.get shouldBe a[java.util.concurrent.TimeoutException]
  }

  it should "compose multiple conversions" in {
    val originalYaes: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO { 21 }

    // YAES -> Cats -> YAES -> Cats
    val catsIO = catseffect.blockingIO(originalYaes).map(_ * 2)

    val result = YaesIO.run {
      Raise.either {
        catsIO.value
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome shouldBe Right(42)
  }

  it should "handle complex computations with both side effects and values" in {
    var counter = 0

    val yaesProgram: (YaesIO, Raise[Throwable]) ?=> String = YaesIO {
      counter += 1
      s"Count: $counter"
    }

    val catsIO = catseffect.blockingIO(yaesProgram)
      .flatMap { msg =>
        CatsIO {
          counter += 10
          s"$msg, Updated: $counter"
        }
      }

    val result = YaesIO.run {
      Raise.either {
        catsIO.value
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome shouldBe Right("Count: 1, Updated: 11")
    counter shouldBe 11
  }

  it should "defer execution until Cats IO is run (referential transparency)" in {
    var sideEffect = 0

    val yaesProgram: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
      sideEffect += 1
      42
    }

    // Creating the Cats IO should NOT execute the YAES program
    val catsIO = catseffect.blockingIO(yaesProgram)
    sideEffect shouldBe 0 // Side effect should not have happened yet!

    // Only when we run the Cats IO should the side effect occur
    val result = catsIO.unsafeRunSync()
    result shouldBe 42
    sideEffect shouldBe 1
  }

  it should "allow multiple executions of the same Cats IO (referential transparency)" in {
    var counter = 0

    val yaesProgram: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
      counter += 1
      counter
    }

    val catsIO = catseffect.blockingIO(yaesProgram)

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
      Raise.either {
        catsIO.value  // Extension method
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome shouldBe Right(42)
  }

  it should "support timeout using fluent syntax" in {
    val slowCatsIO = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)

    val result = YaesIO.run {
      Raise.either {
        slowCatsIO.value(100.millis)  // Extension method
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome.isLeft shouldBe true
    outcome.left.get shouldBe a[java.util.concurrent.TimeoutException]
  }

  it should "allow fluent chaining of Cats operations before conversion" in {
    val result = YaesIO.run {
      Raise.either {
        CatsIO.pure(21)
          .map(_ * 2)
          .flatMap(x => CatsIO.pure(x + 1))
          .value  // Fluent conversion at the end
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome shouldBe Right(43)
  }

  it should "preserve errors when using extension method" in {
    val catsIO = CatsIO.raiseError[Int](new RuntimeException("Extension error"))

    val result = YaesIO.run {
      Raise.either {
        catsIO.value  // Extension method
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome.isLeft shouldBe true
    outcome.left.get.getMessage shouldBe "Extension error"
  }

  it should "handle side effects correctly with extension method" in {
    var sideEffect = 0

    val catsIO = CatsIO {
      sideEffect += 1
      sideEffect
    }

    val result = YaesIO.run {
      Raise.either {
        catsIO.value  // Extension method
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome shouldBe Right(1)
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
      Raise.either {
        catsIO.value  // Extension method
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome shouldBe Right(11)
    counter shouldBe 11
  }

  "Raise integration" should "handle errors with Raise.either when converting Cats IO to YAES IO" in {
    val catsIO = CatsIO.raiseError[Int](new RuntimeException("Error in Cats"))

    val result = YaesIO.run {
      Raise.either {
        catsIO.value
      }
    }

    val either = Await.result(result, 5.seconds)
    either shouldBe a[Left[?, ?]]
    either.left.get shouldBe a[RuntimeException]
    either.left.get.getMessage shouldBe "Error in Cats"
  }

  it should "raise TimeoutException via Raise when timeout occurs" in {
    val slowCatsIO = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)

    val result = YaesIO.run {
      Raise.fold(
        slowCatsIO.value(100.millis)
      )(
        error => s"Timed out: ${error.getClass.getSimpleName}"
      )(
        value => s"Success: $value"
      )
    }

    val outcome = Await.result(result, 5.seconds)
    outcome should include("TimeoutException")
  }

  it should "handle Raise[Throwable] in yaesProgram when converting to Cats IO" in {
    val yaesProgram: (YaesIO, Raise[Throwable]) ?=> String = YaesIO {
      Raise.catching {
        throw new RuntimeException("YAES raised error")
      } { ex => ex }
    }

    val catsIO = catseffect.blockingIO(yaesProgram)

    val exception = intercept[RuntimeException] {
      catsIO.unsafeRunSync()
    }
    exception.getMessage shouldBe "YAES raised error"
  }

  it should "successfully execute when Raise[Throwable] is in context but no error occurs" in {
    val yaesProgram: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
      Raise.catching {
        42
      } { ex => ex }
    }

    val catsIO = catseffect.blockingIO(yaesProgram)
    val result = catsIO.unsafeRunSync()

    result shouldBe 42
  }

  it should "support Raise.recover for default values" in {
    val catsIO = CatsIO.raiseError[Int](new RuntimeException("Error"))

    val result = YaesIO.run {
      Raise.recover {
        catsIO.value
      } { _ => 0 }  // Default value
    }

    Await.result(result, 5.seconds) shouldBe 0
  }

  it should "compose Raise handlers across multiple conversions" in {
    val originalYaes: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
      Raise.catching { 42 } { ex => ex }
    }

    val result = YaesIO.run {
      val catsIO = catseffect.blockingIO(originalYaes).map(_ * 2)
      Raise.either {
        catsIO.value
      }
    }

    val outcome = Await.result(result, 5.seconds)
    outcome shouldBe Right(84)
  }
}
