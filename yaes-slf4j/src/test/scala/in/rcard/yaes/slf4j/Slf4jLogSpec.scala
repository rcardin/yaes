package in.rcard.yaes.slf4j

import in.rcard.yaes.{Log, Logger}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayOutputStream, PrintStream}

class Slf4jLogSpec extends AnyFlatSpec with Matchers {

  /** Captures stderr output produced by slf4j-simple during the given block. */
  private def captureStdErr(block: => Unit): String = {
    val buffer    = new ByteArrayOutputStream()
    val capStream = new PrintStream(buffer, true, "UTF-8")
    val original  = System.err
    System.setErr(capStream)
    try {
      block
    } finally {
      System.err.flush()
      System.setErr(original)
    }
    buffer.toString("UTF-8")
  }

  "Slf4jLog" should "delegate all log levels to SLF4J" in {
    val output = captureStdErr {
      Slf4jLog.run {
        val logger = Log.getLogger("TestLogger")
        logger.trace("Trace message")
        logger.debug("Debug message")
        logger.info("Info message")
        logger.warn("Warn message")
        logger.error("Error message")
        logger.fatal("Fatal message")
      }
    }

    val lines = output.split('\n').map(_.trim)

    lines should contain("TRACE TestLogger - Trace message")
    lines should contain("DEBUG TestLogger - Debug message")
    lines should contain("INFO TestLogger - Info message")
    lines should contain("WARN TestLogger - Warn message")
    lines should contain("ERROR TestLogger - Error message")
    // fatal maps to ERROR
    lines.count(_.contains("ERROR TestLogger")) shouldBe 2
  }

  it should "respect SLF4J backend level configuration" in {
    // Slf4jFilteredLogger is configured to WARN in simplelogger.properties
    val output = captureStdErr {
      Slf4jLog.run {
        val logger = Log.getLogger("Slf4jFilteredLogger")
        logger.trace("should not appear")
        logger.debug("should not appear")
        logger.info("should not appear")
        logger.warn("Warn visible")
        logger.error("Error visible")
        logger.fatal("Fatal visible")
      }
    }

    output should not include "should not appear"
    output should include("Warn visible")
    output should include("Error visible")
    output should include("Fatal visible")
  }

  it should "map FATAL to ERROR level in SLF4J" in {
    val output = captureStdErr {
      Slf4jLog.run {
        val logger = Log.getLogger("FatalLogger")
        logger.fatal("Fatal mapped to error")
      }
    }

    output should include("ERROR FatalLogger - Fatal mapped to error")
  }

  it should "propagate logger name to SLF4J" in {
    val output = captureStdErr {
      Slf4jLog.run {
        val logger = Log.getLogger("my.custom.LoggerName")
        logger.info("Name check")
      }
    }

    output should include("my.custom.LoggerName - Name check")
  }

  it should "support multiple loggers with different names" in {
    val output = captureStdErr {
      Slf4jLog.run {
        val logger1 = Log.getLogger("Logger1")
        val logger2 = Log.getLogger("Logger2")
        logger1.info("From first")
        logger2.info("From second")
      }
    }

    output should include("Logger1 - From first")
    output should include("Logger2 - From second")
  }

  it should "return the block result" in {
    val result = Slf4jLog.run {
      42
    }

    result shouldBe 42
  }

  it should "not evaluate by-name message when level is disabled by SLF4J" in {
    // LazyLogger is configured to ERROR in simplelogger.properties
    var evaluated = false

    Slf4jLog.run {
      val logger = Log.getLogger("LazyLogger")
      logger.debug {
        evaluated = true
        "should not be evaluated"
      }
    }

    evaluated shouldBe false
  }
}
