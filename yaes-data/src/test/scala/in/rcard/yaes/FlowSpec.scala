package in.rcard.yaes

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
    flow
      .onStart {
        Flow.emit(0)
      }
      .collect { value =>
        actualResult += value
      }

    actualResult should contain theSameElementsInOrderAs Seq(0, 1, 2, 3)
  }

  "transform" should "transform the emitted values" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    flow
      .transform { value =>
        Flow.emit(value.toString)
      }
      .collect { value =>
        actualResult += value
      }

    actualResult should contain theSameElementsInOrderAs Seq("1", "2", "3")
  }

  "onEach" should "execute the action for each emitted value" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val actualResult       = scala.collection.mutable.ArrayBuffer[Int]()
    val onEachActualResult = scala.collection.mutable.ArrayBuffer[Int]()
    flow
      .onEach { value =>
        onEachActualResult += value
      }
      .collect { value =>
        actualResult += value
      }

    actualResult should contain theSameElementsInOrderAs Seq(1, 2, 3)
    onEachActualResult should contain theSameElementsInOrderAs Seq(1, 2, 3)
  }

  "map" should "transform the emitted values" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val actualResult = scala.collection.mutable.ArrayBuffer[String]()
    flow
      .map { value =>
        value.toString
      }
      .collect { value =>
        actualResult += value
      }

    actualResult should contain theSameElementsInOrderAs Seq("1", "2", "3")
  }

  "filter" should "filter the emitted values" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val actualResult = scala.collection.mutable.ArrayBuffer[Int]()
    flow
      .filter { value =>
        value % 2 == 0
      }
      .collect { value =>
        actualResult += value
      }

    actualResult should contain theSameElementsInOrderAs Seq(2)
  }

  "take" should "limit the number of emitted values" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val actualResult = scala.collection.mutable.ArrayBuffer[Int]()
    flow
      .take(2)
      .collect { value =>
        actualResult += value
      }

    actualResult should contain theSameElementsInOrderAs Seq(1, 2)
  }

  it should "throw an exception if n is less than or equal to 0" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val exception = intercept[IllegalArgumentException] {
      flow.take(0).collect(_ => ())
    }

    exception.getMessage should be("n must be greater than 0")
  }

  "drop" should "skip the first n emitted values" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val actualResult = scala.collection.mutable.ArrayBuffer[Int]()
    flow
      .drop(2)
      .collect { value =>
        actualResult += value
      }

    actualResult should contain theSameElementsInOrderAs Seq(3)
  }

  it should "throw an exception if n is less than or equal to 0" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val exception = intercept[IllegalArgumentException] {
      flow.drop(0).collect(_ => ())
    }

    exception.getMessage should be("n must be greater than 0")
  }

  "fold" should "reduce the emitted values to a single value" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val result = flow.fold(0) { (acc, value) =>
      acc + value
    }

    result should be(6)
  }

  it should "return the initial value if no values are emitted" in {
    val flow: Flow[Int] = Flow.flow[Int] {}

    val result = flow.fold(0) { (acc, value) =>
      acc + value
    }

    result should be(0)
  }

  "count" should "return the number of emitted values" in {
    val flow: Flow[Int] = Flow.flow[Int] {
      Flow.emit(1)
      Flow.emit(2)
      Flow.emit(3)
    }

    val result = flow.count()

    result should be(3)
  }
  
  it should "return 0 if no values are emitted" in {
    val flow: Flow[Int] = Flow.flow[Int] {}

    val result = flow.count()

    result should be(0)
  }
}
