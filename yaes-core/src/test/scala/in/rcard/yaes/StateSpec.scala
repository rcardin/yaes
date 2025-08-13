package in.rcard.yaes

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
}
