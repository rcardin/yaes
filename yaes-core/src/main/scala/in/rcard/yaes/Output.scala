package in.rcard.yaes

trait Output extends Effect {
  def print(text: String): Unit
  def printLn(text: String): Unit
  def printErr(text: String): Unit
  def printErrLn(text: String): Unit
}

object Output {

  def apply[A](block: => A)(using out: Output): A = block

  def printLn(text: String)(using console: Output): Unit = console.printLn(text)

  def print(text: String)(using console: Output): Unit = console.print(text)

  def printErrLn(text: String)(using console: Output): Unit = console.printErrLn(text)

  def printErr(text: String)(using console: Output): Unit = console.printErr(text)

  def run[A](block: Output ?=> A): A = {
    val handler = new Effect.Handler[Output, A, A] {
      override def handle(program: Output ?=> A): A = program(using Output.unsafe)
    }
    Effect.handle(block)(using handler)
  }

  private val unsafe = new Output {
    override def printErr(text: String): Unit = scala.Console.err.print(text)

    override def print(text: String): Unit = scala.Console.print(text)

    override def printErrLn(text: String): Unit = scala.Console.err.println(text)

    override def printLn(text: String): Unit = scala.Console.println(text)
  }
}
