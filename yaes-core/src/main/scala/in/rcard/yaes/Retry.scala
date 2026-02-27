package in.rcard.yaes

import scala.annotation.tailrec
import scala.concurrent.duration.Duration

/** A retry policy that computes the delay for each attempt. Attempts are 1-indexed: attempt 1 is
  * the first retry after the initial failure.
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
    * @param attempt
    *   the retry attempt number (1-indexed)
    * @return
    *   Some(delay) to retry after the given delay, or None to stop
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
    * @param interval
    *   the constant delay between retries
    * @return
    *   a schedule with fixed delay
    */
  def fixed(interval: Duration): Schedule = {
    // Clamp invalid inputs: negative or non-finite intervals default to Duration.Zero
    val safeInterval =
      if interval.isFinite && interval >= Duration.Zero then interval
      else Duration.Zero

    new Schedule {
      def delay(attempt: Int): Option[Duration] =
        if attempt <= 0 then None
        else Some(safeInterval)
    }
  }

  /** Exponential backoff: initial * factor^(attempt-1), capped at max.
    *
    * The delay for attempt `n` is computed as `initial * factor^(n - 1)`. When this computed delay
    * overflows to a non-finite value (e.g., due to very large attempt numbers), the schedule
    * returns `max` if it is finite, or a large finite cap (`Duration.fromNanos(Long.MaxValue)`)
    * if `max` is infinite.
    *
    * Example:
    * {{{
    * val schedule = Schedule.exponential(100.millis, factor = 2.0, max = 5.seconds)
    * schedule.delay(1) // Some(100.millis)
    * schedule.delay(2) // Some(200.millis)
    * schedule.delay(3) // Some(400.millis)
    * }}}
    *
    * @param initial
    *   the delay before the first retry. Negative or non-finite values are clamped to
    *   `Duration.Zero`.
    * @param factor
    *   the multiplier applied on each attempt (default 2.0). Non-positive, `NaN`, or infinite
    *   values default to `2.0`.
    * @param max
    *   the maximum delay cap (default `Duration.Inf`, meaning no cap). Negative values are clamped
    *   to `Duration.Zero`.
    * @return
    *   a schedule with exponential backoff
    */
  def exponential(
      initial: Duration,
      factor: Double = 2.0,
      max: Duration = Duration.Inf
  ): Schedule = {
    // Clamp invalid inputs to sensible defaults
    val safeInitial =
      if initial.isFinite && initial >= Duration.Zero then initial
      else Duration.Zero

    val safeFactor =
      if factor > 0.0 && !factor.isInfinity && !factor.isNaN then factor
      else 2.0

    val safeMax =
      if max == Duration.Inf then Duration.Inf
      else if max.isFinite && max >= Duration.Zero then max
      else Duration.Zero

    new Schedule {
      private val finiteCap: Duration = Duration.fromNanos(Long.MaxValue)

      def delay(attempt: Int): Option[Duration] =
        if attempt <= 0 then None
        else {
          val computed = safeInitial * Math.pow(safeFactor, (attempt - 1).toDouble)
          if !computed.isFinite then Some(if safeMax.isFinite then safeMax else finiteCap)
          else if safeMax.isFinite && computed > safeMax then Some(safeMax)
          else Some(computed)
        }
    }
  }

  extension (self: Schedule) {

    /** Limits the total number of executions (1 initial + N-1 retries). `attempts(3)` means: 1
      * initial try + up to 2 retries = 3 total executions.
      *
      * Note that the schedule only controls retries, not the initial execution. The block always
      * runs at least once regardless of `n`. Therefore `attempts(0)` and `attempts(1)` both result
      * in no retries — the block executes exactly once and any error is re-raised immediately.
      *
      * Example:
      * {{{
      * val schedule = Schedule.fixed(100.millis).attempts(3)
      * schedule.delay(1) // Some(100.millis) — 1st retry
      * schedule.delay(2) // Some(100.millis) — 2nd retry
      * schedule.delay(3) // None — stop (3 total executions reached)
      * }}}
      *
      * @param n
      *   the maximum number of total executions. Values <= 1 mean no retries.
      * @return
      *   a schedule that stops after n total executions
      */
    def attempts(n: Int): Schedule = new Schedule {
      def delay(attempt: Int): Option[Duration] =
        // n total executions = n-1 retries, so stop when attempt >= n
        if attempt >= n then None
        else self.delay(attempt)
    }

    /** Adds random jitter to each delay. The jittered delay is uniformly distributed in
      * `[delay * (1 - factor), delay * (1 + factor)]`, clamped so it never goes below zero.
      *
      * Typical factors are in `[0.0, 1.0]`. Values greater than 1.0 are accepted — the lower
      * bound of the jitter range is simply floored at 0ms (e.g., factor 1.5 on a 1s delay
      * produces delays in `[0ms, 2500ms]`). Negative factors are treated as 0.0 (no jitter).
      *
      * This method requires the `Random` effect in scope. The `Random` instance is captured at
      * call time, so the resulting schedule can be used without `Random` in scope later.
      *
      * Example:
      * {{{
      * val schedule = Schedule.fixed(1.second).jitter(0.5)
      * // Delays will be random in [500ms, 1500ms]
      * }}}
      *
      * @param factor
      *   jitter range as a fraction of the delay. A factor of 0.5 on a 1s delay produces delays
      *   in [500ms, 1500ms]. Negative values are treated as 0.0.
      * @return
      *   a schedule with random jitter applied to each delay
      */
    def jitter(factor: Double)(using Random): Schedule = new Schedule {
      // Treat non-finite values as no jitter; clamp negatives to 0.0
      private val effectiveFactor =
        if factor.isNaN || factor.isInfinite then 0.0
        else math.max(0.0, factor)

      def delay(attempt: Int): Option[Duration] =
        self.delay(attempt).map { d =>
          val millis = math.max(0.0, d.toMillis.toDouble)
          if effectiveFactor == 0.0 || millis == 0.0 then d
          else {
            val minMillis      = math.max(0.0, millis * (1.0 - effectiveFactor))
            val maxMillis      = millis * (1.0 + effectiveFactor)
            val jittered       = minMillis + Random.nextDouble * (maxMillis - minMillis)
            val NanosPerMilli  = 1_000_000L
            val jitteredNanos  = (jittered * NanosPerMilli).toLong
            Duration.fromNanos(jitteredNanos)
          }
        }
    }
  }
}

/** A handler that retries a failing block according to a [[Schedule]] policy.
  *
  * `Retry` is not an effect — it orchestrates existing effects ([[Raise]] for detecting failure,
  * [[Async]] for delays). The block being retried never calls `Retry.something()` — it just runs,
  * succeeds, or fails.
  *
  * Only errors raised via `Raise[E]` trigger a retry. Other effect types in scope are not
  * intercepted.
  *
  * Example:
  * {{{
  * val result: Either[DbError, User] = Async.run {
  *   Raise.either {
  *     Retry[DbError](Schedule.fixed(500.millis).attempts(3)) {
  *       db.findUser(42)
  *     }
  *   }
  * }
  * }}}
  */
object Retry {

  /** Retries the block on typed errors of type `E` according to the given schedule.
    *
    * Only errors raised via `Raise[E]` trigger a retry. Other effects and error types in scope are
    * not intercepted.
    *
    * If all attempts are exhausted, the last error is re-raised via the outer `Raise[E]`.
    *
    * Example:
    * {{{
    * // Retry HttpError with exponential backoff
    * Retry[HttpError](
    *   Schedule.exponential(100.millis, factor = 2, max = 5.seconds)
    *     .jitter(0.5)
    *     .attempts(5)
    * ) {
    *   httpClient.get("/api/data")
    * }
    * }}}
    *
    * @tparam E
    *   the error type to retry on
    * @param schedule
    *   the retry policy
    * @param block
    *   the computation to retry
    * @return
    *   the result of the first successful attempt
    */
  def apply[E]: RetryPartiallyApplied[E] = new RetryPartiallyApplied[E]

  /** Partially applied class to allow `Retry[E](schedule)(block)` syntax with proper type
    * inference.
    *
    * @tparam E
    *   the error type to retry on
    */
  class RetryPartiallyApplied[E] {

    def apply[A](schedule: Schedule)(
        block: Raise[E] ?=> A
    )(using Async, Raise[E]): A = {

      @tailrec
      def loop(attempt: Int): A = {
        val result = Raise.fold(block)(
          onError = { error =>
            val nextAttempt = attempt + 1
            schedule.delay(nextAttempt) match {
              case Some(d) =>
                Async.delay(d)
                None
              case None =>
                Some(Left(error))
            }
          }
        )(onSuccess = value => Some(Right(value)))

        result match {
          case Some(Right(value)) => value
          case Some(Left(error))  => Raise.raise(error)
          case None               => loop(attempt + 1)
        }
      }

      loop(0)
    }
  }
}
