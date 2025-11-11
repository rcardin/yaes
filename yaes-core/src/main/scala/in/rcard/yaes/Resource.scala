package in.rcard.yaes

import scala.collection.mutable
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Queue
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque

type Resource = Yaes[Resource.Unsafe]

/** Companion object providing convenient methods for working with the `Resource` effect.
  *
  * The `Resource` effect provides automatic resource management with guaranteed cleanup. It ensures
  * that all acquired resources are properly released in LIFO order, even when exceptions occur.
  * This is particularly useful for managing files, database connections, network connections, and
  * other resources that need explicit cleanup.
  *
  * Key features:
  *   - Automatic resource cleanup in reverse order of acquisition (LIFO)
  *   - Exception safety - resources are cleaned up even if exceptions occur
  *   - Support for any type of resource with custom release functions
  *   - Built-in support for `Closeable` resources
  *   - Support for simple cleanup actions via finalizers
  *
  * Example Usage:
  * {{{
  * import java.io.{FileInputStream, FileOutputStream}
  * 
  * Resource.run {
  *   val input = Resource.acquire(new FileInputStream("input.txt"))
  *   val output = Resource.acquire(new FileOutputStream("output.txt"))
  *   
  *   // Custom resource management
  *   val connection = Resource.install(openDatabaseConnection()) { conn =>
  *     conn.close()
  *     println("Database connection closed")
  *   }
  *   
  *   // Simple cleanup action
  *   Resource.ensuring {
  *     println("Cleanup completed")
  *   }
  *   
  *   // Use resources safely - they will be automatically cleaned up
  *   processFiles(input, output, connection)
  * }
  * // All resources are automatically closed here, even if exceptions occurred
  * }}}
  */
object Resource {

  /** Lifts a block of code into the Resource effect.
    *
    * @param block
    *   The code block to be lifted into the Resource effect
    * @param res
    *   The Resource effect provided through context parameters
    * @return
    *   The block with the Resource effect
    */
  def apply[A](block: => A)(using res: Resource): A = block

  /** Installs a resource with custom acquisition and release logic.
    *
    * The resource is acquired immediately and a finalizer is registered to ensure
    * proper cleanup. The release function will be called automatically when the
    * resource scope ends, even if an exception occurs.
    *
    * Example:
    * {{{
    * Resource.run {
    *   val connection = Resource.install(openDatabaseConnection()) { conn =>
    *     conn.close()
    *     logger.info("Database connection closed")
    *   }
    *   
    *   val lock = Resource.install(acquireLock()) { l =>
    *     l.release()
    *     logger.info("Lock released")
    *   }
    *   
    *   // Use connection and lock safely
    *   performDatabaseOperations(connection)
    * }
    * // Resources are cleaned up in reverse order: lock, then connection
    * }}}
    *
    * @param acquire
    *   The acquisition function that returns the resource
    * @param release
    *   The release function that cleans up the resource
    * @param res
    *   The Resource effect provided through context parameters
    * @return
    *   The acquired resource
    * @tparam A
    *   The type of the resource
    */
  inline def install[A](inline acquire: => A)(inline release: A => Unit)(using res: Resource): A = {
    res.unsafe.install(acquire)(release)
  }

  /** Acquires a `Closeable` resource and ensures it is automatically closed.
    *
    * This is a convenience method for resources that implement the `Closeable` interface.
    * The resource will be automatically closed when the resource scope ends.
    *
    * Example:
    * {{{
    * import java.io.{FileInputStream, BufferedReader, InputStreamReader}
    * 
    * Resource.run {
    *   val inputStream = Resource.acquire(new FileInputStream("data.txt"))
    *   val reader = Resource.acquire(new BufferedReader(new InputStreamReader(inputStream)))
    *   
    *   // Read file contents safely
    *   val content = reader.lines().toArray.mkString("\n")
    *   processContent(content)
    * }
    * // Both reader and inputStream are automatically closed
    * }}}
    *
    * @param acquire
    *   The acquisition function that returns the Closeable resource
    * @param res
    *   The Resource effect provided through context parameters
    * @return
    *   The acquired Closeable resource
    * @tparam A
    *   The type of the Closeable resource
    */
  inline def acquire[A <: Closeable](inline acquire: => A)(using res: Resource): A = {
    res.unsafe.install(acquire)(c => c.close())
  }

  /** Registers a finalizer action to be executed when the resource scope ends.
    *
    * This method is useful for registering cleanup actions that don't involve a specific
    * resource but need to be executed when leaving the resource scope.
    *
    * Example:
    * {{{
    * Resource.run {
    *   // Set up some global state
    *   setupGlobalConfiguration()
    *   
    *   Resource.ensuring {
    *     cleanupGlobalConfiguration()
    *     logger.info("Global configuration cleaned up")
    *   }
    *   
    *   Resource.ensuring {
    *     logger.info("Operation completed")
    *   }
    *   
    *   // Perform main logic
    *   performComplexOperation()
    * }
    * // Finalizers are executed in reverse order:
    * // 1. "Operation completed" is logged
    * // 2. Global configuration is cleaned up
    * }}}
    *
    * @param finalizer
    *   The cleanup action to be executed
    * @param res
    *   The Resource effect provided through context parameters
    */
  inline def ensuring(inline finalizer: => Unit)(using res: Resource): Unit = {
    res.unsafe.install(())(_ => finalizer)
  }

  /** Runs a program that requires the `Resource` effect.
    *
    * This method handles the `Resource` effect by providing automatic resource management.
    * All resources acquired within the block are guaranteed to be cleaned up in reverse order
    * (LIFO - Last In, First Out) when the block completes, whether successfully or with an exception.
    *
    * Resource cleanup is exception-safe:
    * - If the main program throws an exception, cleanup still occurs
    * - If cleanup itself throws exceptions, they are handled appropriately
    * - Original exceptions from the main program are preserved
    *
    * Example:
    * {{{
    * import java.io.{FileWriter, PrintWriter}
    * 
    * val result = Resource.run {
    *   val writer = Resource.acquire(new FileWriter("output.txt"))
    *   val printer = Resource.acquire(new PrintWriter(writer))
    *   
    *   Resource.ensuring {
    *     println("File processing completed")
    *   }
    *   
    *   // Write data to file
    *   printer.println("Hello, World!")
    *   printer.flush()
    *   
    *   "success"
    * }
    * // Cleanup order: finalizer runs, then printer closes, then writer closes
    * println(result) // prints: success
    * }}}
    *
    * @param block
    *   The code block to be run with the `Resource` effect
    * @return
    *   The result of the code block
    * @tparam A
    *   The return type of the code block
    */
  def run[A](block: Resource ?=> A): A = {
    val handler = new Yaes.Handler[Resource.Unsafe, A, A] {
      override def handle(program: Resource ?=> A): A = {

        val resourceHandler          = unsafe
        var originalError: Throwable = null
        try {
          program(using Yaes(resourceHandler))
        } catch {
          case error: Throwable =>
            originalError = error
            throw error
        } finally {
          var originalReleaseError: Throwable = null
          while (!resourceHandler.finalizers.isEmpty()) {
            val finalizer = resourceHandler.finalizers.pop()
            try {
              finalizer.release(finalizer.resource)
            } catch {
              case releaseError: Throwable =>
                if (originalReleaseError == null) {
                  originalReleaseError = releaseError
                }
                if (originalError != null) {
                  // FIXME Should we use an effect here?
                  println(s"Error during resource release")
                  releaseError.printStackTrace()
                  originalReleaseError = originalError
                }
            }
          }

          if (originalReleaseError != null) {
            throw originalReleaseError
          }
        }
      }

    }
    Yaes.handle(block)(using handler)
  }

  private def unsafe: Resource.Unsafe = new Resource.Unsafe {

    override val finalizers: Deque[Finalizer[?]] = new ConcurrentLinkedDeque()

    override def install[A](acquire: => A)(release: A => Unit): A = {

      val acquired = acquire
      val finalizer = Finalizer(acquired, release)
      finalizers.push(finalizer)
      acquired
    }
  }

  /** Internal case class representing a finalizer for a resource.
    *
    * A finalizer pairs a resource with its cleanup function. This is used internally
    * to track resources and ensure they are properly cleaned up.
    *
    * @param resource
    *   The acquired resource
    * @param release
    *   The cleanup function for the resource
    * @tparam A
    *   The type of the resource
    */
  private[yaes] case class Finalizer[A](val resource: A, release: A => Unit)

  /** Unsafe interface for resource management operations.
    *
    * This trait provides the low-level operations for resource management without any
    * safety guarantees. It is intended for internal use only and should not be used
    * directly in application code.
    *
    * The `Unsafe` interface maintains a queue of finalizers that will be executed
    * when the resource scope ends. Resources are cleaned up in LIFO order.
    */
  trait Unsafe {
    
    /** Installs a resource with its cleanup function.
      *
      * @param acquire
      *   The acquisition function that returns the resource
      * @param release
      *   The cleanup function for the resource
      * @return
      *   The acquired resource
      * @tparam A
      *   The type of the resource
      */
    def install[A](acquire: => A)(release: A => Unit): A
    
    /** Internal queue of finalizers for tracking acquired resources.
      *
      * This deque maintains the order of resource acquisition and is used
      * to ensure proper cleanup in reverse order.
      */
    private[yaes] val finalizers: Deque[Finalizer[?]]
  }
}
