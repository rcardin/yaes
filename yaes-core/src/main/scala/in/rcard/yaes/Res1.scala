package in.rcard.yaes

import scala.caps.SharedCapability

import language.experimental.captureChecking
import java.io.FileOutputStream

type Res1[R] = Res1.Unsafe[R]

object Res1 {

  def install[R](using res: Res1[R])(acquire: => R): R^ =
    res.install(acquire)

  def run[R, A](block: Res1[R] ?=> A): A = {
    val resourceHandler          = unsafe[R]
    var originalError: Throwable = null

    block(using resourceHandler)

  }

  private def unsafe[R] = new Res1.Unsafe[R] {

    override def install(acquire: => R): R^ = {

      val acquired = acquire
      acquired
    }
  }

  trait Unsafe[R] extends SharedCapability {

    def install(acquire: => R): R^
  }
}

@main def res1(): Unit = {
  println("1")
  var fos: FileOutputStream = null
  val result = Res1.run {
    val fis = Res1.install({
        val innerFos = new FileOutputStream("test.txt")
        fos = innerFos
        innerFos
    })
    // fos = fis
    fis.write(42)
    fis.flush()

    println("2")
    // () => (fis)
  }
  println("5")
}
