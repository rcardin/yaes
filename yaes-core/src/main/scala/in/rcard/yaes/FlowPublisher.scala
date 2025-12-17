package in.rcard.yaes

import java.util.concurrent.Flow.{Publisher, Subscriber, Subscription}
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

/** Converts a YAES Flow to a Reactive Streams Publisher.
  *
  * This implementation bridges YAES Flow (push-based cold streams) with Reactive Streams Publisher
  * (pull-based with backpressure). It collects the flow in a separate fiber while respecting
  * subscriber demand.
  *
  * Example:
  * {{{
  * val flow = Flow(1, 2, 3, 4, 5)
  * Async.run {
  *   val publisher = flow.asPublisher()
  *   publisher.subscribe(new Subscriber[Int] {
  *     var subscription: Subscription = _
  *
  *     override def onSubscribe(s: Subscription): Unit = {
  *       subscription = s
  *       s.request(10)  // Request elements
  *     }
  *
  *     override def onNext(item: Int): Unit = println(s"Received: $item")
  *     override def onError(t: Throwable): Unit = println(s"Error: $t")
  *     override def onComplete(): Unit = println("Completed")
  *   })
  * }
  * }}}
  *
  * @param flow
  *   The Flow to convert to Publisher
  * @param bufferCapacity
  *   Channel capacity for buffering between Flow and Subscriber
  * @param async
  *   Async effect context for fiber management
  * @tparam A
  *   Element type
  */
class FlowPublisher[A](
    flow: Flow[A],
    bufferCapacity: Channel.Type = Channel.Type.Bounded(16, Channel.OverflowStrategy.SUSPEND)
)(using async: Async)
    extends Publisher[A] {

  override def subscribe(subscriber: Subscriber[? >: A]): Unit = {
    require(subscriber != null, "Subscriber cannot be null")

    val channel      = Channel[A](bufferCapacity)
    val demand       = new AtomicLong(0)
    val demandSignal = new Semaphore(0)
    val cancelled    = new AtomicBoolean(false)
    val terminated   = new AtomicBoolean(false)

    // Fork collector fiber
    val collectorFiber = Async.fork("flow-collector") {
      try {
        Raise.ignore {
          flow.collect { value =>
            if (!cancelled.get()) {
              channel.send(value)
            }
          }
        }
      } catch {
        case t: Throwable =>
          // Unexpected error from Flow - only report if not cancelled
          // (Reactive Streams spec: no signals after cancellation)
          if (!cancelled.get() && terminated.compareAndSet(false, true)) {
            subscriber.onError(t)
          }
      } finally {
        channel.close()
      }
    }

    // Fork emitter fiber
    val emitterFiber: Fiber[Unit] = Async.fork("subscriber-emitter") {
      val subscription = new FlowSubscription(
        subscriber,
        channel,
        demand,
        demandSignal,
        cancelled,
        terminated
      )

      subscriber.onSubscribe(subscription)

      var running = true
      while (running && !cancelled.get()) {
        // Always try to receive next element
        val receiveResult: Either[Channel.ChannelClosed.type, A] = Raise.either {
          channel.receive()
        }

        receiveResult match {
          case Right(value) =>
            if (value != null) {
              // Wait for demand before delivering
              while (demand.get() == 0 && !cancelled.get()) {
                demandSignal.acquire()
              }

              // If not cancelled, deliver the element
              if (!cancelled.get()) {
                try {
                  subscriber.onNext(value)
                  demand.decrementAndGet()
                } catch {
                  case t: Throwable =>
                    running = false
                    if (terminated.compareAndSet(false, true)) {
                      subscriber.onError(t)
                    }
                }
              } else {
                running = false
              }
            } else {
              running = false
              if (terminated.compareAndSet(false, true)) {
                subscriber.onError(new NullPointerException("Flow emitted null element"))
              }
            }
          case Left(Channel.ChannelClosed) =>
            running = false
            if (terminated.compareAndSet(false, true) && !cancelled.get()) {
              subscriber.onComplete()
            }
        }
      }
    }
  }

  private class FlowSubscription(
      subscriber: Subscriber[? >: A],
      channel: Channel[A],
      demand: AtomicLong,
      demandSignal: Semaphore,
      cancelled: AtomicBoolean,
      terminated: AtomicBoolean
  ) extends Subscription {

    override def request(n: Long): Unit = {
      if (n <= 0) {
        val ex = new IllegalArgumentException(s"Rule 3.9: request($n) must be > 0")
        if (terminated.compareAndSet(false, true)) {
          subscriber.onError(ex)
        }
        cancel()
      } else {
        demand.addAndGet(n)
        demandSignal.release()
      }
    }

    override def cancel(): Unit = {
      if (cancelled.compareAndSet(false, true)) {
        channel.cancel()
        demandSignal.release()  // Wake up emitter if waiting for demand
      }
    }
  }
}

object FlowPublisher {

  /** Creates a Publisher from a Flow with default buffer capacity.
    *
    * @param flow
    *   The Flow to convert
    * @param async
    *   Async effect context
    * @return
    *   Publisher that emits elements from the Flow
    */
  def fromFlow[A](flow: Flow[A])(using async: Async): Publisher[A] =
    new FlowPublisher(flow)

  /** Creates a Publisher from a Flow with custom buffer capacity.
    *
    * @param flow
    *   The Flow to convert
    * @param bufferCapacity
    *   Channel capacity for buffering
    * @param async
    *   Async effect context
    * @return
    *   Publisher that emits elements from the Flow
    */
  def fromFlow[A](flow: Flow[A], bufferCapacity: Channel.Type)(using async: Async): Publisher[A] =
    new FlowPublisher(flow, bufferCapacity)

  /** Extension method to convert Flow to Publisher. */
  extension [A](flow: Flow[A]) {

    /** Converts this Flow to a Reactive Streams Publisher.
      *
      * @param bufferCapacity
      *   Channel capacity for buffering (default: Bounded(16, SUSPEND))
      * @param async
      *   Async effect context
      * @return
      *   Publisher that emits elements from this Flow
      */
    def asPublisher(
        bufferCapacity: Channel.Type = Channel.Type.Bounded(16, Channel.OverflowStrategy.SUSPEND)
    )(using async: Async): Publisher[A] =
      new FlowPublisher(flow, bufferCapacity)
  }
}
