package in.rcard.yaes

import in.rcard.yaes.State.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StateSpec extends AnyFlatSpec with Matchers {

  "State" should "get the state" in {
    val (actualState, actualResult) = State.run(42) {
      val current = State.get
      current
    }

    actualState shouldBe 42
    actualResult shouldBe 42
  }

  it should "set returns the old state" in {
    val (_, actualResult) = State.run(42) {
      val oldState = State.set(100)
      val newState = State.get
      (oldState, newState)
    }

    actualResult shouldBe (42, 100)
  }

  it should "get, set, and get the state" in {
    val (_, (initialState, updatedState)) = State.run(42) {
      val oldState = State.get
      State.set(100)
      val newState = State.get
      (oldState, newState)
    }

    initialState shouldBe 42
    updatedState shouldBe 100
  }

  it should "update the state" in {
    val (_, actualUpdatedResult) = State.run(42) {
      State.update[Int](10 + _)
    }

    actualUpdatedResult shouldBe 52
  }

  it should "use the state" in {
    val (_, actualResult) = State.run(42) {
      State.use[Int, String](state => s"$state")
    }

    actualResult shouldBe "42"
  }

  it should "mix different State" in {
    val (_, actualResult) = State.run[Int, Int](42) {
      val (_, innerResult) = State.run[String, Int]("43") {
        State.get[String].toInt
      }
      innerResult + State.get[Int]
    }

    actualResult shouldBe 85
  }

  it should "implement complex use cases" in {
    def counter(n: Int): State[Int] ?=> Int = {
      if n <= 0 then State.get[Int]
      else
        State.update[Int](_ + 1)
        counter(n - 1)
    }

    val actualCounterResult = State.run(0)(counter(10))

    actualCounterResult shouldBe (10, 10)
  }
}
