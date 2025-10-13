package in.rcard.yaes

import in.rcard.yaes.Async.Async
import in.rcard.yaes.Raise.Raise
import in.rcard.yaes.Channel.SendChannel
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import in.rcard.yaes.Channel.ChannelClosed

object Channel {

  case object ChannelClosed
  type ChannelClosed = ChannelClosed.type

  trait SendChannel[T] {
    def send(value: T)(using Async, Raise[ChannelClosed]): Unit
    def close(): Boolean
  }

  trait ReceiveChannel[T] {
    def receive()(using Async, Raise[ChannelClosed]): T
  }

  def unbounded[T](): Channel[T] =
    new Channel(new java.util.concurrent.LinkedBlockingQueue[T]())
}

class Channel[T] private (queue: BlockingQueue[T])
    extends Channel.ReceiveChannel[T],
      Channel.SendChannel[T] {

  private val closed = new AtomicBoolean(false)

  override def receive()(using Async, Raise[ChannelClosed]): T =
    if (closed.get() && queue.isEmpty()) {
      Raise.raise(ChannelClosed)
    } else {
      queue.take()
    }

  override def send(value: T)(using Async, Raise[ChannelClosed]): Unit =
    if (closed.get()) {
      Raise.raise(ChannelClosed)
    } else {
      queue.put(value)
    }

  override def close(): Boolean = closed.compareAndSet(false, true)

}
