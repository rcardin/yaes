package in.rcard.yaes.slf4j

import in.rcard.yaes.{Log, Logger}
import org.slf4j.{Logger as JLogger}

/** A [[Logger]] implementation that delegates to an SLF4J [[org.slf4j.Logger]].
  *
  * Level filtering is controlled entirely by the SLF4J backend configuration (e.g., logback.xml,
  * simplelogger.properties). By-name `msg` is not evaluated unless the SLF4J guard
  * (`underlying.isXxxEnabled`) passes.
  *
  * SLF4J has no FATAL level, so [[fatal]] delegates to `underlying.error`.
  *
  * @param name
  *   The logger name (propagated to SLF4J).
  * @param underlying
  *   The SLF4J logger instance.
  */
private[slf4j] class Slf4jLogger(
    override val name: String,
    private val underlying: JLogger
) extends Logger {

  override def trace(msg: => String)(using Log): Unit =
    if (underlying.isTraceEnabled) underlying.trace(msg)

  override def debug(msg: => String)(using Log): Unit =
    if (underlying.isDebugEnabled) underlying.debug(msg)

  override def info(msg: => String)(using Log): Unit =
    if (underlying.isInfoEnabled) underlying.info(msg)

  override def warn(msg: => String)(using Log): Unit =
    if (underlying.isWarnEnabled) underlying.warn(msg)

  override def error(msg: => String)(using Log): Unit =
    if (underlying.isErrorEnabled) underlying.error(msg)

  override def fatal(msg: => String)(using Log): Unit =
    if (underlying.isErrorEnabled) underlying.error(msg)
}
