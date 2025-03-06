package in.rcard.yaes

import in.rcard.yaes.Raise.Raise

import java.io.IOException

/** A capability trait representing console input operations in a capability-passing style.
  *
  * The `Input` trait provides a safe abstraction for reading input from the console while handling
  * potential IOExceptions through the effect system.
  *
  * Example:
  * {{{
  * def readName()(using input: Input, output: Output, raise: Raise[IOException]): String =
  *   output.println("What's your name?")
  *   val name = input.readLn()
  *   output.println(s"Hello, $name!")
  * }}}
  */
trait Input extends Effect {
  def readLn()(using t: Raise[IOException]): String
}

/** Companion object for the Input capability, providing utility methods and handlers.
  *
  * This object contains methods to run Input operations and a default unsafe implementation that
  * directly interacts with the console.
  *
  * Example usage:
  * {{{
  * // Program that requires Input capability
  * val name = Input.readLn()
  *
  * // Run a program that requires Input capability
  * val result = Input.run {
  *   name
  * }
  * }}}
  */
object Input {

  /** Lifts a block of code into the Input effect.
    *
    * @param block
    *   The code block to be lifted into the Input effect
    * @param in
    *   The Input capability provided through context parameters
    * @return
    *   The block with the Input capability
    */
  def apply[A](block: => A)(using in: Input): A = block

  /** Reads a line of input from the console.
    *
    * @param input
    *   The Input capability provided through context parameters
    * @return
    *   The line of input read from the console
    */
  def readLn()(using input: Input)(using t: Raise[IOException]): String = input.readLn()

  /** Runs a program that requires Input capability.
    *
    * This method handles the Input effect by supplying the implementation that directly interfaces
    * with the system console.
    *
    * Example usage:
    * {{{
    *   val name = Input.run {
    *     Input.readLn()
    *   }
    * }}}
    *
    * @param block
    *   The code block to be run with the Input capability
    * @return
    *   The result of the program
    */
  def run[A](block: Input ?=> A): A = {
    val handler = new Effect.Handler[Input, A, A] {
      override def handle(program: Input ?=> A): A = program(using Input.unsafe)
    }
    Effect.handle(block)(using handler)
  }

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
