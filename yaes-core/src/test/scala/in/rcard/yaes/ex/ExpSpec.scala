package in.rcard.yaes.ex

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.ex.ThirdStrike.Random
import in.rcard.yaes.ex.ThirdStrike.Raise
import in.rcard.yaes.ex.ThirdStrike.Handler

class ExpSpec extends AnyFlatSpec with Matchers {
  "Yaes" should "compose different effects" in {
    val program: Handler[Raise[String] & Random] ?=> Int = {
      42
    }
    val result: Handler[Random] ?=> Int = Raise.run(program)
    Random.run(result) shouldBe 42
  }
}
