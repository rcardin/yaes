package in.rcard.yaes.ex

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.ex.Exp.Yaes
import in.rcard.yaes.ex.Exp.YRandom
import in.rcard.yaes.ex.Exp.YConsole
import in.rcard.yaes.ex.Exp.flatMap
import in.rcard.yaes.ex.Exp.map

class ExpSpec extends AnyFlatSpec with Matchers {
  "Yaes" should "compose different effects" in {
    val randomInt: Yaes[YRandom, Int] = YRandom {
      YRandom.nextInt
    }

    val printRandomInt: (YRandom, YConsole) ?=> Unit = YConsole {
      YConsole.println(randomInt.toString())
    }

    YConsole.run {
      YRandom.run {
        printRandomInt
      }
    }

    // val program: (YRandom, YConsole) ?=> Unit = 
    //   YRandom {
    //     YRandom.nextInt
    //   }.flatMap { r =>
    //     YConsole {
    //       YConsole.println(r.toString())
    //     }
    //   }
  }
}
