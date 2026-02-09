package in.rcard.yaes

import java.time.Clock as JClock
import java.time.LocalDateTime

type Log = Yaes[Log.Unsafe]

/** Represents a logging effect.
  *
  * Provides methods for logging messages at different severity levels (trace, debug, info, warn,
  * error, fatal). Implementations determine where and how messages are logged.
  *
  * @see
  *   [[Log.Level]]
  */
trait Logger {

  /** The name of this logger */
  val name: String
  def trace(msg: => String)(using Log): Unit
  def debug(msg: => String)(using Log): Unit
  def info(msg: => String)(using Log): Unit
  def warn(msg: => String)(using Log): Unit
  def error(msg: => String)(using Log): Unit
  def fatal(msg: => String)(using Log): Unit
}

object Log {

  /** Default clock used for timestamping log messages. Uses the system's default time zone. */
  given clock: JClock = java.time.Clock.systemDefaultZone()

  /** Represents the severity level of a log message.
    *
    * Levels are ordered: TRACE < DEBUG < INFO < WARN < ERROR < FATAL.
    *
    * @param level
    *   Internal integer representation of the level's priority.
    */
  sealed abstract class Level(private val level: Int) {
    def enabled(other: Level): Boolean = level <= other.level
  }
  object Level {
    case object Trace extends Level(0)
    case object Debug extends Level(10)
    case object Info  extends Level(20)
    case object Warn  extends Level(30)
    case object Error extends Level(40)
    case object Fatal extends Level(50)
  }

  /** Internal console-based implementation of the [[Logger]] trait. */
  class ConsoleLogger private[Log] (
      override val name: String,
      private val level: Level,
      private val clock: java.time.Clock
  ) extends Logger {

    override def trace(msg: => String)(using Log): Unit =
      if (level.enabled(Level.Trace)) {
        log("TRACE", msg)
      }

    override def debug(msg: => String)(using Log): Unit =
      if (level.enabled(Level.Debug)) {
        log("DEBUG", msg)
      }

    override def info(msg: => String)(using Log): Unit =
      if (level.enabled(Level.Info)) {
        log("INFO", msg)
      }

    override def warn(msg: => String)(using Log): Unit =
      if (level.enabled(Level.Warn)) {
        log("WARN", msg)
      }

    override def error(msg: => String)(using Log): Unit =
      if (level.enabled(Level.Error)) {
        log("ERROR", msg)
      }

    override def fatal(msg: => String)(using Log): Unit =
      if (level.enabled(Level.Fatal)) {
        log("FATAL", msg)
      }

    private def log(level: String, message: String): Unit = {
      val now = LocalDateTime.now(clock)
      println(s"$now - $level - $name - $message")
    }
  }

  /** Retrieves a logger instance with the specified name.
    *
    * @param name
    *   The name for the logger.
    * @param log
    *   The Log effect provided through context parameters.
    * @return
    *   A [[Logger]] instance.
    */
  def getLogger(name: String)(using log: Log): Logger = log.unsafe.getLogger(name)

  /** Runs a computation that requires the [[Log]] effect, using a provided level and clock.
    *
    * This handler provides the [[Log]] effect to the `block` of code. The default implementation
    * uses a [[ConsoleLogger]]. The `level` parameter controls the minimum severity of messages that
    * will be emitted &mdash; messages below this level are silently discarded.
    *
    * Example:
    * {{{
    * Log.run(Log.Level.Info) {
    *   val logger = Log.getLogger("MyLogger")
    *   logger.debug("This will NOT be printed")
    *   logger.info("Starting computation...")
    *   logger.info("Computation finished.")
    * }
    * }}}
    *
    * @param level
    *   The minimum logging level. Defaults to [[Level.Debug]].
    * @param block
    *   The computation requiring the [[Log]] effect.
    * @param clock
    *   The clock to use for timestamping log messages (a default is provided implicitly).
    * @tparam A
    *   The result type of the computation.
    * @return
    *   The result of the computation `block`.
    */
  def run[A](level: Level = Level.Debug)(block: Log ?=> A)(using clock: JClock): A =
    val handler = new Yaes.Handler[Log.Unsafe, A, A] {
      override def handle(program: Log ?=> A): A = program(using
        Yaes(Log.unsafe(clock, level))
      )
    }
    Yaes.handle(block)(using handler)

  private def unsafe(clock: java.time.Clock, level: Level) = new Unsafe {
    override def getLogger(name: String): Logger =
      new ConsoleLogger(name, level, clock)
  }

  trait Unsafe {
    def getLogger(name: String): Logger
  }
}
