package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class ChannelSpec extends AnyFlatSpec with Matchers {

  "A Channel" should "send and receive values correctly" in {
    val channel             = Channel.unbounded[Int]()
    var actualReceivedValue = 0

    Async.run {
      Async.fork {
        channel.send(42)
      }

      val fiber = Async.fork {
        actualReceivedValue = channel.receive()
      }
    }

    actualReceivedValue should be(42)
  }

  it should "block on receive when no value is available" in {
    val channel             = Channel.unbounded[Int]()
    var actualReceivedValue = 0

    Async.run {
      val fiber = Async.fork {
        actualReceivedValue = channel.receive()
      }

      Async.delay(100.millis)
      channel.send(42)
    }

    actualReceivedValue should be(42)
  }

  it should "not discard messages sent before closing" in {
    val channel = Channel.unbounded[Int]()

    Async.run {
      Async.fork {
        channel.send(1)
        channel.send(2)
        channel.send(3)
        channel.close()
      }

      Async.delay(200.millis)

      val actualResult = channel.receive() + channel.receive() + channel.receive()

      actualResult should be(6)
    }
  }
}
