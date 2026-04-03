package in.rcard.yaes

import scala.collection.mutable
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Queue
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import scala.caps.SharedCapability
import language.experimental.captureChecking
import java.io.File
import java.io.FileInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.net.http.HttpClient

type Res[R] = Res.Unsafe[R]

object Res {

  inline def install[R](using res: Res[R])(inline acquire: => R)(inline release: R => Unit): R^ =
    res.install(acquire)(release)

  def run[R, A](block: Res[R] ?=> A): A = {
    val resourceHandler          = unsafe[R]
    var originalError: Throwable = null
    try {
      block(using resourceHandler)
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
            if (originalError != null) {
              originalError.addSuppressed(releaseError)
            } else if (originalReleaseError == null) {
              originalReleaseError = releaseError
            } else {
              originalReleaseError.addSuppressed(releaseError)
            }
        }
      }

      if (originalReleaseError != null) {
        throw originalReleaseError
      }
    }
  }

  private def unsafe[R] = new Res.Unsafe[R] {

    override val finalizers: Deque[Finalizer] = new ConcurrentLinkedDeque()

    override def install[R1 <: R](acquire: => R1)(release: R1 => Unit): R1^ = {

      val acquired  = acquire
      val finalizer = Finalizer(acquired, release.asInstanceOf[Any -> Unit])
      finalizers.push(finalizer)
      acquired
    }
  }

  private[yaes] class Finalizer(val resource: Any, val release: Any -> Unit)

  trait Unsafe[-R] extends SharedCapability {

    def install[R1 <: R](acquire: => R1)(release: R1 => Unit): R1^

    private[yaes] val finalizers: Deque[Finalizer]
  }
}

class Client

@main def res(): Unit = {
  println("1")
  var fos: FileOutputStream = null
  val result = Res.run {
    val fis = Res.install(new FileOutputStream("test.txt"))((r => {
        println("4")
        fos = r
        r.close()
    }))
    val os = Res.install(new Client())((r => {
        println("3")
    }))
    fis.write(42)
    fis.flush()

    println("2")
  }
  println("5")
}
