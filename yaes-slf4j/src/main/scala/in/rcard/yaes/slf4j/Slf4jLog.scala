package in.rcard.yaes.slf4j

import in.rcard.yaes.{Log, Logger, Yaes}
import org.slf4j.LoggerFactory

/** Handler that provides the [[Log]] effect backed by SLF4J.
  *
  * Level filtering is controlled entirely by the SLF4J backend configuration (e.g., logback.xml,
  * simplelogger.properties). Unlike [[Log.run]], there is no level parameter &mdash; configure
  * levels in your SLF4J backend instead.
  *
  * Usage is identical to [[Log.run]] except timestamps, formatting, and output destinations are
  * controlled by the SLF4J backend (Logback, Log4j2, slf4j-simple, etc.):
  *
  * {{{
  * Slf4jLog.run {
  *   val logger = Log.getLogger("MyService")
  *   logger.info("Hello from SLF4J!")
  * }
  * }}}
  *
  * User code (`Log.getLogger`, `logger.info(...)`, etc.) stays completely unchanged &mdash; only
  * the handler call site changes from `Log.run` to `Slf4jLog.run`.
  */
object Slf4jLog {

  /** Runs a computation that requires the [[Log]] effect, using SLF4J as the logging backend.
    *
    * @param block
    *   The computation requiring the [[Log]] effect.
    * @tparam A
    *   The result type of the computation.
    * @return
    *   The result of the computation `block`.
    */
  def run[A](block: Log ?=> A): A =
    val handler = new Yaes.Handler[Log.Unsafe, A, A] {
      override def handle(program: Log ?=> A): A = program(using
        Yaes(unsafe)
      )
    }
    Yaes.handle(block)(using handler)

  private val unsafe: Log.Unsafe = new Log.Unsafe {
    override def getLogger(name: String): Logger =
      new Slf4jLogger(name, LoggerFactory.getLogger(name))
  }
}
