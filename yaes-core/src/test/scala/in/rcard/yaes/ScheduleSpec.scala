package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

class ScheduleSpec extends AnyFlatSpec with Matchers {

  "Schedule.fixed" should "return the same delay for every attempt" in {
    val schedule = Schedule.fixed(500.millis)
    schedule.delay(1) shouldBe Some(500.millis)
    schedule.delay(2) shouldBe Some(500.millis)
    schedule.delay(100) shouldBe Some(500.millis)
  }

  it should "return None for attempt <= 0" in {
    val schedule = Schedule.fixed(500.millis)
    schedule.delay(0) shouldBe None
    schedule.delay(-1) shouldBe None
  }

  "Schedule.attempts" should "limit total executions to n (1 initial + n-1 retries)" in {
    val schedule = Schedule.fixed(100.millis).attempts(3)
    // attempts(3) = 3 total executions = 1 initial + 2 retries
    // So retry attempts 1 and 2 should return delays, attempt 3 should return None
    schedule.delay(1) shouldBe Some(100.millis)
    schedule.delay(2) shouldBe Some(100.millis)
    schedule.delay(3) shouldBe None
  }

  it should "return None immediately when attempts is 1 (no retries)" in {
    val schedule = Schedule.fixed(100.millis).attempts(1)
    schedule.delay(1) shouldBe None
  }

  it should "return None immediately when attempts is 0" in {
    val schedule = Schedule.fixed(100.millis).attempts(0)
    schedule.delay(1) shouldBe None
  }

  "Schedule.exponential" should "return initial * factor^(attempt-1)" in {
    val schedule = Schedule.exponential(100.millis, factor = 2.0)
    schedule.delay(1) shouldBe Some(100.millis)  // 100 * 2^0
    schedule.delay(2) shouldBe Some(200.millis)  // 100 * 2^1
    schedule.delay(3) shouldBe Some(400.millis)  // 100 * 2^2
    schedule.delay(4) shouldBe Some(800.millis)  // 100 * 2^3
  }

  it should "use default factor of 2.0" in {
    val schedule = Schedule.exponential(100.millis)
    schedule.delay(1) shouldBe Some(100.millis)
    schedule.delay(2) shouldBe Some(200.millis)
    schedule.delay(3) shouldBe Some(400.millis)
  }

  it should "cap delay at max" in {
    val schedule = Schedule.exponential(100.millis, factor = 2.0, max = 300.millis)
    schedule.delay(1) shouldBe Some(100.millis)  // 100 * 2^0 = 100
    schedule.delay(2) shouldBe Some(200.millis)  // 100 * 2^1 = 200
    schedule.delay(3) shouldBe Some(300.millis)  // 100 * 2^2 = 400, capped to 300
    schedule.delay(4) shouldBe Some(300.millis)  // still capped
  }

  it should "return None for attempt <= 0" in {
    val schedule = Schedule.exponential(100.millis)
    schedule.delay(0) shouldBe None
    schedule.delay(-1) shouldBe None
  }

  it should "compose with attempts" in {
    val schedule = Schedule.exponential(100.millis).attempts(3)
    schedule.delay(1) shouldBe Some(100.millis)
    schedule.delay(2) shouldBe Some(200.millis)
    schedule.delay(3) shouldBe None
  }

  "Schedule.jitter" should "produce delay within [delay*(1-factor), delay*(1+factor)]" in {
    val schedule = Schedule.fixed(1000.millis).jitter(0.5)
    // Run many iterations to check bounds
    val delays = (1 to 1000).flatMap(_ => schedule.delay(1))
    all(delays.map(_.toMillis)) should (be >= 500L and be <= 1500L)
  }

  it should "produce varying delays (not all identical)" in {
    val schedule = Schedule.fixed(1000.millis).jitter(0.5)
    val delays = (1 to 100).flatMap(_ => schedule.delay(1).map(_.toMillis))
    delays.toSet.size should be > 1
  }

  it should "compose with exponential" in {
    val schedule = Schedule.exponential(1000.millis, factor = 2.0).jitter(0.5)
    // Attempt 1: base = 1000ms, jitter range [500, 1500]
    val delays1 = (1 to 1000).flatMap(_ => schedule.delay(1))
    all(delays1.map(_.toMillis)) should (be >= 500L and be <= 1500L)
    // Attempt 2: base = 2000ms, jitter range [1000, 3000]
    val delays2 = (1 to 1000).flatMap(_ => schedule.delay(2))
    all(delays2.map(_.toMillis)) should (be >= 1000L and be <= 3000L)
  }

  it should "compose with attempts" in {
    val schedule = Schedule.fixed(100.millis).jitter(0.5).attempts(3)
    schedule.delay(1) shouldBe defined
    schedule.delay(2) shouldBe defined
    schedule.delay(3) shouldBe None
  }

  it should "handle zero jitter factor (no variation)" in {
    val schedule = Schedule.fixed(1000.millis).jitter(0.0)
    val delays = (1 to 100).flatMap(_ => schedule.delay(1))
    all(delays) shouldBe 1000.millis
  }

  it should "handle zero-duration base delay without throwing" in {
    val schedule = Schedule.fixed(Duration.Zero).jitter(0.5)
    val delays = (1 to 100).flatMap(_ => schedule.delay(1))
    all(delays) shouldBe Duration.Zero
  }

  "Schedule.exponential" should "cap at max when overflow produces infinite duration" in {
    val schedule = Schedule.exponential(100.millis, factor = 2.0, max = 5.seconds)
    // Very large attempt number would overflow without the cap
    val d = schedule.delay(1000)
    d shouldBe Some(5.seconds)
  }

  it should "cap at default max when no explicit max is provided" in {
    val schedule = Schedule.exponential(100.millis, factor = 2.0)
    // Large attempt that would overflow to Infinity
    val d = schedule.delay(1000)
    d shouldBe defined
    d.get.isFinite shouldBe true
  }
}
