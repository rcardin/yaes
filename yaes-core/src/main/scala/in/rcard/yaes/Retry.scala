package in.rcard.yaes

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
  }
}
