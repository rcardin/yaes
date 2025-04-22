package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.System
import java.time.Clock as JClock
import java.time.Instant
import java.time.ZoneId

// Tuesday 22 April 2025 19:55:59.000
private val FIXED_TIME = Instant.ofEpochMilli(1_745_351_759_000L)

class LogSpec extends AnyFlatSpec with Matchers {

  given fixedClock: JClock =
    JClock.fixed(FIXED_TIME, ZoneId.of("UTC"))

  "The Log effect" should "be able to log at trace level" in {
    val actualResult = new ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Log.run {
        val logger = Log.getLogger("TestLogger", Log.Level.Trace)

        logger.trace("Trace message")
        logger.debug("Debug message")
        logger.info("Info message")
        logger.warn("Warn message")
        logger.error("Error message")
        logger.fatal("Fatal message")
      }
    }

    val output = actualResult.toString
    output should include("TestLogger")
    output should include("TRACE")
    output should include("Trace message")
  }
}
