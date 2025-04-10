package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Instant

class ClockSpec extends AnyFlatSpec with Matchers {

  "The Clock effect" should "return an instant" in {
    val startPoint = Instant.now()
    val actualResult = Clock.run {
      Clock.now
    }
    val endPoint = Instant.now()
    actualResult should be >= startPoint
    actualResult should be <= endPoint
  }

  it should "return a monotonic duration" in {
    val startPoint = System.nanoTime()
    val actualResult = Clock.run {
      Clock.nowMonotonic
    }
    val endPoint = System.nanoTime()
    actualResult.toNanos should be >= startPoint
    actualResult.toNanos should be <= endPoint
  }
}
