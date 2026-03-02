package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

class RetrySpec extends AnyFlatSpec with Matchers {

  "Retry" should "succeed immediately if the block succeeds on first try" in {
    val result = Async.run {
      Raise.either[String, Int] {
        Retry[String](Schedule.fixed(100.millis).attempts(3)) {
          42
        }
      }
    }
    result shouldBe Right(42)
  }

  it should "succeed on Nth retry" in {
    var attempts = 0
    val result = Async.run {
      Raise.either[String, Int] {
        Retry[String](Schedule.fixed(10.millis).attempts(5)) {
          attempts += 1
          if attempts < 3 then Raise.raise("not yet")
          attempts
        }
      }
    }
    result shouldBe Right(3)
    attempts shouldBe 3
  }

  it should "re-raise the last error when all attempts are exhausted" in {
    var attempts = 0
    val result = Async.run {
      Raise.either[String, Int] {
        Retry[String](Schedule.fixed(10.millis).attempts(3)) {
          attempts += 1
          Raise.raise(s"error-$attempts")
        }
      }
    }
    result shouldBe Left("error-3")
    attempts shouldBe 3
  }

  it should "delay between retries using the schedule" in {
    var attempts = 0
    val startTime = java.lang.System.nanoTime()
    val result = Async.run {
      Raise.either[String, Int] {
        Retry[String](Schedule.fixed(100.millis).attempts(3)) {
          attempts += 1
          Raise.raise(s"error-$attempts")
        }
      }
    }
    val elapsed = (java.lang.System.nanoTime() - startTime) / 1_000_000L
    // 2 retries with 100ms delays = at least ~200ms
    elapsed should be >= 150L
    result shouldBe Left("error-3")
  }

  it should "only retry errors of the specified type" in {
    sealed trait AppError
    case class HttpError(msg: String)       extends AppError
    case class ValidationError(msg: String) extends AppError

    var httpAttempts = 0
    val result = Async.run {
      Raise.either[AppError, Int] {
        Retry[AppError](Schedule.fixed(10.millis).attempts(5)) {
          httpAttempts += 1
          if httpAttempts < 3 then Raise.raise(HttpError("timeout"))
          httpAttempts
        }
      }
    }
    result shouldBe Right(3)
    httpAttempts shouldBe 3
  }

  it should "propagate errors of a different Raise type without retrying" in {
    var retryAttempts = 0
    val result = Async.run {
      Raise.either[String, Either[Int, Int]] {
        Raise.either[Int, Int] {
          Retry[Int](Schedule.fixed(10.millis).attempts(5)) {
            retryAttempts += 1
            // This raises String, not Int — should propagate immediately
            Raise.raise("not retried")
            42
          }
        }
      }
    }
    result shouldBe Left("not retried")
    retryAttempts shouldBe 1
  }

  it should "work with exponential backoff schedule" in {
    var attempts = 0
    val result = Async.run {
      Raise.either[String, Int] {
        Retry[String](Schedule.exponential(10.millis, factor = 2.0).attempts(4)) {
          attempts += 1
          if attempts < 4 then Raise.raise(s"error-$attempts")
          attempts
        }
      }
    }
    result shouldBe Right(4)
    attempts shouldBe 4
  }

  it should "work with a schedule that retries forever until success" in {
    var attempts = 0
    val result = Async.run {
      Raise.either[String, Int] {
        Retry[String](Schedule.fixed(1.millis)) {
          attempts += 1
          if attempts < 10 then Raise.raise("not yet")
          attempts
        }
      }
    }
    result shouldBe Right(10)
  }

  it should "execute the block once and re-raise when attempts is 1 (no retries)" in {
    var attempts = 0
    val result = Async.run {
      Raise.either[String, Int] {
        Retry[String](Schedule.fixed(10.millis).attempts(1)) {
          attempts += 1
          Raise.raise(s"error-$attempts")
        }
      }
    }
    result shouldBe Left("error-1")
    attempts shouldBe 1
  }

  it should "work with zero-duration delays" in {
    var attempts = 0
    val result = Async.run {
      Raise.either[String, Int] {
        Retry[String](Schedule.fixed(Duration.Zero).attempts(3)) {
          attempts += 1
          if attempts < 3 then Raise.raise(s"error-$attempts")
          attempts
        }
      }
    }
    result shouldBe Right(3)
    attempts shouldBe 3
  }
}
