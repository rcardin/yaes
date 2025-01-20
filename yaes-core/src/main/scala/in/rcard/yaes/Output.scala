package in.rcard.yaes

trait Print {
  def printLn(text: String): Unit
}

class PrintToConsole extends Print {
  override def printLn(text: String): Unit = scala.Console.println(text)
}

type Output = Effect[Print]

object Output {

  def apply[A](block: => A)(using out: Output): A = block

  def printLn(text: String)(using console: Output): Unit = console.sf.printLn(text)

  def run[A](block: Output ?=> A): A = {
    block(using Effect(new PrintToConsole))
  }
}
