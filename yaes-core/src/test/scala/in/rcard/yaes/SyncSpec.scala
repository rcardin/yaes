package in.rcard.yaes

import org.scalatest.TryValues.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.concurrent.Futures

class SyncSpec extends AsyncFlatSpec with Matchers {

  "The Sync effect" should "be able to run a side-effecting operation" in {
    val fortyTwo: Sync ?=> Int = Sync {
      42
    }

    val fortyThree: Sync ?=> Int = fortyTwo + 1

    for {
      actualResult <- Sync.run(fortyThree)
    } yield actualResult shouldBe 43
  }

  it should "be able to run a side-effecting operation that throws an exception" in {
    val fortyTwo: Sync ?=> Int = Sync {
      throw new RuntimeException("Boom!")
    }

    val fortyThree: Sync ?=> Int = fortyTwo + 1

    for {
      actualResult <- Sync.run(fortyThree).failed
    } yield actualResult should have message "Boom!"
  }

  it should "compose multiple Sync effects" in {
    val fortyThree: Sync ?=> Int = {
      val a = Sync(42)
      val b = Sync(1)
      a + b
    }

    for {
      actualResult <- Sync.run(fortyThree)
    } yield actualResult shouldBe 43
  }
}
