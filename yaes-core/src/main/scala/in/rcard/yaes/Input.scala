package in.rcard.yaes

import in.rcard.yaes.Raise.Raise

import java.io.IOException

/** Companion object for the Input effect, providing utility methods and handlers.
  *
  * This object contains methods to run Input operations and a default unsafe implementation that
  * directly interacts with the console.
  *
  * Example usage:
  * {{{
  * // Program that requires Input effect
  * val name = Input.readLn()
  *
  * // Run a program that requires Input effect
  * val result = Input.run {
  *   name
  * }
  * }}}
  */
object Input {

  type Input = Yaes[Input.Unsafe]

  /** Lifts a block of code into the Input effect.
    *
    * @param block
    *   The code block to be lifted into the Input effect
    * @param in
    *   The Input effect provided through context parameters
    * @return
    *   The block with the Input effect
    */
  def apply[A](block: => A)(using in: Input): A = block

  /** Reads a line of input from the console.
    *
    * @param input
    *   The Input effect provided through context parameters
    * @return
    *   The line of input read from the console
    */
  def readLn()(using input: Input)(using t: Raise[IOException]): String = input.unsafe.readLn()

  /** Runs a program that requires Input effect.
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
    *   The code block to be run with the Input effect
    * @return
    *   The result of the program
    */
  def run[A](block: Input ?=> A): A = {
    val handler = new Yaes.Handler[Input.Unsafe, A, A] {
      override def handle(program: Input ?=> A): A = program(using Yaes(Input.unsafe))
    }
    Yaes.handle(block)(using handler)
  }

  val unsafe = new Input.Unsafe {
    override def readLn()(using t: Raise[IOException]): String = Raise {
      try {
        scala.io.StdIn.readLine()
      } catch {
        case e: IOException =>
          Raise.raise(e)
      }
    }
  }

  /** An effect trait representing console input operations.
    *
    * The `Input` trait provides a safe abstraction for reading input from the console while
    * handling potential IOExceptions through the effect system.
    *
    * Example:
    * {{{
    * def readName()(using input: Input, output: Output, raise: Raise[IOException]): String =
    *   output.println("What's your name?")
    *   val name = input.readLn()
    *   output.println(s"Hello, $name!")
    * }}}
    */
  trait Unsafe {
    def readLn()(using t: Raise[IOException]): String
  }
}
