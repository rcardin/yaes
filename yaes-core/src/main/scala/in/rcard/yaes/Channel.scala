package in.rcard.yaes

import in.rcard.yaes.Async.Async
import in.rcard.yaes.Raise.Raise
import in.rcard.yaes.Channel.SendChannel
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import in.rcard.yaes.Channel.ChannelClosed
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.SynchronousQueue

object Channel {

  sealed trait Type {}
  object Type       {
    case object Unbounded             extends Type
    case class Bounded(capacity: Int) extends Type
    case object Rendezvous            extends Type
  }

  case object ChannelClosed
  type ChannelClosed = ChannelClosed.type

  trait SendChannel[T] {
    def send(value: T)(using Async, Raise[ChannelClosed]): Unit
    def close(): Boolean
  }

  trait ReceiveChannel[T] {
    def receive()(using Async, Raise[ChannelClosed]): T
    def cancel()(using Async): Unit
  }

  def unbounded[T](): Channel[T] = Channel(Type.Unbounded)

  def bounded[T](capacity: Int): Channel[T] = Channel(Type.Bounded(capacity))

  def rendezvous[T](): Channel[T] = Channel(Type.Rendezvous)

  def apply[T](channelType: Type): Channel[T] = channelType match {
    case Type.Unbounded         => new Channel(new LinkedBlockingQueue[T]())
    case Type.Bounded(capacity) =>
      new Channel(new ArrayBlockingQueue[T](capacity))
    case Type.Rendezvous =>
      new Channel(new SynchronousQueue[T]())
  }

  extension [T](channel: ReceiveChannel[T]) {
    def foreach[U](f: T => U)(using Async): Unit = {
      Raise.run { // FIXME Not the best implementation
        while (true) {
          val value = channel.receive()
          f(value)
        }
      }
    }
  }

  trait Producer[T] extends SendChannel[T] {}
  object Producer {
    def send[T](value: T)(using p: Producer[T], a: Async, r: Raise[ChannelClosed]): Unit =
      p.send(value)
    def close()(using p: Producer[?], a: Async, r: Raise[ChannelClosed]): Boolean =
      p.close()
  }

  def produce[T](block: Producer[T] ?=> Unit)(using Async): ReceiveChannel[T] = {
    val channel = Channel.unbounded[T]() // FIXME We need to move this away
    Async
      .fork {
        try {
          block(using
            new Producer[T] {
              override def send(value: T)(using Async, Raise[ChannelClosed]): Unit =
                channel.send(value)
              override def close(): Boolean = channel.close()
            }
          )
        } finally {
          channel.close()
        }
      }
    channel
  }
}

class Channel[T] private (private val queue: BlockingQueue[T])
    extends Channel.ReceiveChannel[T],
      Channel.SendChannel[T] {

  private enum Status {
    case Open, Close, Cancelled
  }

  private val status = new AtomicReference(Status.Open)

  override def receive()(using Async, Raise[ChannelClosed]): T =
    if (status.get() != Status.Open && queue.isEmpty()) { // FIXME Possible race condition?
      Raise.raise(ChannelClosed)
    } else {
      queue.take()
    }

  override def send(value: T)(using Async, Raise[ChannelClosed]): Unit =
    status.get() match {
      case Status.Cancelled =>
        Thread.currentThread().interrupt()
      case Status.Close =>
        Raise.raise(ChannelClosed)
      case Status.Open =>
        queue.put(value)
    }

  override def close(): Boolean = status.compareAndSet(Status.Open, Status.Close)

  override def cancel()(using Async): Unit = {
    status.compareAndSet(Status.Open, Status.Cancelled)
    queue.clear()
  }
}
