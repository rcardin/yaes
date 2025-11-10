package in.rcard.yaes

import in.rcard.yaes.Channel.ChannelClosed
import in.rcard.yaes.Channel.SendChannel

import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

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
  def apply[T](channelType: Type): Channel[T] =
    channelType match {
      case Type.Unbounded                     => new UnboundedChannel[T]()
      case Type.Bounded(capacity, onOverflow) => new BoundedChannel[T](capacity, onOverflow)
      case Type.Rendezvous                    => new RendezvousChannel[T]()
    }

  /** Implementation of an unbounded channel with unlimited buffer capacity.
    *
    * This channel uses a [[LinkedList]] as the backing queue, which can grow without bounds.
    * Senders never suspend in this implementation, making it suitable for scenarios where
    * throughput is prioritized over memory constraints.
    *
    * @tparam T
    *   the type of elements in the channel
    */
  private class UnboundedChannel[T] extends Channel[T] {

    private val queue: Queue[T] = new LinkedList[T]

    override def receive()(using Async, Raise[ChannelClosed]): T = {
      lock.lock()
      try {
        while (queue.isEmpty) {
          if (closed) {
            Raise.raise(ChannelClosed)
          }
          notEmpty.await()
        }
        if (cancelled || (queue.isEmpty && closed)) {
          Raise.raise(ChannelClosed)
        }
        val value = queue.poll()
        value
      } finally {
        lock.unlock()
      }
    }

    override def send(value: T)(using Async, Raise[ChannelClosed]): Unit = {
      lock.lock()
      try {
        if (cancelled) {
          Thread.currentThread().interrupt()
          return ()
        } else if (closed) {
          Raise.raise(ChannelClosed)
        } else {
          queue.offer(value)
          notEmpty.signal()
        }
      } finally {
        lock.unlock()
      }
    }
  }

  /** Implementation of a rendezvous channel with zero buffer capacity.
    *
    * This channel has no buffer and requires sender and receiver to meet (rendezvous)
    * simultaneously. The sender suspends until a receiver is ready, and vice versa. This provides
    * the strongest synchronization guarantee between communicating parties.
    *
    * @tparam T
    *   the type of elements in the channel
    */
  private class RendezvousChannel[T] extends Channel[T] {

    private var item: T                    = null.asInstanceOf[T]
    private var hasItem: Boolean = false

    override def receive()(using Async, Raise[ChannelClosed]): T = {
      lock.lock()
      try {
        while (!hasItem) {
          if (closed) {
            Raise.raise(ChannelClosed)
          }
          notEmpty.await()
        }
        if (cancelled || (!hasItem && closed)) {
          Raise.raise(ChannelClosed)
        }
        val value = item
        item = null.asInstanceOf[T]
        hasItem = false
        notFull.signal()
        value
      } finally {
        lock.unlock()
      }
    }

    override def send(value: T)(using Async, Raise[ChannelClosed]): Unit = {
      lock.lock()
      try {
        if (cancelled) {
          Thread.currentThread().interrupt()
          return ()
        } else if (closed) {
          Raise.raise(ChannelClosed)
        }

        while (hasItem) {
          if (closed) {
            Raise.raise(ChannelClosed)
          }
          notFull.await()
        }

        if (cancelled) {
          Thread.currentThread().interrupt()
          return ()
        } else if (closed) {
          Raise.raise(ChannelClosed)
        }

        item = value
        hasItem = true
        notEmpty.signal()
        while (hasItem) {
          if (closed) {
            Raise.raise(ChannelClosed)
          }
          if (cancelled) {
            Thread.currentThread().interrupt()
            return ()
          }
          notFull.await()
        }
      } finally {
        lock.unlock()
      }
    }
  }

  /** Implementation of a bounded channel with fixed buffer capacity and configurable overflow
    * handling.
    *
    * This channel limits the number of buffered elements to the specified capacity. When the buffer
    * is full, the behavior depends on the [[OverflowStrategy]]:
    *   - [[OverflowStrategy.SUSPEND]]: Senders suspend until space becomes available
    *   - [[OverflowStrategy.DROP_OLDEST]]: The oldest buffered element is removed
    *   - [[OverflowStrategy.DROP_LATEST]]: The new element is discarded
    *
    * @param capacity
    *   the maximum number of elements that can be buffered
    * @param onOverflow
    *   the strategy to apply when the buffer is full
    * @tparam T
    *   the type of elements in the channel
    */
  private class BoundedChannel[T](capacity: Int, onOverflow: Channel.OverflowStrategy)
      extends Channel[T] {

    private val queue: Queue[T] = new LinkedList[T]

    override def receive()(using Async, Raise[ChannelClosed]): T = {
      lock.lock()
      try {
        while (queue.isEmpty) {
          if (closed) {
            Raise.raise(ChannelClosed)
          }
          notEmpty.await()
        }

        if (cancelled || (queue.isEmpty && closed)) {
          Raise.raise(ChannelClosed)
        }

        val value = queue.poll()

        if (onOverflow == OverflowStrategy.SUSPEND) {
          notFull.signal()
        }
        value
      } finally {
        lock.unlock()
      }
    }

    override def send(value: T)(using Async, Raise[ChannelClosed]): Unit = {
      lock.lock()
      try {
        if (cancelled) {
          Thread.currentThread().interrupt()
          return ()
        } else if (closed) {
          Raise.raise(ChannelClosed)
        } else {

          onOverflow match {
            case OverflowStrategy.SUSPEND     => sendSuspend(value)
            case OverflowStrategy.DROP_LATEST => sendDropLatest(value)
            case OverflowStrategy.DROP_OLDEST => sendDropOldest(value)
          }
        }
      } finally {
        lock.unlock()
      }
    }

    /** Sends a value using the SUSPEND overflow strategy.
      *
      * This method suspends the sender when the buffer is full, waiting until space becomes
      * available. This provides backpressure to prevent fast producers from overwhelming slow
      * consumers.
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
    private def sendSuspend(value: T)(using Async, Raise[ChannelClosed]): Unit = {
      while (queue.size() >= capacity) {
        if (cancelled) {
          Thread.currentThread().interrupt()
          return ()
        }
        if (closed) {
          Raise.raise(ChannelClosed)
        }
        notFull.await()
      }

      if (cancelled) {
        Thread.currentThread().interrupt()
        return ()
      }
      if (closed) {
        Raise.raise(ChannelClosed)
      }

      queue.offer(value)
      notEmpty.signal()
    }

    /** Sends a value using the DROP_OLDEST overflow strategy.
      *
      * When the buffer is full, this method removes the oldest buffered element to make space for
      * the new value. The send operation completes immediately without suspending.
      *
      * @param value
      *   the element to send
      * @param async
      *   the async context
      * @param raise
      *   the raise context for handling [[ChannelClosed]] errors
      */
    private def sendDropOldest(value: T)(using Async, Raise[ChannelClosed]): Unit = {
      if (queue.size() >= capacity) {
        queue.poll()
      }

      queue.offer(value)
      notEmpty.signal()
    }

    /** Sends a value using the DROP_LATEST overflow strategy.
      *
      * When the buffer is full, this method discards the new value and keeps the buffer unchanged.
      * The send operation completes immediately without suspending.
      *
      * @param value
      *   the element to send (may be discarded)
      * @param async
      *   the async context
      * @param raise
      *   the raise context for handling [[ChannelClosed]] errors
      */
    private def sendDropLatest(value: T)(using Async, Raise[ChannelClosed]): Unit = {
      if (queue.size() < capacity) {
        queue.offer(value)
        notEmpty.signal()
      }
    }
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

/** A channel implementation that supports both sending and receiving operations.
  *
  * This class implements both [[Channel.SendChannel]] and [[Channel.ReceiveChannel]], providing
  * full bidirectional channel operations.
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
  * @tparam T
  *   the type of elements in the channel
  */
abstract class Channel[T] extends Channel.ReceiveChannel[T], Channel.SendChannel[T] {

  /** Synchronization lock for thread-safe access to channel state. */
  protected val lock = new ReentrantLock()

  /** Condition variable signaled when elements are added to the channel. */
  protected val notEmpty = lock.newCondition()

  /** Condition variable signaled when elements are removed from the channel. */
  protected val notFull = lock.newCondition()

  /** Flag indicating whether the channel has been closed. */
  @volatile protected var closed = false

  /** Flag indicating whether the channel has been cancelled. */
  @volatile protected var cancelled = false

  /** Closes the channel, preventing further sends.
    *
    * After closing, attempts to send will raise [[Channel.ChannelClosed]]. Receivers can still
    * consume buffered elements until the queue is empty.
    *
    * @return
    *   `true` if the channel was successfully closed, `false` if already closed or cancelled
    */
  override def close(): Boolean = {
    lock.lock()
    try {
      if (closed) {
        return false
      }
      closed = true
      notFull.signalAll()
      notEmpty.signalAll()
    } finally {
      lock.unlock()
    }
    true
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
    lock.lock()
    try {
      closed = true
      cancelled = true
      notFull.signalAll()
      notEmpty.signalAll()
    } finally {
      lock.unlock()
    }
  }
}
