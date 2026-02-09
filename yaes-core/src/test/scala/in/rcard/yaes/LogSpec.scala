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

  "The Log effect" should "log at TRACE level" in {
    val actualResult = new ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Log.run(Log.Level.Trace) {
        val logger = Log.getLogger("TestLogger")

        logger.trace("Trace message")
        logger.debug("Debug message")
        logger.info("Info message")
        logger.warn("Warn message")
        logger.error("Error message")
        logger.fatal("Fatal message")
      }
    }

    val actualLoggedLines = actualResult.toString.split('\n')

    actualLoggedLines should contain allOf (
      "2025-04-22T19:55:59 - TRACE - TestLogger - Trace message",
      "2025-04-22T19:55:59 - DEBUG - TestLogger - Debug message",
      "2025-04-22T19:55:59 - INFO - TestLogger - Info message",
      "2025-04-22T19:55:59 - WARN - TestLogger - Warn message",
      "2025-04-22T19:55:59 - ERROR - TestLogger - Error message",
      "2025-04-22T19:55:59 - FATAL - TestLogger - Fatal message"
    )
  }

  it should "log at DEBUG level" in {
    val actualResult = new ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Log.run(Log.Level.Debug) {
        val logger = Log.getLogger("TestLogger")

        logger.trace("Trace message")
        logger.debug("Debug message")
        logger.info("Info message")
        logger.warn("Warn message")
        logger.error("Error message")
        logger.fatal("Fatal message")
      }
    }

    val actualLoggedLines = actualResult.toString.split('\n')

    actualLoggedLines should contain allOf (
      "2025-04-22T19:55:59 - DEBUG - TestLogger - Debug message",
      "2025-04-22T19:55:59 - INFO - TestLogger - Info message",
      "2025-04-22T19:55:59 - WARN - TestLogger - Warn message",
      "2025-04-22T19:55:59 - ERROR - TestLogger - Error message",
      "2025-04-22T19:55:59 - FATAL - TestLogger - Fatal message"
    )
  }

  it should "log at INFO level" in {
    val actualResult = new ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Log.run(Log.Level.Info) {
        val logger = Log.getLogger("TestLogger")

        logger.trace("Trace message")
        logger.debug("Debug message")
        logger.info("Info message")
        logger.warn("Warn message")
        logger.error("Error message")
        logger.fatal("Fatal message")
      }
    }

    val actualLoggedLines = actualResult.toString.split('\n')

    actualLoggedLines should contain allOf (
      "2025-04-22T19:55:59 - INFO - TestLogger - Info message",
      "2025-04-22T19:55:59 - WARN - TestLogger - Warn message",
      "2025-04-22T19:55:59 - ERROR - TestLogger - Error message",
      "2025-04-22T19:55:59 - FATAL - TestLogger - Fatal message"
    )
  }

  it should "log at WARN level" in {
    val actualResult = new ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Log.run(Log.Level.Warn) {
        val logger = Log.getLogger("TestLogger")

        logger.trace("Trace message")
        logger.debug("Debug message")
        logger.info("Info message")
        logger.warn("Warn message")
        logger.error("Error message")
        logger.fatal("Fatal message")
      }
    }

    val actualLoggedLines = actualResult.toString.split('\n')

    actualLoggedLines should contain allOf (
      "2025-04-22T19:55:59 - WARN - TestLogger - Warn message",
      "2025-04-22T19:55:59 - ERROR - TestLogger - Error message",
      "2025-04-22T19:55:59 - FATAL - TestLogger - Fatal message"
    )
  }

  it should "log at ERROR level" in {
    val actualResult = new ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Log.run(Log.Level.Error) {
        val logger = Log.getLogger("TestLogger")

        logger.trace("Trace message")
        logger.debug("Debug message")
        logger.info("Info message")
        logger.warn("Warn message")
        logger.error("Error message")
        logger.fatal("Fatal message")
      }
    }

    val actualLoggedLines = actualResult.toString.split('\n')

    actualLoggedLines should contain allOf (
      "2025-04-22T19:55:59 - ERROR - TestLogger - Error message",
      "2025-04-22T19:55:59 - FATAL - TestLogger - Fatal message"
    )
  }

  it should "log at FATAL level" in {
    val actualResult = new ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Log.run(Log.Level.Fatal) {
        val logger = Log.getLogger("TestLogger")

        logger.trace("Trace message")
        logger.debug("Debug message")
        logger.info("Info message")
        logger.warn("Warn message")
        logger.error("Error message")
        logger.fatal("Fatal message")
      }
    }

    val actualLoggedLines = actualResult.toString.split('\n')

    actualLoggedLines should contain(
      "2025-04-22T19:55:59 - FATAL - TestLogger - Fatal message"
    )
  }

  it should "default to DEBUG level when no level is specified" in {
    val actualResult = new ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Log.run() {
        val logger = Log.getLogger("TestLogger")

        logger.trace("Trace message")
        logger.debug("Debug message")
        logger.info("Info message")
      }
    }

    val actualLoggedLines = actualResult.toString.split('\n')

    actualLoggedLines should contain allOf (
      "2025-04-22T19:55:59 - DEBUG - TestLogger - Debug message",
      "2025-04-22T19:55:59 - INFO - TestLogger - Info message"
    )
    actualLoggedLines.mkString("\n") should not include "TRACE"
  }

  it should "use different name for different instances of loggers" in {
    val actualResult = new ByteArrayOutputStream()
    Console.withOut(actualResult) {
      Log.run(Log.Level.Trace) {
        val logger1 = Log.getLogger("TestLogger1")
        val logger2 = Log.getLogger("TestLogger2")

        logger1.trace("Trace message")
        logger2.debug("Debug message")
      }
    }

    val actualLoggedLines = actualResult.toString.split('\n')

    actualLoggedLines should contain allOf (
      "2025-04-22T19:55:59 - TRACE - TestLogger1 - Trace message",
      "2025-04-22T19:55:59 - DEBUG - TestLogger2 - Debug message"
    )
  }
}
