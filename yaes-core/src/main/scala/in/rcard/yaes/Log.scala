package in.rcard.yaes

import in.rcard.yaes.Log.Log

import java.time.Clock as JClock
import java.time.LocalDateTime

trait Logger {
  val name: String
  val level: Log.Level = Log.Level.Debug
  def trace(msg: => String)(using Log): Unit
  def debug(msg: => String)(using Log): Unit
  def info(msg: => String)(using Log): Unit
  def warn(msg: => String)(using Log): Unit
  def error(msg: => String)(using Log): Unit
  def fatal(msg: => String)(using Log): Unit
}

object Log {

  given clock: JClock = java.time.Clock.systemDefaultZone()

  type Log = Yaes[Log.Unsafe]

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

  class ConsoleLogger private[Log] (
      override val name: String,
      override val level: Level,
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

  def getLogger(name: String)(using log: Log): Logger = log.unsafe.getLogger(name, Level.Debug)

  def getLogger(name: String, level: Level)(using log: Log): Logger =
    log.unsafe.getLogger(name, level)

  def run[A](block: Log ?=> A)(using clock: JClock): A =
    val handler = new Yaes.Handler[Log.Unsafe, A, A] {
      override def handle(program: Log ?=> A): A = program(using
        Yaes(Log.unsafe(clock))
      )
    }
    Yaes.handle(block)(using handler)

//   def run[A](clock: java.time.Clock)(block: Log ?=> A): A = {
//     val handler = new Yaes.Handler[Log.Unsafe, A, A] {
//       override def handle(program: Log ?=> A): A = program(using Yaes(Log.unsafe(clock)))
//     }
//     Yaes.handle(block)(using handler)
//   }

  private def unsafe(clock: java.time.Clock) = new Unsafe {
    override def getLogger(name: String, level: Level): Logger =
      new ConsoleLogger(name, level, clock)
  }

  trait Unsafe {
    def getLogger(name: String, level: Level): Logger
  }
}
