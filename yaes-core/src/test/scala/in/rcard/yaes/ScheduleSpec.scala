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
}
