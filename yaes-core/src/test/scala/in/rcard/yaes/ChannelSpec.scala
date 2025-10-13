package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._
import in.rcard.yaes.Channel.ChannelClosed

class ChannelSpec extends AnyFlatSpec with Matchers {

  "A Channel" should "send and receive values correctly" in {
    val channel             = Channel.unbounded[Int]()
    var actualReceivedValue = 0

    Raise.run {
      Async.run {
        Async.fork {
          channel.send(42)
        }

        val fiber = Async.fork {
          actualReceivedValue = channel.receive()
        }
      }
    }

    actualReceivedValue should be(42)
  }

  it should "block on receive when no value is available" in {
    val channel             = Channel.unbounded[Int]()
    var actualReceivedValue = 0

    Raise.run {
      Async.run {
        val fiber = Async.fork {
          actualReceivedValue = channel.receive()
        }

        Async.delay(100.millis)
        channel.send(42)
      }
    }

    actualReceivedValue should be(42)
  }

  it should "not discard messages sent before closing" in {
    val channel = Channel.unbounded[Int]()

    val actualResult = Raise.run {
      Async.run {
        Async.fork {
          channel.send(1)
          channel.send(2)
          channel.send(3)
          channel.close()
        }

        Async.delay(200.millis)

        channel.receive() + channel.receive() + channel.receive()
      }
    }

    actualResult should be(6)
  }

  it should "return false if the channel was already closed" in {
    val channel = Channel.unbounded[Int]()

    val firstCloseResult  = channel.close()
    val secondCloseResult = channel.close()

    firstCloseResult should be(true)
    secondCloseResult should be(false)
  }

  it should "raise ChannelClosed when sending on a closed channel" in {
    val channel = Channel.unbounded[Int]()
    channel.close()

    val actualResult =
      Raise.run {
        Async.run {
          channel.send(42)
        }
      }

    actualResult should be(ChannelClosed)
  }

  it should "raise ChannelClosed when receiving from a closed and empty channel" in {
    val channel = Channel.unbounded[Int]()
    channel.close()

    val actualResult =
      Raise.run {
        Async.run {
          channel.receive()
        }
      }

    actualResult should be(ChannelClosed)
  }
}
