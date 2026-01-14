package in.rcard.yaes

import java.lang.{System => JSystem}
import java.time.{Clock => JClock}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/** An abstract base class for YAES applications providing a common entry point.
  *
  * This trait provides a foundation for building applications using the YAES framework, with
  * built-in support for common effects like Output, Random, Clock, System, and Log.
  *
  * Example usage:
  * {{{
  * object MyApp extends YaesApp:
  *   def run: Unit = {
  *     Output.printLn(s"Starting application with args: ${args.mkString(", ")}")
  *     
  *     val currentTime = Clock.now
  *     Output.printLn(s"Current time: $currentTime")
  *     
  *     val randomNumber = Random.nextInt
  *     Output.printLn(s"Random number: $randomNumber")
  *     
  *     val logger = Log.getLogger("MyApp")
  *     logger.info("Application started successfully")
  *   }
  * }}}
  */
trait YaesApp {

  private var _args: Array[String] = null

  /** The command-line arguments this application was started with. */
  final protected def args: Array[String] =
    if _args eq null then Array.empty
    else _args

  /** The execution context used for async operations. Can be overridden. */
  protected given executionContext: ExecutionContext = ExecutionContext.global

  /** The clock used for logging. Can be overridden. */
  protected given logClock: JClock = JClock.systemDefaultZone()

  /** The timeout for blocking IO operations. Can be overridden. */
  protected def runTimeout: Duration = Duration.Inf

  /** The application logic that will be executed when the application starts.
    *
    * This method has access to common effects:
    *   - Output: for console output
    *   - Input: for console input
    *   - Random: for random number generation
    *   - Clock: for time operations
    *   - System: for system properties and environment variables
    *   - Log: for structured logging
    *
    * Note: Exceptions thrown during execution will be caught by the IO effect and handled
    * by the handleError method.
    *
    * Override this method to define your application logic.
    *
    * Example:
    * {{{
    * object MyApp extends YaesApp:
    *   def run: Unit = {
    *     val logger = Log.getLogger("MyApp")
    *     logger.info("Application started")
    *     Output.printLn("Hello, YAES!")
    *   }
    * }}}
    */
  def run: (
      Output,
      Input,
      Random,
      Clock,
      System,
      Log
  ) ?=> Unit

  /** Executes the run method with all the effect handlers in the correct order.
    *
    * The order of handlers is:
    *   1. Sync (outermost) - handles side effects, async operations, and exceptions
    *   2. Output - console output
    *   3. Input - console input
    *   4. Random - random number generation
    *   5. Clock - time operations
    *   6. System - system properties/env vars
    *   7. Log (innermost) - structured logging
    */
  private def executeRun(): Unit = {
    val result = Sync.runBlocking(runTimeout) {
      Output.run {
        Input.run {
          Random.run {
            Clock.run {
              System.run {
                Log.run {
                  run
                }
              }
            }
          }
        }
      }
    }

    handleError(result.failed.toOption)
  }

  /** Called when the application completes, either successfully or with an error.
    * 
    * Override this method to customize exit behavior (e.g., for testing or custom error handling).
    * 
    * The default implementation:
    *   - On None: exits with code 0 (success)
    *   - On Some(exception): prints error message and stack trace, then exits with code 1
    *
    * @param error
    *   None if the application completed successfully, Some(exception) if an error occurred
    */
  protected def handleError(error: Option[Throwable]): Unit = {
    error match {
      case None => sys.exit(0)
      case Some(ex) =>
        JSystem.err.println(s"Application error: ${ex.getMessage}")
        ex.printStackTrace()
        sys.exit(1)
    }
  }

  /** The main entry point of the application.
    * This method should not be overridden.
    *
    * @param args
    *   The command-line arguments
    */
  final def main(args: Array[String]): Unit = {
    this._args = args
    executeRun()
  }
}

/** Companion object providing utilities for YaesApp. */
object YaesApp {

  /** Creates a simple YaesApp from a block of code.
    *
    * Example:
    * {{{
    * val app = YaesApp {
    *   Output.printLn("Hello, YAES!")
    * }
    * app.main(Array.empty)
    * }}}
    *
    * @param block
    *   The code to execute with the available effects
    * @return
    *   A YaesApp instance
    */
  def apply(
      block: (
          Output,
          Input,
          Random,
          Clock,
          System,
          Log
      ) ?=> Unit
  ): YaesApp = new YaesApp {
    def run: (
        Output,
        Input,
        Random,
        Clock,
        System,
        Log
    ) ?=> Unit = block
  }
}
