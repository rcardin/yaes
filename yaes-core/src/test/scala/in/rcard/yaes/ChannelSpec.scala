package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._
import in.rcard.yaes.Channel.ChannelClosed
import in.rcard.yaes.Channel.Producer
import in.rcard.yaes.Async.Cancelled
import java.util.concurrent.LinkedBlockingQueue

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

  it should "allow using foreach to process incoming messages" in {
    val channel      = Channel.unbounded[Int]()
    var actualResult = 0

    Raise.run {
      Async.run {
        Async.fork {
          channel.send(1)
          channel.send(2)
          channel.send(3)
          channel.close()
        }

        Async.delay(200.millis)

        for (value <- channel) {
          actualResult += value
        }
      }
    }

    actualResult should be(6)
  }

  it should "use produce to create a producer and receive messages" in {
    val actualResult = Raise.run {
      Async.run {
        val channel = Channel.produce[Int] {
          Producer.send(1)
          Producer.send(2)
          Producer.send(3)
        }

        var sum = 0
        for (value <- channel) {
          sum += value
        }
        sum
      }
    }

    actualResult should be(6)
  }

  it should "close the channel if the producer throws an exception" in {
    val actualQueue     = new LinkedBlockingQueue[Int]()
    val actualException = intercept[RuntimeException] {
      Raise.run {
        Async.run {
          val channel = Channel.produce[Int] {
            Producer.send(1)
            Async.delay(100.millis)
            Producer.send(2)
            Async.delay(100.millis)
            Producer.send(3)
            throw new RuntimeException("Oops!!!")
          }

          Async.fork {
            for (value <- channel) {
              actualQueue.put(value)
            }
          }
        }
      }
    }

    actualException shouldBe a[RuntimeException]
    actualException.getMessage shouldEqual "Oops!!!"
    actualQueue.toArray should contain theSameElementsInOrderAs List(1, 2, 3)
  }

  it should "close the channel if the producer is cancelled" in {
    val actualQueue = new LinkedBlockingQueue[Int]()
    Raise.run {
      Async.run {
        val producerFb = Async.fork {
          val channel = Channel.produce[Int] {
            var i = 0
            while (true) {
              Producer.send(i)
              i += 1
              Async.delay(100.millis)
            }
          }

          Async.fork {
            for (value <- channel) {
              actualQueue.put(value)
            }
          }
        }

        Async.delay(550.millis)
        producerFb.cancel()
      }
    }

    actualQueue.toArray should contain theSameElementsInOrderAs List(0, 1, 2, 3, 4, 5)
  }

  it should "close the channel if the channel is cancelled" in {
    val actualQueue  = new LinkedBlockingQueue[String]()
    val actualResult = Raise.run {
      Async.run {
        val channel = Channel.produce[Int] {
          var i = 0
          while (true) {
            Producer.send(i)
            actualQueue.put(s"p$i")
            i += 1
            Async.delay(100.millis)
          }
        }

        actualQueue.put(s"c${channel.receive()}")
        Async.delay(150.millis)
        actualQueue.put(s"c${channel.receive()}")
        channel.cancel()
        channel.receive()
      }
    }

    actualResult should be(ChannelClosed)
    actualQueue.toArray should contain theSameElementsInOrderAs List("p0", "c0", "p1", "c1")
  }

  it should "cancel a closed channel" in {
    val actualQueue  = new LinkedBlockingQueue[Int]()
    val actualResult = Raise.run {
      Async.run {
        val channel = Channel.produce[Int] {
          Producer.send(1)
          actualQueue.put(1)
          Producer.send(2)
          actualQueue.put(2)
          Producer.send(3)
          actualQueue.put(3)
        }

        Async.delay(200.millis)
        channel.cancel()
        val readMsg = channel.receive()
        actualQueue.put(readMsg)
      }
    }

    actualResult should be(ChannelClosed)
    actualQueue.toArray should contain theSameElementsInOrderAs List(1, 2, 3)
  }

  "Bounded channel" should "block on send when full" in {
    val channel     = Channel.bounded[Int](2)
    val actualQueue = new LinkedBlockingQueue[String]()

    Raise.run {
      Async.run {
        val senderFiber = Async.fork {
          channel.send(1)
          actualQueue.put("p1")
          channel.send(2)
          actualQueue.put("p2")
          channel.send(3)
          actualQueue.put("p3")
        }

        Async.delay(200.millis)
        
        val queueSnapshot = actualQueue.toArray.toList
        queueSnapshot should contain allOf ("p1", "p2")
        
        actualQueue.put(s"c${channel.receive()}")
        actualQueue.put(s"c${channel.receive()}")

        Async.delay(200.millis)

        actualQueue.put(s"c${channel.receive()}")
        channel.close()
      }
    }

    val finalQueue = actualQueue.toArray.toList.map(_.toString)
    finalQueue should contain allOf ("p1", "p2", "p3", "c1", "c2", "c3")
    
    finalQueue.filter(_.startsWith("p")) should equal(List("p1", "p2", "p3"))
    
    finalQueue.filter(_.startsWith("c")) should equal(List("c1", "c2", "c3"))
  }

  "Rendezvous channel" should "block on send until receive is ready" in {
    val channel     = Channel.rendezvous[Int]()
    val actualQueue = new LinkedBlockingQueue[String]()

    Raise.run {
      Async.run {
        val senderFiber = Async.fork {
          actualQueue.put("p1")
          channel.send(1)
          Async.delay(100.millis)
          actualQueue.put("p2")
          channel.send(2)
        }

        Async.delay(200.millis)

        actualQueue.put(s"c${channel.receive()}")
        actualQueue.put(s"c${channel.receive()}")
        channel.close()
      }
    }

    actualQueue.toArray should contain theSameElementsInOrderAs List("p1", "c1", "p2", "c2")
  }
}
