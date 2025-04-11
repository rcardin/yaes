package in.rcard.yaes

import java.time.Instant
import java.lang.System as JSystem
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationLong

/** Companion object for the [[Clock]] capability, providing utility methods and handlers.
  *
  * This object contains methods to run Clock operations to get the current time and a monotonic
  * duration in an effectful way.
  *
  * Example:
  * {{{
  * def printCurrentTime()(using Clock, Output): Unit = {
  *   val currentTime = Clock.now
  *   Output.printLn(s"Current time: $currentTime")
  * }}}
  */
object Clock {

  type Clock = Yaes[Clock.Unsafe]

  /** Lifts a block of code into the Clock effect.
    *
    * @param block
    *   The code block to be lifted into the Clock effect
    * @param clock
    *   The Clock capability provided through context parameters
    * @return
    *   The block with the Clock capability
    */
  def apply[A](block: => A)(using clock: Clock): A = block

  /** Gets the current calendar time as an [[Instant]] (wall-clock time).
    *
    * @param clock
    *   The Clock capability provided through context parameters
    * @return
    *   The current time as an [[Instant]]
    * @see
    *   [[nowMonotonic]] for measuring elapsed time between events
    */
  def now(using clock: Clock): Instant = clock.unsafe.now

  /** Gets the current monotonic duration. Monotonic time is a time measurement that always
    * increases and never goes backward. It's independent of the system clock, so it's not affected
    * by changes to the system clock.
    *
    * @param clock
    *   The Clock capability provided through context parameters
    * @return
    *   The current monotonic duration as a [[Duration]]
    */
  def nowMonotonic(using clock: Clock): Duration = clock.unsafe.nowMonotonic

  /** Runs a program that requires Clock capability.
    *
    * This method handles the Clock effect by supplying the implementation that directly interfaces
    * with the system clock.
    *
    * Example usage:
    * {{{
    *   val currentTime: Instant = Clock.run {
    *     Clock.now
    *   }
    * }}}
    *
    * @param block
    *   The code block to be run with the Clock capability
    * @return
    *   The result of the code block
    * @see
    *   [[now]] for calendar time/date operations
    */
  def run[A](block: Clock ?=> A): A = {
    val handler = new Yaes.Handler[Clock.Unsafe, A, A] {
      override def handle(program: Clock ?=> A): A = program(using Yaes(Clock.unsafe))
    }
    Yaes.handle(block)(using handler)
  }

  private val unsafe: Unsafe = new Unsafe {
    def now: Instant           = Instant.now()
    def nowMonotonic: Duration = JSystem.nanoTime().nanos
  }

  /** Unsafe implementation of the Clock capability.
    *
    * The capability provides access to methods to get the current time and a monotonic duration.
    * Monotonic time is a time measurement that always increases and never goes backward. It's
    * independent of the system clock, so it's not affected by changes to the system clock.
    */
  trait Unsafe {
    def now: Instant
    def nowMonotonic: Duration
  }
}
