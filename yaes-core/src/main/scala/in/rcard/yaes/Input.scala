package in.rcard.yaes

import java.io.IOException

trait Input extends Effect {
  def readLn()(using t: Raise[IOException]): String
}

object Input {

  def apply[A](block: => A)(using in: Input): A = block

  def readLn()(using input: Input)(using t: Raise[IOException]): String = input.readLn()

  def run[A](block: Input ?=> A): A = block(using Input.unsafe)

  val unsafe = new Input {
    override def readLn()(using t: Raise[IOException]): String = Raise {
      try {
        scala.io.StdIn.readLine()
      } catch {
        case e: IOException =>
          Raise.raise(e)
      }
    }
  }
}
