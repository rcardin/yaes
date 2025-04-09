package in.rcard.yaes

import java.time.Instant
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationLong

/** Companion object for the [[Clock]] capability, providing utility methods and handlers.
  */
object Clock {

  type Clock = Yaes[Clock.Unsafe]

  def now(using clock: Clock): Instant           = clock.unsafe.now
  def nowMonotonic(using clock: Clock): Duration = ???

  def run[A](block: Clock ?=> A): A = {
    val handler = new Yaes.Handler[Clock.Unsafe, A, A] {
      override def handle(program: Clock ?=> A): A = program(using Yaes(Clock.unsafe))
    }
    Yaes.handle(block)(using handler)
  }

  private val unsafe: Unsafe = new Unsafe {
    def now: Instant           = Instant.now()
    def nowMonotonic: Duration = System.nanoTime().nanos
  }

  trait Unsafe {
    def now: Instant
    def nowMonotonic: Duration
  }
}
