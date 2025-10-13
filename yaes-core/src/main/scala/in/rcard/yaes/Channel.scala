package in.rcard.yaes

import in.rcard.yaes.Async.Async
import in.rcard.yaes.Channel.SendChannel
import java.util.concurrent.BlockingQueue

object Channel {

  trait SendChannel[T] {
    def send(value: T)(using Async): Unit
    def close(): Boolean
  }

  trait ReceiveChannel[T] {
    def receive()(using Async): T
  }

  def unbounded[T](): Channel[T] =
    new Channel(new java.util.concurrent.LinkedBlockingQueue[T]())
}

class Channel[T] private(queue: BlockingQueue[T]) extends Channel.ReceiveChannel[T], Channel.SendChannel[T] {

  override def receive()(using Async): T = 
    queue.take()

  override def send(value: T)(using Async): Unit =
    queue.put(value)

  override def close(): Boolean = ???


}
