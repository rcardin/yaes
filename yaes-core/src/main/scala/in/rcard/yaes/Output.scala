package in.rcard.yaes

import in.rcard.yaes.Effect.Handler
import in.rcard.yaes.Effect.handle

trait Print {
  def print(text: String): Unit
  def printLn(text: String): Unit
  def printErr(text: String): Unit
  def printErrLn(text: String): Unit
}

class PrintToConsole extends Print {

  override def printErr(text: String): Unit = scala.Console.err.print(text)

  override def print(text: String): Unit = scala.Console.print(text)

  override def printErrLn(text: String): Unit = scala.Console.err.println(text)

  override def printLn(text: String): Unit = scala.Console.println(text)
}

type Output = Effect[Print]

object Output {

  def apply[A](block: => A)(using out: Output): A = block

  def printLn(text: String)(using console: Output): Unit = console.sf.printLn(text)

  def print(text: String)(using console: Output): Unit = console.sf.print(text)

  def printErrLn(text: String)(using console: Output): Unit = console.sf.printErrLn(text)

  def printErr(text: String)(using console: Output): Unit = console.sf.printErr(text)

  def run[A](block: Output ?=> A): A = handle(block).`with`(default)

  val default: Handler[Print] = new Handler[Print] {
    val unsafe: Print = new Print {
      override def printErr(text: String): Unit = scala.Console.err.print(text)

      override def print(text: String): Unit = scala.Console.print(text)

      override def printErrLn(text: String): Unit = scala.Console.err.println(text)

      override def printLn(text: String): Unit = scala.Console.println(text)
    }
  }
}
