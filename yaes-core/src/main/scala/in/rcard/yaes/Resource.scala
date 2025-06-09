package in.rcard.yaes

import scala.collection.mutable
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Queue
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque

object Resource {

  type Resource = Yaes[Resource.Unsafe]

  def apply[A](block: => A)(using res: Resource): A = block

  inline def install[A](inline acquire: => A)(inline release: A => Unit)(using res: Resource): A = {
    res.unsafe.install(acquire)(release)
  }

  inline def acquire[A <: Closeable](inline acquire: => A)(using res: Resource): A = {
    res.unsafe.install(acquire)(c => c.close())
  }

  inline def ensuring(inline finalizer: => Unit)(using res: Resource): Unit = {
    res.unsafe.install(())(_ => finalizer)
  }

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

  private[yaes] case class Finalizer[A](val resource: A, release: A => Unit)

  trait Unsafe {
    def install[A](acquire: => A)(release: A => Unit): A
    private[yaes] val finalizers: Deque[Finalizer[?]]
  }
}
