package in.rcard.yaes

import java.io.IOException

trait Read {
  def readLn()(using t: Raise[IOException]): String
}

type Input = Effect[Read]

object Input {

  def apply[A](block: => A)(using in: Input): A = block

  def readLn()(using input: Input)(using t: Raise[IOException]): String = input.sf.readLn()

  def run[A](block: Input ?=> A): A = block(using Input.unsafe)

  val unsafe = new Effect(new Read {
    override def readLn()(using t: Raise[IOException]): String = Raise {
      try {
        scala.io.StdIn.readLine()
      } catch {
        case e: IOException =>
          Raise.raise(e)
      }
    }
  })
}
