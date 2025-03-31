package in.rcard.yaes

object AlternateDefinition {

  // Eff[Random & Output] ?=> A
  trait Eff[+A] {

  }

  trait Random
  object Random {
    def nextBoolean(using Eff[Random]): Boolean = ???

    def run[O, A](program: Eff[O] & Eff[Random] ?=> A)(using Eff[O]): A = ???
  }

  trait Output
  object Output {
    def printLn(text: String)(using Eff[Output]): Unit = ???

    def run[O](program: Eff[O] & Eff[Output] ?=> Unit): Unit = ???
  }

  val program: Eff[Random & Output] ?=> Unit = {
    if (Random.nextBoolean) {
      Output.printLn("Hello")
    } else {
      Output.printLn("World")
    }
  }

  val partial: Eff[Output] ?=> Unit = Random.run(program) // Eff[Output] ?=> Unit
  val total: Unit = Output.run(partial) // Unit
}
