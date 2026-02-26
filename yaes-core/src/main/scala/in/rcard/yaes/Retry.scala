package in.rcard.yaes

import java.util.concurrent.ThreadLocalRandom
import scala.concurrent.duration.Duration

/** A retry policy that computes the delay for each attempt.
  * Attempts are 1-indexed: attempt 1 is the first retry after the initial failure.
  *
  * Example:
  * {{{
  * // Fixed delay of 500ms, up to 3 total executions
  * val schedule = Schedule.fixed(500.millis).attempts(3)
  * schedule.delay(1) // Some(500.millis)
  * schedule.delay(2) // Some(500.millis)
  * schedule.delay(3) // None — stop retrying
  * }}}
  */
trait Schedule {

  /** Returns the delay before the next attempt, or None to stop retrying.
    *
    * @param attempt the retry attempt number (1-indexed)
    * @return Some(delay) to retry after the given delay, or None to stop
    */
  def delay(attempt: Int): Option[Duration]
}

object Schedule {

  /** Fixed delay between each retry. Retries forever unless combined with [[attempts]].
    *
    * Example:
    * {{{
    * val schedule = Schedule.fixed(500.millis)
    * schedule.delay(1)   // Some(500.millis)
    * schedule.delay(100) // Some(500.millis)
    * }}}
    *
    * @param delay the constant delay between retries
    * @return a schedule with fixed delay
    */
  def fixed(interval: Duration): Schedule = new Schedule {
    def delay(attempt: Int): Option[Duration] =
      if attempt <= 0 then None
      else Some(interval)
  }

  /** Exponential backoff: initial * factor^(attempt-1), capped at max.
    *
    * Example:
    * {{{
    * val schedule = Schedule.exponential(100.millis, factor = 2.0, max = 5.seconds)
    * schedule.delay(1) // Some(100.millis)
    * schedule.delay(2) // Some(200.millis)
    * schedule.delay(3) // Some(400.millis)
    * }}}
    *
    * @param initial the delay before the first retry
    * @param factor the multiplier applied on each attempt (default 2.0)
    * @param max the maximum delay cap (default Duration.Inf)
    * @return a schedule with exponential backoff
    */
  def exponential(
      initial: Duration,
      factor: Double = 2.0,
      max: Duration = Duration.Inf
  ): Schedule = new Schedule {
    def delay(attempt: Int): Option[Duration] =
      if attempt <= 0 then None
      else {
        val computed = initial * Math.pow(factor, (attempt - 1).toDouble)
        if max.isFinite && computed > max then Some(max)
        else Some(computed)
      }
  }

  extension (self: Schedule) {

    /** Limits the total number of executions (1 initial + N-1 retries).
      * `attempts(3)` means: 1 initial try + up to 2 retries = 3 total executions.
      *
      * Example:
      * {{{
      * val schedule = Schedule.fixed(100.millis).attempts(3)
      * schedule.delay(1) // Some(100.millis) — 1st retry
      * schedule.delay(2) // Some(100.millis) — 2nd retry
      * schedule.delay(3) // None — stop (3 total executions reached)
      * }}}
      *
      * @param n the maximum number of total executions (must be >= 0)
      * @return a schedule that stops after n total executions
      */
    def attempts(n: Int): Schedule = new Schedule {
      def delay(attempt: Int): Option[Duration] =
        // n total executions = n-1 retries, so stop when attempt >= n
        if attempt >= n then None
        else self.delay(attempt)
    }

    /** Adds random jitter to each delay.
      *
      * Example:
      * {{{
      * val schedule = Schedule.fixed(1.second).jitter(0.5)
      * // Delays will be random in [500ms, 1500ms]
      * }}}
      *
      * @param factor jitter range as a fraction of the delay (0.0 to 1.0).
      *               A factor of 0.5 on a 1s delay produces delays in [500ms, 1500ms].
      * @return a schedule with random jitter applied to each delay
      */
    def jitter(factor: Double): Schedule = new Schedule {
      def delay(attempt: Int): Option[Duration] =
        self.delay(attempt).map { d =>
          if factor == 0.0 then d
          else {
            val millis    = d.toMillis.toDouble
            val minMillis = millis * (1.0 - factor)
            val maxMillis = millis * (1.0 + factor)
            val jittered  = ThreadLocalRandom.current().nextDouble(minMillis, maxMillis)
            Duration.fromNanos((jittered * 1_000_000).toLong)
          }
        }
    }
  }
}
