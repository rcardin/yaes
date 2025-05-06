package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FlowSpec extends AnyFlatSpec with Matchers {

  "A flow" should "collect every value emitted" in {

    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val actualResult = scala.collection.mutable.ArrayBuffer[Int]()
    flow.collect { 
      actualResult += _
    }

    actualResult should contain theSameElementsInOrderAs Seq(1, 2, 3)
  }
}
