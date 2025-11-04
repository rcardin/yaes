package in.rcard.yaes

import in.rcard.yaes.Channel.SendChannel
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import in.rcard.yaes.Channel.ChannelClosed
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.SynchronousQueue

/** A channel is a communication primitive for transferring data between asynchronous computations.
  * Conceptually, a channel is similar to [[java.util.concurrent.BlockingQueue]], but it has
  * suspending operations instead of blocking ones and can be closed.
  *
  * A channel is composed of two interfaces: [[SendChannel]] for sending elements and
  * [[ReceiveChannel]] for receiving elements. This separation allows precise control over which
  * operations are available in different contexts.
  *
  * Example usage:
  * {{{
  * import in.rcard.yaes.Channel
  * import in.rcard.yaes.Async
  *
  * // Create an unbounded channel
  * val channel = Channel.unbounded[Int]()
  *
  * Raise.run {
  *   Async.run {
  *     // Producer
  *     Async.fork {
  *       channel.send(1)
  *       channel.send(2)
  *       channel.send(3)
  *       channel.close()
  *     }
  *
  *     // Consumer
  *     channel.foreach { value =>
  *       println(s"Received: $value")
  *     }
  *   }
  * }
  * }}}
  *
  * Channels support different buffer configurations:
  *
  *   - [[Type.Unbounded]]: A channel with unlimited buffer capacity that never suspends the sender
  *   - [[Type.Bounded]]: A channel with a fixed buffer capacity; senders suspend when buffer is
  *     full
  *   - [[Type.Rendezvous]]: A channel with no buffer; sender and receiver must rendezvous (meet)
  *
  * @see
  *   [[SendChannel]] for sending operations
  * @see
  *   [[ReceiveChannel]] for receiving operations
  */
object Channel {

  /** The type of channel buffer strategy.
    *
    * Different types control how elements are buffered and when senders/receivers suspend.
    */
  sealed trait Type {}
  object Type       {

    /** An unbounded channel that never suspends the sender.
      *
      * Elements are buffered in an unlimited queue. This channel type is suitable when you need to
      * ensure that senders never block, but be aware that memory usage can grow without bounds.
      *
      * Example:
      * {{{
      * val channel = Channel.unbounded[String]()
      *
      * Raise.run {
      *   Async.run {
      *     Async.fork {
      *       // These sends will never suspend
      *       channel.send("message1")
      *       channel.send("message2")
      *       channel.send("message3")
      *     }
      *   }
      * }
      * }}}
      */
    case object Unbounded extends Type

    /** A bounded channel with a fixed buffer capacity.
      *
      * When the buffer is full, the behavior depends on the `onOverflow` policy:
      *   - [[OverflowStrategy.SUSPEND]]: The sender suspends until space becomes available
      *     (default)
      *   - [[OverflowStrategy.DROP_OLDEST]]: The oldest element is dropped to make space
      *   - [[OverflowStrategy.DROP_LATEST]]: The new element is discarded
      *
      * Example:
      * {{{
      * import Channel.OverflowStrategy
      *
      * val channel = Channel.bounded[Int](capacity = 2)
      *
      * Raise.run {
      *   Async.run {
      *     Async.fork {
      *       channel.send(1) // Succeeds immediately
      *       channel.send(2) // Succeeds immediately
      *       channel.send(3) // Suspends until receiver takes an element
      *     }
      *   }
      * }
      * }}}
      *
      * @param capacity
      *   the maximum number of elements that can be buffered
      * @param onOverflow
      *   the strategy to use when the buffer is full (default: [[OverflowStrategy.SUSPEND]])
      */
    case class Bounded(capacity: Int, onOverflow: OverflowStrategy = OverflowStrategy.SUSPEND)
        extends Type

    /** A rendezvous channel with no buffer.
      *
      * The sender and receiver must meet (rendezvous): [[SendChannel.send]] suspends until another
      * computation invokes [[ReceiveChannel.receive]], and vice versa.
      *
      * Example:
      * {{{
      * val channel = Channel.rendezvous[String]()
      *
      * Raise.run {
      *   Async.run {
      *     val sender = Async.fork {
      *       channel.send("hello") // Suspends until receiver is ready
      *       println("Message sent")
      *     }
      *
      *     val receiver = Async.fork {
      *       val msg = channel.receive() // Suspends until sender is ready
      *       println(s"Received: $msg")
      *     }
      *   }
      * }
      * }}}
      */
    case object Rendezvous extends Type
  }

  /** A strategy for buffer overflow handling in bounded channels.
    *
    * This controls what happens when a bounded channel's buffer is full and a new element is sent:
    *   - [[OverflowStrategy.SUSPEND]]: The sender suspends until space becomes available (default
    *     behavior)
    *   - [[OverflowStrategy.DROP_OLDEST]]: The oldest value in the buffer is dropped and the new
    *     value is added
    *   - [[OverflowStrategy.DROP_LATEST]]: The new value is discarded and the buffer remains
    *     unchanged
    *
    * Example:
    * {{{
    * import Channel.OverflowStrategy
    *
    * // Channel that drops oldest element when full
    * val channel1 = Channel.bounded[Int](capacity = 2, onOverflow = OverflowStrategy.DROP_OLDEST)
    *
    * // Channel that drops newest element when full
    * val channel2 = Channel.bounded[Int](capacity = 2, onOverflow = OverflowStrategy.DROP_LATEST)
    *
    * // Channel that suspends sender when full (default)
    * val channel3 = Channel.bounded[Int](capacity = 2, onOverflow = OverflowStrategy.SUSPEND)
    * }}}
    */
  enum OverflowStrategy {

    /** Suspends the sender until space becomes available in the buffer.
      *
      * This is the default behavior for bounded channels and provides backpressure to prevent fast
      * producers from overwhelming slow consumers.
      */
    case SUSPEND

    /** Drops the oldest element in the buffer and adds the new element.
      *
      * When the buffer is full, the oldest buffered element is removed to make space for the new
      * element. The send operation completes immediately without suspending.
      */
    case DROP_OLDEST

    /** Drops the new element and keeps the buffer unchanged.
      *
      * When the buffer is full, the new element being sent is discarded and the buffer remains
      * unchanged. The send operation completes immediately without suspending.
      */
    case DROP_LATEST
  }

  /** Indicates that a channel operation failed because the channel was closed. */
  case object ChannelClosed
  type ChannelClosed = ChannelClosed.type

  /** The send-only side of a channel.
    *
    * This interface provides operations for sending elements to a channel and closing it. It does
    * not expose receiving operations, allowing you to pass only the sending capability to
    * producers.
    *
    * @tparam T
    *   the type of elements in the channel
    */
  trait SendChannel[T] {

    /** Sends an element to the channel, suspending if necessary.
      *
      * If the channel's buffer is full (for bounded channels) or if there's no receiver ready (for
      * rendezvous channels), this operation suspends until space becomes available or a receiver is
      * ready.
      *
      * Example:
      * {{{
      * val channel = Channel.bounded[String](2)
      * Raise.run {
      *   Async.run {
      *     Async.fork {
      *       channel.send("first")  // Succeeds immediately
      *       channel.send("second") // Succeeds immediately
      *       channel.send("third")  // Suspends until space available
      *     }
      *   }
      * }
      * }}}
      *
      * @param value
      *   the element to send
      * @param async
      *   the async context
      * @param raise
      *   the raise context for handling [[ChannelClosed]] errors
      * @throws ChannelClosed
      *   if the channel is closed
      */
    def send(value: T)(using Async, Raise[ChannelClosed]): Unit

    /** Closes the channel, preventing further sends.
      *
      * After closing, no more elements can be sent. Receivers can still receive remaining buffered
      * elements. Once all buffered elements are consumed, receive operations will raise
      * [[ChannelClosed]].
      *
      * Example:
      * {{{
      * val channel = Channel.unbounded[Int]()
      *
      * Raise.run {
      *   Async.run {
      *     Async.fork {
      *       channel.send(1)
      *       channel.send(2)
      *     }
      *       val closed = channel.close() // Returns true
      *       val alreadyClosed = channel.close() // Returns false
      *
      *     // Can still receive buffered elements
      *
      *     println(channel.receive()) // Prints 1
      *     println(channel.receive()) // Prints 2
      *     println(channel.receive()) // Raises ChannelClosed
      *   }
      * }
      * }}}
      *
      * @return
      *   `true` if the channel was successfully closed, `false` if it was already closed
      */
    def close(): Boolean
  }

  /** The receive-only side of a channel.
    *
    * This interface provides operations for receiving elements from a channel and canceling it. It
    * does not expose sending operations, allowing you to pass only the receiving capability to
    * consumers.
    *
    * @tparam T
    *   the type of elements in the channel
    */
  trait ReceiveChannel[T] {

    /** Receives an element from the channel, suspending if necessary.
      *
      * If the channel is empty, this operation suspends until an element becomes available. If the
      * channel is closed and empty, it raises [[ChannelClosed]].
      *
      * Example:
      * {{{
      * val channel = Channel.unbounded[Int]()
      *
      * Raise.run {
      *   Async.run {
      *     Async.fork {
      *       Async.delay(1.second)
      *       channel.send(42)
      *     }
      *
      *     val value = channel.receive() // Suspends until element available
      *     println(s"Received: $value")
      *   }
      * }
      * }}}
      *
      * @param async
      *   the async context
      * @param raise
      *   the raise context for handling [[ChannelClosed]] errors
      * @return
      *   the received element
      * @throws ChannelClosed
      *   if the channel is closed and empty
      */
    def receive()(using Async, Raise[ChannelClosed]): T

    /** Cancels the channel, clearing all buffered elements.
      *
      * After cancellation, all buffered elements are discarded, and ongoing operations are
      * interrupted. This is useful for cleanup when you no longer need the channel's data.
      *
      * Example:
      * {{{
      * val channel = Channel.unbounded[String]()
      *
      * Raise.run {
      *   Async.run {
      *     Async.fork {
      *       channel.send("msg1")
      *       channel.send("msg2")
      *     }
      *
      *     channel.cancel() // Clears all buffered messages
      *
      *     channel.receive() // Will fail as channel is cancelled
      *   }
      * }
      * }}}
      *
      * @param async
      *   the async context
      */
    def cancel()(using Async): Unit
  }

  /** Creates an unbounded channel.
    *
    * An unbounded channel has unlimited buffer capacity and never suspends the sender. Use this
    * when you need maximum throughput and memory usage is not a concern.
    *
    * Example:
    * {{{
    * val channel = Channel.unbounded[Int]()
    *
    * Raise.run {
    *   Async.run {
    *     // These operations complete immediately
    *     (1 to 1000).foreach(channel.send)
    *     channel.close()
    *   }
    * }
    * }}}
    *
    * @tparam T
    *   the type of elements in the channel
    * @return
    *   a new unbounded channel
    */
  def unbounded[T](): Channel[T] = Channel(Type.Unbounded)

  /** Creates a bounded channel with the specified capacity and overflow policy.
    *
    * A bounded channel controls behavior when the buffer is full based on the `onOverflow` policy:
    *   - [[OverflowStrategy.SUSPEND]]: Senders suspend until space becomes available (default)
    *   - [[OverflowStrategy.DROP_OLDEST]]: The oldest element is dropped to make space
    *   - [[OverflowStrategy.DROP_LATEST]]: The new element is discarded
    *
    * Example:
    * {{{
    * import Channel.OverflowStrategy
    *
    * // Default behavior: suspend when full
    * val channel1 = Channel.bounded[String](capacity = 10)
    *
    * // Drop oldest element when full
    * val channel2 = Channel.bounded[Int](capacity = 5, onOverflow = OverflowStrategy.DROP_OLDEST)
    *
    * Raise.run {
    *   Async.run {
    *     val producer = Async.fork {
    *       (1 to 100).foreach { i =>
    *         channel2.send(i) // Never suspends; drops oldest when full
    *       }
    *     }
    *     channel2.close()
    *
    *     channel2.foreach { msg =>
    *       println(msg) // Only receives last 5 elements
    *     }
    *   }
    * }
    * }}}
    *
    * @param capacity
    *   the maximum number of elements that can be buffered
    * @param onOverflow
    *   the strategy to use when the buffer is full (default: [[OverflowStrategy.SUSPEND]])
    * @tparam T
    *   the type of elements in the channel
    * @return
    *   a new bounded channel
    */
  def bounded[T](
      capacity: Int,
      onOverflow: OverflowStrategy = OverflowStrategy.SUSPEND
  ): Channel[T] = Channel(Type.Bounded(capacity, onOverflow))

  /** Creates a rendezvous channel (zero buffer capacity).
    *
    * A rendezvous channel has no buffer. Send and receive operations must happen simultaneously:
    * the sender suspends until a receiver is ready, and vice versa. This provides the strongest
    * synchronization between sender and receiver.
    *
    * Example:
    * {{{
    * val channel = Channel.rendezvous[String]()
    *
    * Raise.run {
    *   Async.run {
    *     val sender = Async.fork {
    *       println("Sender: waiting for receiver...")
    *       channel.send("hello") // Suspends until receiver calls receive
    *       println("Sender: message delivered!")
    *     }
    *
    *     Async.delay(1.second)
    *     println("Receiver: ready to receive...")
    *     val msg = channel.receive() // Both sender and receiver meet here
    *     println(s"Receiver: got $msg")
    *   }
    * }
    * }}}
    *
    * @tparam T
    *   the type of elements in the channel
    * @return
    *   a new rendezvous channel
    */
  def rendezvous[T](): Channel[T] = Channel(Type.Rendezvous)

  /** Creates a channel with the specified type.
    *
    * This is the general factory method that allows creating a channel with any supported buffer
    * strategy. Consider using the more specific factory methods [[unbounded]], [[bounded]], or
    * [[rendezvous]] for better readability.
    *
    * Example:
    * {{{
    * val channel1 = Channel[Int](Channel.Type.Unbounded)
    * val channel2 = Channel[String](Channel.Type.Bounded(5))
    * val channel3 = Channel[Double](Channel.Type.Rendezvous)
    * }}}
    *
    * @param channelType
    *   the type of channel to create
    * @tparam T
    *   the type of elements in the channel
    * @return
    *   a new channel with the specified type
    */
  def apply[T](channelType: Type): Channel[T] = channelType match {
    case Type.Unbounded =>
      new Channel[T](new UnboundedQueue[Any](new LinkedBlockingQueue[Any]()))
    case Type.Bounded(capacity, onOverflow) =>
      val javaQueue                = new ArrayBlockingQueue[Any](capacity)
      val queue: ChannelQueue[Any] = onOverflow match {
        case OverflowStrategy.SUSPEND     => new SuspendBoundedQueue[Any](javaQueue)
        case OverflowStrategy.DROP_OLDEST => new DropOldestBoundedQueue[Any](javaQueue)
        case OverflowStrategy.DROP_LATEST => new DropLatestBoundedQueue[Any](javaQueue)
      }
      new Channel[T](queue)
    case Type.Rendezvous =>
      new Channel[T](new RendezvousQueue[Any](new SynchronousQueue[Any]()))
  }

  /** Extension methods for [[ReceiveChannel]]. */
  extension [T](channel: ReceiveChannel[T]) {

    /** Iterates over all elements in the channel until it's closed.
      *
      * This method receives elements from the channel and applies the given function to each one.
      * It continues until the channel is closed and all buffered elements are consumed.
      *
      * Example:
      * {{{
      * val channel = Channel.unbounded[Int]()
      *
      * Raise.run {
      *   Async.run {
      *     Async.fork {
      *       (1 to 5).foreach(channel.send)
      *       channel.close()
      *     }
      *
      *     for (value <- channel) {
      *       println(s"Processing: $value")
      *     }
      *     println("All elements processed")
      *   }
      * }
      * }}}
      *
      * @param f
      *   the function to apply to each element
      * @param async
      *   the async context
      * @tparam U
      *   the return type of the function (typically Unit)
      */
    def foreach[U](f: T => U)(using Async): Unit = {
      Raise.run[ChannelClosed, Unit] {
        while (true) {
          val value = channel.receive()
          f(value)
        }
      }
    }
  }

  /** A producer is a [[SendChannel]] that can be used with context-bound syntax.
    *
    * This trait is used by the [[produce]] and [[produceWith]] functions to provide a convenient
    * DSL for channel producers. It allows you to send values and close the channel without
    * explicitly passing the channel around.
    *
    * @tparam T
    *   the type of elements produced
    * @see
    *   [[produce]] for usage examples
    */
  trait Producer[T] extends SendChannel[T] {}

  /** Companion object for [[Producer]] providing context-bound methods. */
  object Producer {

    /** Sends an element using the implicit [[Producer]] context.
      *
      * This is a convenience method for use within [[produce]] or [[produceWith]] blocks.
      *
      * Example:
      * {{{
      * Raise.run {
      *   Async.run {
      *     val channel = Channel.produce[Int] {
      *       Producer.send(1)
      *       Producer.send(2)
      *       Producer.send(3)
      *     }
      *   }
      * }
      * }}}
      *
      * @param value
      *   the element to send
      * @param p
      *   the producer context
      * @param a
      *   the async context
      * @param r
      *   the raise context
      * @tparam T
      *   the type of element
      */
    def send[T](value: T)(using p: Producer[T], a: Async, r: Raise[ChannelClosed]): Unit =
      p.send(value)

    /** Closes the channel using the implicit [[Producer]] context.
      *
      * Example:
      * {{{
      * Raise.run {
      *   Async.run {
      *     val channel = Channel.produce[Int] {
      *       Producer.send(1)
      *       Producer.send(2)
      *       Producer.close() // Explicitly close before block ends
      *     }
      *   }
      * }
      * }}}
      *
      * @param p
      *   the producer context
      * @param a
      *   the async context
      * @param r
      *   the raise context
      * @return
      *   `true` if successfully closed, `false` if already closed
      */
    def close()(using p: Producer[?], a: Async, r: Raise[ChannelClosed]): Boolean =
      p.close()
  }

  /** Creates a channel and launches a producer in a separate fiber.
    *
    * This is a convenience function that creates an unbounded channel, launches a producer
    * coroutine to send elements, and returns the receive-only side. The channel is automatically
    * closed when the producer block completes (normally or with an exception).
    *
    * Example:
    * {{{
    * import Channel.Producer
    *
    * Raise.run {
    *   Async.run {
    *     val channel = Channel.produce[Int] {
    *       (1 to 10).foreach { i =>
    *         Producer.send(i * i)
    *       }
    *       // Channel automatically closed when block completes
    *     }
    *
    *     channel.foreach { value =>
    *       println(s"Square: $value")
    *     }
    *   }
    * }
    * }}}
    *
    * @param block
    *   the producer block that sends elements
    * @param async
    *   the async context
    * @tparam T
    *   the type of elements in the channel
    * @return
    *   a [[ReceiveChannel]] for consuming the produced elements
    */
  def produce[T](block: Producer[T] ?=> Unit)(using Async): ReceiveChannel[T] =
    produceWith(Channel.Type.Unbounded)(block)

  /** Creates a channel with a specific type and launches a producer in a separate fiber.
    *
    * This function is similar to [[produce]], but allows you to specify the channel type (bounded,
    * unbounded, or rendezvous). The channel is automatically closed when the producer block
    * completes.
    *
    * Example:
    * {{{
    * import Channel.Producer
    *
    * Raise.run {
    *   Async.run {
    *     // Create a bounded producer
    *     val channel = Channel.produceWith(Channel.Type.Bounded(5)) {
    *       var count = 0
    *       while (count < 100) {
    *         Producer.send(count)
    *         count += 1
    *       }
    *     }
    *
    *     // Consume with backpressure
    *     channel.foreach { value =>
    *       Async.delay(100.millis) // Slow consumer
    *       println(value)
    *     }
    *   }
    * }
    * }}}
    *
    * @param channelType
    *   the type of channel to create (default: Unbounded)
    * @param block
    *   the producer block that sends elements
    * @param async
    *   the async context
    * @tparam T
    *   the type of elements in the channel
    * @return
    *   a [[ReceiveChannel]] for consuming the produced elements
    */
  def produceWith[T](
      channelType: Channel.Type = Channel.Type.Unbounded
  )(block: Producer[T] ?=> Unit)(using Async): ReceiveChannel[T] = {
    val channel = Channel[T](channelType)
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

/** A minimal queue interface for channel operations.
  *
  * This trait defines only the operations actually needed by the Channel implementation, avoiding
  * the complexity of the full [[java.util.concurrent.BlockingQueue]] interface.
  *
  * @tparam T
  *   the type of elements in the queue
  */
private trait ChannelQueue[T] {

  /** Inserts the specified element into this queue, waiting if necessary for space to become
    * available.
    *
    * @param e
    *   the element to add
    */
  def put(e: T): Unit

  /** Retrieves and removes the head of this queue, waiting if necessary until an element becomes
    * available.
    *
    * @return
    *   the head of this queue
    */
  def take(): T

  /** Inserts the specified element into this queue if it is possible to do so immediately without
    * violating capacity restrictions, returning `true` upon success and `false` if no space is
    * currently available.
    *
    * @param e
    *   the element to add
    * @return
    *   `true` if the element was added, `false` otherwise
    */
  def offer(e: T): Boolean

  /** Removes all elements from this queue. */
  def clear(): Unit
}

/** A standard queue wrapper for unbounded channels.
  *
  * Wraps a [[java.util.concurrent.LinkedBlockingQueue]] which has unlimited capacity.
  *
  * @param delegate
  *   the underlying linked blocking queue
  * @tparam T
  *   the type of elements in the queue
  */
private class UnboundedQueue[T](private val delegate: LinkedBlockingQueue[T])
    extends ChannelQueue[T] {
  override def put(e: T): Unit      = delegate.put(e)
  override def take(): T            = delegate.take()
  override def offer(e: T): Boolean = delegate.offer(e)
  override def clear(): Unit        = delegate.clear()
}

/** A standard queue wrapper for bounded channels with suspend behavior.
  *
  * Wraps a [[java.util.concurrent.ArrayBlockingQueue]] which blocks when full.
  *
  * @param delegate
  *   the underlying array blocking queue
  * @tparam T
  *   the type of elements in the queue
  */
private class SuspendBoundedQueue[T](private val delegate: ArrayBlockingQueue[T])
    extends ChannelQueue[T] {
  override def put(e: T): Unit      = delegate.put(e)
  override def take(): T            = delegate.take()
  override def offer(e: T): Boolean = delegate.offer(e)
  override def clear(): Unit        = delegate.clear()
}

/** A standard queue wrapper for rendezvous channels.
  *
  * Wraps a [[java.util.concurrent.SynchronousQueue]] which has no capacity and requires direct
  * handoff.
  *
  * @param delegate
  *   the underlying synchronous queue
  * @tparam T
  *   the type of elements in the queue
  */
private class RendezvousQueue[T](private val delegate: SynchronousQueue[T])
    extends ChannelQueue[T] {
  override def put(e: T): Unit      = delegate.put(e)
  override def take(): T            = delegate.take()
  override def offer(e: T): Boolean = delegate.offer(e)
  override def clear(): Unit        = delegate.clear()
}

/** A queue that drops the oldest element when full instead of blocking.
  *
  * This queue is used for bounded channels with [[Channel.OverflowStrategy.DROP_OLDEST]] policy.
  * When the queue is full and a new element is added, the oldest element is automatically removed
  * to make space. Wraps a [[java.util.concurrent.ArrayBlockingQueue]].
  *
  * @param delegate
  *   the underlying array blocking queue
  * @tparam T
  *   the type of elements in the queue
  */
private class DropOldestBoundedQueue[T](private val delegate: ArrayBlockingQueue[T])
    extends ChannelQueue[T] {
  override def put(e: T): Unit = {
    while (!delegate.offer(e)) {
      delegate.poll()
    }
  }

  override def take(): T            = delegate.take()
  override def offer(e: T): Boolean = delegate.offer(e)
  override def clear(): Unit        = delegate.clear()
}

/** A queue that drops the newest element when full instead of blocking.
  *
  * This queue is used for bounded channels with [[Channel.OverflowStrategy.DROP_LATEST]] policy.
  * When the queue is full and a new element is added, the new element is simply discarded. Wraps a
  * [[java.util.concurrent.ArrayBlockingQueue]].
  *
  * @param delegate
  *   the underlying array blocking queue
  * @tparam T
  *   the type of elements in the queue
  */
private class DropLatestBoundedQueue[T](private val delegate: ArrayBlockingQueue[T])
    extends ChannelQueue[T] {
  override def put(e: T): Unit = {
    delegate.offer(e)
    ()
  }

  override def take(): T            = delegate.take()
  override def offer(e: T): Boolean = delegate.offer(e)
  override def clear(): Unit        = delegate.clear()
}

/** A channel implementation that supports both sending and receiving operations.
  *
  * This class implements both [[Channel.SendChannel]] and [[Channel.ReceiveChannel]], providing
  * full bidirectional channel operations. Channels are backed by a [[ChannelQueue]] that wraps
  * Java's [[BlockingQueue]] implementations and provides thread-safe concurrent access.
  *
  * The channel maintains internal state to track whether it's open, closed, or cancelled:
  *   - '''Open''': Normal operation; send and receive work normally
  *   - '''Closed''': No more sends allowed; remaining buffered elements can still be received
  *   - '''Cancelled''': All buffered elements are cleared; operations fail immediately
  *
  * Example usage:
  * {{{
  * val channel = Channel.bounded[String](10)
  *
  * Raise.run {
  *   Async.run {
  *     // Producer fiber
  *     Async.fork {
  *       channel.send("Hello")
  *       channel.send("World")
  *       channel.close()
  *     }
  *
  *     // Consumer fiber
  *     println(channel.receive()) // "Hello"
  *     println(channel.receive()) // "World"
  *     channel.receive() // Raises ChannelClosed
  *   }
  * }
  * }}}
  *
  * @param queue
  *   the underlying queue for buffering elements
  * @tparam T
  *   the type of elements in the channel
  */
class Channel[T] private (private val queue: ChannelQueue[Any])
    extends Channel.ReceiveChannel[T],
      Channel.SendChannel[T] {

  /** Internal status of the channel. */
  private enum Status {
    case Open, Close, Cancelled
  }

  /** Marker object to indicate closed state in the queue. */
  private object ClosedMarker

  private val status = new AtomicReference(Status.Open)

  /** Receives an element from the channel, suspending if necessary.
    *
    * This operation blocks the current thread until an element is available or the channel is
    * closed. If the channel is closed and empty, it raises [[Channel.ChannelClosed]].
    *
    * @param async
    *   the async context
    * @param raise
    *   the raise context for handling errors
    * @return
    *   the received element
    * @throws Channel.ChannelClosed
    *   if the channel is closed and empty
    */
  override def receive()(using Async, Raise[ChannelClosed]): T = {
    val element = queue.take()
    element match {
      case ClosedMarker =>
        queue.offer(ClosedMarker)
        Raise.raise(ChannelClosed)
      case value =>
        if (status.get() == Status.Close) {
          queue.offer(ClosedMarker)
        }
        value.asInstanceOf[T]
    }
  }

  /** Sends an element to the channel, suspending if necessary.
    *
    * This operation blocks the current thread if the channel's buffer is full (for bounded
    * channels) or until a receiver is ready (for rendezvous channels).
    *
    * @param value
    *   the element to send
    * @param async
    *   the async context
    * @param raise
    *   the raise context for handling errors
    * @throws Channel.ChannelClosed
    *   if the channel is closed
    */
  override def send(value: T)(using Async, Raise[ChannelClosed]): Unit =
    status.get() match {
      case Status.Cancelled =>
        Thread.currentThread().interrupt()
      case Status.Close =>
        Raise.raise(ChannelClosed)
      case Status.Open =>
        queue.put(value)
    }

  /** Closes the channel, preventing further sends.
    *
    * After closing, attempts to send will raise [[Channel.ChannelClosed]]. Receivers can still
    * consume buffered elements until the queue is empty.
    *
    * @return
    *   `true` if the channel was successfully closed, `false` if already closed or cancelled
    */
  override def close(): Boolean = {
    if (status.compareAndSet(Status.Open, Status.Close)) {
      queue.offer(ClosedMarker)
      true
    } else {
      false
    }
  }

  /** Cancels the channel and clears all buffered elements.
    *
    * This operation immediately discards all buffered elements and marks the channel as cancelled.
    * Ongoing send/receive operations are interrupted.
    *
    * @param async
    *   the async context
    */
  override def cancel()(using Async): Unit = {
    status.set(Status.Cancelled)
    queue.clear()
    queue.offer(ClosedMarker)
  }
}
