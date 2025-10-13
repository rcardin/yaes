package in.rcard.yaes

import in.rcard.yaes.Async.Async
import in.rcard.yaes.Channel.SendChannel
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

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

class Channel[T] private (queue: BlockingQueue[T])
    extends Channel.ReceiveChannel[T],
      Channel.SendChannel[T] {

  private val closed = new AtomicBoolean(false)

  override def receive()(using Async): T =
    if (closed.get() && queue.isEmpty()) {
      throw new IllegalStateException("Channel is closed and empty")
    } else {
      queue.take()
    }

  override def send(value: T)(using Async): Unit =
    if (closed.get()) {
      throw new IllegalStateException("Channel is closed")
    } else {
      queue.put(value)
    }

  override def close(): Boolean = closed.compareAndSet(false, true)

}
