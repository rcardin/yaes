package in.rcard.yaes

import in.rcard.yaes.Yaes.Effect

/** Companion object providing convenient methods for working with the Output effect.
  *
  * This object contains:
  *   - Helper methods for common output operations
  *   - A runner that provides a concrete implementation for executing Output effects
  *   - An unsafe implementation that directly interfaces with the system console
  *
  * Example usage:
  * {{{
  *   // Define a program that requires Output capability
  *   def greetUser(name: String)(using Output): Unit = {
  *     Output.print("Hello, ")
  *     Output.printLn(name)
  *   }
  *
  *   // Run the program using the Output effect handler
  *   Output.run {
  *     greetUser("Alice")
  *     Output.printLn("How are you?")
  *   }
  * }}}
  */
object Output {

  type Output = Yaes[Output.Unsafe]

  /** Lifts a block of code into the Output effect.
    *
    * @param block
    *   The code block to be lifted into the Output effect
    * @param out
    *   The Output capability provided through context parameters
    * @return
    *   The block with the Output capability
    */
  def apply[A](block: => A)(using out: Output): A = block

  /** Prints a line of `text` to the console.
    *
    * @param text
    *   The text to print
    * @param console
    *   The Output capability provided through context parameters
    */
  def printLn(text: String)(using console: Output): Unit = console.unsafe.printLn(text)

  /** Prints `text` to the console.
    *
    * @param text
    *   The text to print
    * @param console
    *   The Output capability provided through context parameters
    */
  def print(text: String)(using console: Output): Unit = console.unsafe.print(text)

  /** Prints an error line of `text` to the console.
    *
    * @param text
    *   The text to print
    * @param console
    *   The Output capability provided through context parameters
    */
  def printErrLn(text: String)(using console: Output): Unit = console.unsafe.printErrLn(text)

  /** Prints an error `text` to the console.
    *
    * @param text
    *   The text to print
    * @param console
    *   The Output capability provided through context parameters
    */
  def printErr(text: String)(using console: Output): Unit = console.unsafe.printErr(text)

  /** Runs a block of code that requires Output capability by providing a concrete implementation.
    *
    * This method handles the Output effect by supplying the implementation that directly interfaces
    * with the system console.
    *
    * @param block
    *   The code block requiring Output capability
    * @return
    *   The result of executing the block
    *
    * Example usage:
    * {{{
    *   // Define a program that requires Output capability
    *   def greetUser(name: String)(using Output): Unit = {
    *     Output.print("Hello, ")
    *     Output.printLn(name)
    *   }
    *
    *   // Run the program using the Output effect handler
    *   Output.run {
    *     greetUser("Alice")
    *     Output.printLn("How are you?")
    *   }
    * }}}
    */
  def run[A](block: Output ?=> A): A = {
    val handler = new Yaes.Handler[Output.Unsafe, A, A] {
      override def handle(program: Output ?=> A): A = program(using new Yaes(Output.unsafe))
    }
    Yaes.handle(block)(using handler)
  }

  private val unsafe = new Output.Unsafe {
    override def printErr(text: String): Unit = scala.Console.err.print(text)

    override def print(text: String): Unit = scala.Console.print(text)

    override def printErrLn(text: String): Unit = scala.Console.err.println(text)

    override def printLn(text: String): Unit = scala.Console.println(text)
  }

  /** A capability trait representing the effect of console output operations.
    *
    * This trait defines a capability for performing console output operations in a controlled
    * manner. Following the capability-passing style pattern, programs requiring output operations
    * must receive an instance of `Output` through context parameters.
    */
  trait Unsafe extends Effect {

    /** Prints `text` to the console.
      *
      * @param text
      *   The text to print
      */
    def print(text: String): Unit

    /** Prints a line of `text` to the console.
      *
      * @param text
      *   The text to print
      */
    def printLn(text: String): Unit

    /** Prints an error `text` to the console.
      *
      * @param text
      *   The text to print
      */
    def printErr(text: String): Unit

    /** Prints an error line of `text` to the console.
      *
      * @param text
      *   The text to print
      */
    def printErrLn(text: String): Unit
  }
}
