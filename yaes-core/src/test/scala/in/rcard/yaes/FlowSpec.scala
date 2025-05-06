package in.rcard.yaes

import in.rcard.yaes.Output.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.Flow.asFlow

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

  it should "collect a stateful program" in {
    val actualEffectfulResult = new java.io.ByteArrayOutputStream()
    Console.withOut(actualEffectfulResult) {
      val flow: Flow[Int] = Flow.flow[Int] {
        Flow.emit(1)
        Flow.emit(2)
        Flow.emit(3)
      }

      val actualResult = scala.collection.mutable.ArrayBuffer[Int]()
      val program: Output ?=> Unit = flow.collect { value =>
        Output.print(value.toString)
        actualResult += value
      }

      Output.run(program)

      actualResult should contain theSameElementsInOrderAs Seq(1, 2, 3)
      actualEffectfulResult.toString should be("123")
    }
  }

  it should "be created from a list" in {
    val flow: Flow[Int] = List(1, 2, 3).asFlow()

    val actualResult = scala.collection.mutable.ArrayBuffer[Int]()
    flow.collect {
      actualResult += _
    }

    actualResult should contain theSameElementsInOrderAs Seq(1, 2, 3)
  }

  it should "be created an array of elements (varargs)" in {
    val flow: Flow[Int] = Flow(1, 2, 3)

    val actualResult = scala.collection.mutable.ArrayBuffer[Int]()
    flow.collect {
      actualResult += _
    }

    actualResult should contain theSameElementsInOrderAs Seq(1, 2, 3)
  }

  "onStart" should "execute the action before collecting" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val actualResult = scala.collection.mutable.ArrayBuffer[Int]()
    flow.onStart {
      Flow.emit(0)
    }.collect { value =>
      actualResult += value
    }

    actualResult should contain theSameElementsInOrderAs Seq(0, 1, 2, 3)
  }

  it should "execute the action before collecting in a stateful program" in {
    val actualEffectfulResult = new java.io.ByteArrayOutputStream()
    Console.withOut(actualEffectfulResult) {
      val flow: Flow[Int] = Flow.flow[Int] {
        Flow.emit(1)
        Flow.emit(2)
        Flow.emit(3)
      }

      val actualResult = scala.collection.mutable.ArrayBuffer[Int]()
      val program: Output ?=> Unit = flow.onStart {
        Output.print("0")
      }.collect { value =>
        actualResult += value
      }

      Output.run(program)

      actualResult should contain theSameElementsInOrderAs Seq(1, 2, 3)
      actualEffectfulResult.toString should be("0")
    }
  }
}
