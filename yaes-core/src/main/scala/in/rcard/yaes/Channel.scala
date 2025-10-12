package in.rcard.yaes

import in.rcard.yaes.Async.Async

object Channel {

  trait SendChannel[T] {
    def send(value: T)(using Async): Unit
    def close(): Unit
  }

  trait ReceiveChannel[T] {
    def receive()(using Async): T
  }
}
