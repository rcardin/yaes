package in.rcard.yaes

import scala.collection.mutable.ListBuffer
import java.util.concurrent.locks.ReentrantLock

type Shutdown = Yaes[Shutdown.Unsafe]

private enum ShutdownState:
  case RUNNING, SHUTTING_DOWN

/** Graceful shutdown coordination for YAES applications.
  *
  * Provides state management and callback hooks for coordinating graceful shutdown across
  * concurrent operations. Automatically registers JVM shutdown hooks to handle SIGTERM/SIGINT
  * signals.
  *
  * **Shutdown Flow:**
  *   1. JVM receives SIGTERM/SIGINT (or user calls [[initiateShutdown]]) 2. State transitions to
  *      SHUTTING_DOWN 3. All registered hooks are invoked 4. Application checks [[isShuttingDown]]
  *      to reject new work 5. Existing work completes (managed by [[Async]] structured
  *      concurrency)
  *
  * Example - Simple shutdown:
  * {{{
  * Shutdown.run {
  *   while (!Shutdown.isShuttingDown()) {
  *     // Process work
  *   }
  *   // Cleanup happens here
  * }
  * }}}
  *
  * Example - With hooks:
  * {{{
  * Shutdown.run {
  *   Resource.run {
  *     val db = Resource.acquire(openDatabase())
  *
  *     Shutdown.onShutdown(() => {
  *       println("Shutdown initiated, stopping work acceptance")
  *     })
  *
  *     while (!Shutdown.isShuttingDown()) {
  *       processJob(db)
  *     }
  *     // Resource cleanup happens automatically
  *   }
  * }
  * }}}
  *
  * Example - Daemon server (with Async):
  * {{{
  * Shutdown.run {
  *   val serverShutdown = Async.runDaemon(timeout = 30.seconds) {
  *     // Async.runDaemon uses onShutdown internally to coordinate scope shutdown
  *     startServer()
  *   }
  *   serverShutdown  // Blocks until shutdown
  * }
  * }}}
  */
object Shutdown {

  /** Initiates graceful shutdown.
    *
    * Transitions the state to SHUTTING_DOWN and invokes all registered hooks. This method is
    * idempotent - calling it multiple times will only transition state and invoke hooks once.
    *
    * **Automatically triggered by:**
    *   - JVM shutdown (SIGTERM, SIGINT, Ctrl+C)
    *   - Container stop (Docker, Kubernetes)
    *
    * **Manually triggered for:**
    *   - Tests (controlled shutdown)
    *   - Health-based shutdown
    *   - Time-based shutdown
    *
    * @param shutdown
    *   the Shutdown effect context
    */
  def initiateShutdown()(using shutdown: Shutdown): Unit =
    shutdown.unsafe.requestShutdown()

  /** Checks if shutdown has been initiated.
    *
    * Use this to determine whether to accept new work. Applications should check this flag before
    * starting new tasks and reject work when shutting down.
    *
    * Example:
    * {{{
    * if (Shutdown.isShuttingDown()) {
    *   respondWith(503, "Service Unavailable")
    * } else {
    *   acceptRequest()
    * }
    * }}}
    *
    * @param shutdown
    *   the Shutdown effect context
    * @return
    *   true if shutdown has been initiated, false otherwise
    */
  def isShuttingDown()(using shutdown: Shutdown): Boolean =
    shutdown.unsafe.checkShuttingDown()

  /** Registers a callback to be invoked when shutdown is initiated.
    *
    * Hooks are executed synchronously after the state transition to SHUTTING_DOWN, in the order
    * they were registered. Each hook is wrapped in exception handling - if a hook throws, other
    * hooks will still execute.
    *
    * **Use cases:**
    *   - Notify scopes to begin graceful termination (used by [[Async.runDaemon]])
    *   - Log shutdown initiation
    *   - Trigger application-specific shutdown logic
    *
    * **Note:** For resource cleanup (closing files, connections, etc.), prefer the [[Resource]]
    * effect which guarantees LIFO cleanup order.
    *
    * Example:
    * {{{
    * Shutdown.run {
    *   Shutdown.onShutdown(() => {
    *     logger.info("Shutdown initiated")
    *     metrics.recordShutdown()
    *   })
    *
    *   // Application logic
    * }
    * }}}
    *
    * @param hook
    *   the callback function to invoke on shutdown
    * @param shutdown
    *   the Shutdown effect context
    */
  def onShutdown(hook: () => Unit)(using shutdown: Shutdown): Unit =
    shutdown.unsafe.registerHook(hook)

  /** Runs a program with shutdown coordination.
    *
    * Sets up shutdown infrastructure including JVM shutdown hooks and state management. The
    * program executes with access to shutdown operations via the [[Shutdown]] effect context.
    *
    * @param program
    *   the program requiring shutdown coordination
    * @tparam A
    *   the result type
    * @return
    *   the result of the program
    */
  def run[A](program: Shutdown ?=> A): A = {
    val handler = new Yaes.Handler[Shutdown.Unsafe, A, A] {
      override def handle(prog: Shutdown ?=> A): A = {
        var state: ShutdownState = ShutdownState.RUNNING
        val hooks = ListBuffer[() => Unit]()
        val lock = new ReentrantLock()

        val shutdownImpl = new Unsafe {
          override def requestShutdown(): Unit = {
            val hooksToExecute = lock.synchronized {
              if (state == ShutdownState.RUNNING) {
                state = ShutdownState.SHUTTING_DOWN
                hooks.toList // Create immutable snapshot
              } else {
                List.empty[() => Unit]
              }
            }

            // Execute hooks outside the lock to prevent deadlock
            hooksToExecute.foreach { hook =>
              try {
                hook()
              } catch {
                case e: Exception =>
                  java.lang.System.err.println(s"Shutdown hook failed: ${e.getClass.getName}: ${e.getMessage}")
              }
            }
          }

          override def registerHook(hook: () => Unit): Unit = lock.synchronized {
            if (state == ShutdownState.RUNNING) {
              hooks += hook
            }
            // Silently ignore hooks registered after shutdown has started
          }

          override def checkShuttingDown(): Boolean = lock.synchronized {
            state == ShutdownState.SHUTTING_DOWN
          }
        }

        // Register JVM shutdown hook (triggers shutdown on SIGTERM/SIGINT)
        val jvmHook = new Thread(() => {
          shutdownImpl.requestShutdown()
        }, "yaes-shutdown-hook")

        Runtime.getRuntime.addShutdownHook(jvmHook)

        try {
          prog(using Yaes(shutdownImpl))
        } finally {
          try {
            Runtime.getRuntime.removeShutdownHook(jvmHook)
          } catch {
            case _: IllegalStateException => () // JVM already shutting down
          }
        }
      }
    }
    Yaes.handle(program)(using handler)
  }

  /** Unsafe implementation of the Shutdown effect.
    *
    * Provides low-level operations for shutdown state management and hook execution.
    */
  trait Unsafe {

    /** Requests shutdown, transitioning state and executing hooks.
      */
    def requestShutdown(): Unit

    /** Checks if the system is currently shutting down.
      *
      * @return
      *   true if shutdown has been initiated
      */
    def checkShuttingDown(): Boolean

    /** Registers a hook to be executed on shutdown.
      *
      * @param hook
      *   the callback function to register
      */
    def registerHook(hook: () => Unit): Unit
  }
}
