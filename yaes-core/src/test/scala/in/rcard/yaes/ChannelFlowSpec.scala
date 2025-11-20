package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class ChannelFlowSpec extends AnyFlatSpec with Matchers {

  // ========== Basic Functionality Tests ==========

  "channelFlow" should "emit values sent from the builder block" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        Channel.Producer.send(1)
        Channel.Producer.send(2)
        Channel.Producer.send(3)
      }

      flow.collect { value =>
        result += value
      }
    }
    
    result should contain theSameElementsInOrderAs Seq(1, 2, 3)
  }

  it should "be cold - execute the block on each collection" in {
    var executionCount = 0
    val result1 = scala.collection.mutable.ArrayBuffer[Int]()
    val result2 = scala.collection.mutable.ArrayBuffer[Int]()

    Async.run {
      val flow = Channel.channelFlow[Int] {
        executionCount += 1
        Channel.Producer.send(executionCount)
      }

      flow.collect { value => result1 += value }
      flow.collect { value => result2 += value }
    }

    executionCount should be(2)
    result1 should contain theSameElementsInOrderAs Seq(1)
    result2 should contain theSameElementsInOrderAs Seq(2)
  }

  it should "emit values in order" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        (1 to 10).foreach(Channel.Producer.send)
      }

      flow.collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs (1 to 10)
  }

  it should "handle empty channelFlow (no emissions)" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        // No emissions
      }

      flow.collect { value => result += value }
    }

    result should be(empty)
  }

  // ========== Concurrent Emission Tests ==========

  it should "support concurrent emission from multiple fibers" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        Raise.run {
          Async.run {
            Async.fork {
              Channel.Producer.send(1)
              Channel.Producer.send(2)
            }

            Async.fork {
              Channel.Producer.send(3)
              Channel.Producer.send(4)
            }
          }
        }
      }

      flow.collect { value => result += value }
    }

    result should contain allOf (1, 2, 3, 4)
    result should have size 4
  }

  it should "emit values from forked fibers in arrival order" in {
    val result = scala.collection.mutable.ArrayBuffer[String]()
    Async.run {
      val flow = Channel.channelFlow[String] {
        Raise.run {
          Async.run {
            Async.fork {
              Async.delay(50.millis)
              Channel.Producer.send("slow")
            }

            Async.fork {
              Channel.Producer.send("fast")
            }
          }
        }
      }

      flow.collect { value => result += value }
    }

    // Fast should arrive before slow
    result should contain theSameElementsInOrderAs Seq("fast", "slow")
  }

  it should "complete after all forked fibers complete" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        Raise.run {
          Async.run {
            Async.fork {
              Async.delay(100.millis)
              Channel.Producer.send(1)
            }

            Async.fork {
              Async.delay(50.millis)
              Channel.Producer.send(2)
            }

            Channel.Producer.send(3)
          }
        }
      }

      flow.collect { value => result += value }
    }

    result should contain allOf (1, 2, 3)
    result should have size 3
  }

  // ========== Lifecycle & Error Handling Tests ==========

  it should "close channel automatically when block completes" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        Channel.Producer.send(1)
        Channel.Producer.send(2)
      }

      flow.collect { value => result += value }
    }

    // Should complete without hanging
    result should contain theSameElementsInOrderAs Seq(1, 2)
  }

  it should "handle exceptions in the builder block" in {
    an[RuntimeException] should be thrownBy {
      Async.run {
        val flow = Channel.channelFlow[Int] {
          Raise.run {
            Async.run {
              Channel.Producer.send(1)
              throw new RuntimeException("Test exception")
            }
          }
        }

        flow.collect { value => () }
      }
    }
  }

  it should "propagate exceptions from forked fibers" in {
    an[RuntimeException] should be thrownBy {
      Async.run {
        val flow = Channel.channelFlow[Int] {
          Raise.run {
            Async.run {
              Channel.Producer.send(1)

              Async.fork {
                Async.delay(50.millis)
                throw new RuntimeException("Forked fiber exception")
              }
            }
          }
        }

        flow.collect { value => () }
      }
    }
  }

  it should "support cancellation of the flow" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        Raise.run {
          Async.run {
            var count = 0
            while (count < 1000) {
              Channel.Producer.send(count)
              count += 1
              Async.delay(10.millis)
            }
          }
        }
      }

      flow.take(5).collect { value => result += value }
    }

    result should have size 5
  }

  // ========== Integration with Channel Types (channelFlowWith) ==========

  "channelFlowWith" should "work with unbounded channel type" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlowWith[Int](Channel.Type.Unbounded) {
        (1 to 100).foreach(Channel.Producer.send)
      }

      flow.collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs (1 to 100)
  }

  it should "work with bounded channel type" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlowWith[Int](Channel.Type.Bounded(10)) {
        (1 to 20).foreach(Channel.Producer.send)
      }

      flow.collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs (1 to 20)
  }

  it should "work with rendezvous channel type" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlowWith[Int](Channel.Type.Rendezvous) {
        Raise.run {
          Async.run {
            Async.fork {
              Channel.Producer.send(1)
              Channel.Producer.send(2)
              Channel.Producer.send(3)
            }
          }
        }
      }

      flow.collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs Seq(1, 2, 3)
  }

  it should "handle backpressure with bounded channels" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlowWith[Int](Channel.Type.Bounded(2)) {
        Raise.run {
          Async.run {
            Async.fork {
              // Producer tries to send more than buffer capacity
              (1 to 10).foreach { i =>
                Channel.Producer.send(i)
              }
            }
          }
        }
      }

      flow.collect { value =>
        result += value
        Async.delay(50.millis) // Slow consumer
      }
    }

    result should contain theSameElementsInOrderAs (1 to 10)
  }

  // ========== Integration with Flow Operators ==========

  it should "work with map operator" in {
    val result = scala.collection.mutable.ArrayBuffer[String]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        (1 to 5).foreach(Channel.Producer.send)
      }

      flow.map(_.toString).collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs Seq("1", "2", "3", "4", "5")
  }

  it should "work with filter operator" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        (1 to 10).foreach(Channel.Producer.send)
      }

      flow.filter(_ % 2 == 0).collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs Seq(2, 4, 6, 8, 10)
  }

  it should "work with take operator" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        (1 to 100).foreach(Channel.Producer.send)
      }

      flow.take(3).collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs Seq(1, 2, 3)
  }

  it should "work with transform operator" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlow[Int] {
        Channel.Producer.send(1)
        Channel.Producer.send(2)
        Channel.Producer.send(3)
      }

      flow
        .transform { value =>
          Flow.emit(value)
          Flow.emit(value * 10)
        }
        .collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs Seq(1, 10, 2, 20, 3, 30)
  }

  // ========== Complex Scenarios ==========

  it should "merge multiple flows concurrently" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      def merge[T](flow1: Flow[T], flow2: Flow[T]): Flow[T] =
        Channel.channelFlow[T] {
          Raise.run {
            Async.run {
              Async.fork {
                flow1.collect { value => Channel.Producer.send(value) }
              }

              Async.fork {
                flow2.collect { value => Channel.Producer.send(value) }
              }
            }
          }
        }

      val flow1 = Flow(1, 2, 3)
      val flow2 = Flow(4, 5, 6)

      merge(flow1, flow2).collect { value => result += value }
    }

    result should contain allOf (1, 2, 3, 4, 5, 6)
    result should have size 6
  }

  it should "handle slow consumers with bounded buffer" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlowWith[Int](Channel.Type.Bounded(5)) {
        Raise.run {
          Async.run {
            Async.fork {
              (1 to 20).foreach { i =>
                Channel.Producer.send(i)
              }
            }
          }
        }
      }

      flow.collect { value =>
        result += value
        Async.delay(20.millis)
      }
    }

    result should contain theSameElementsInOrderAs (1 to 20)
  }

  it should "support nested channelFlow" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val outerFlow = Channel.channelFlow[Int] {
        val innerFlow = Channel.channelFlow[Int] {
          (1 to 3).foreach(Channel.Producer.send)
        }

        innerFlow.collect { value =>
          Channel.Producer.send(value * 10)
        }
      }

      outerFlow.collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs Seq(10, 20, 30)
  }

  it should "work with zipWithIndex" in {
    val result = scala.collection.mutable.ArrayBuffer[(String, Long)]()
    Async.run {
      val flow = Channel.channelFlow[String] {
        Channel.Producer.send("a")
        Channel.Producer.send("b")
        Channel.Producer.send("c")
      }

      flow.zipWithIndex().collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs Seq(("a", 0L), ("b", 1L), ("c", 2L))
  }

  // ========== Edge Cases ==========

  it should "handle rapid collection and cancellation" in {
    val result1 = scala.collection.mutable.ArrayBuffer[Int]()
    val result2 = scala.collection.mutable.ArrayBuffer[Int]()
    
    Async.run {
      val flow = Channel.channelFlow[Int] {
        var i = 0
        while (i < 1000) {
          Channel.Producer.send(i)
          i += 1
        }
      }

      // Collect multiple times with different take amounts
      flow.take(3).collect { value => result1 += value }
      flow.take(5).collect { value => result2 += value }
    }

    result1 should have size 3
    result2 should have size 5
  }

  it should "handle zero-capacity (rendezvous) correctly" in {
    val result = scala.collection.mutable.ArrayBuffer[Int]()
    Async.run {
      val flow = Channel.channelFlowWith[Int](Channel.Type.Rendezvous) {
        Raise.run {
          Async.run {
            Async.fork {
              Channel.Producer.send(1)
              Channel.Producer.send(2)
              Channel.Producer.send(3)
            }
          }
        }
      }

      flow.collect { value => result += value }
    }

    result should contain theSameElementsInOrderAs Seq(1, 2, 3)
  }
}
