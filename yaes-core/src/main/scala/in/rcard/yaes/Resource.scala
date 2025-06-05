package in.rcard.yaes

import scala.collection.mutable
import java.io.Closeable

object Resource {

  type Resource = Yaes[Resource.Unsafe]

  def apply[A](block: => A)(using res: Resource): A = block

  def install[A](acquire: => A)(release: A => Unit)(using res: Resource): A = {
    res.unsafe.install(acquire)(release)
  }

  def acquire[A <: Closeable](acquire: => A)(using res: Resource): A = {
    res.unsafe.install(acquire)(c => c.close())
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
          resourceHandler.resourcesToRelease.foreach { case _Resource(resource, release) =>
            try {
              release(resource)
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

    override val resourcesToRelease: mutable.ListBuffer[Any] = mutable.ListBuffer.empty[Any]

    override def install[A](acquire: => A)(release: A => Unit): A = {

      val acquired = acquire
      val resource = _Resource(acquired, release)
      resourcesToRelease.prepend(resource)
      acquired
    }
  }

  private case class _Resource[A](val resource: A, release: A => Unit)

  trait Unsafe {
    def install[A](acquire: => A)(release: A => Unit): A
    private[yaes] val resourcesToRelease: mutable.ListBuffer[Any]
  }
}
