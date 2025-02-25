package in.rcard.yaes

trait Print {
  def print(text: String): Unit
  def printLn(text: String): Unit
  def printErr(text: String): Unit
  def printErrLn(text: String): Unit
}

type Output = Effect[Print]

object Output {

  def apply[A](block: => A)(using out: Output): A = block

  def printLn(text: String)(using console: Output): Unit = console.sf.printLn(text)

  def print(text: String)(using console: Output): Unit = console.sf.print(text)

  def printErrLn(text: String)(using console: Output): Unit = console.sf.printErrLn(text)

  def printErr(text: String)(using console: Output): Unit = console.sf.printErr(text)

  def run[A](block: Output ?=> A): A =
    block(using Output.unsafe)

  val unsafe = new Effect(new Print {
    override def printErr(text: String): Unit = scala.Console.err.print(text)

    override def print(text: String): Unit = scala.Console.print(text)

    override def printErrLn(text: String): Unit = scala.Console.err.println(text)

    override def printLn(text: String): Unit = scala.Console.println(text)
  })
}
