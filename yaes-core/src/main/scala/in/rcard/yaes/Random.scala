package in.rcard.yaes

/** A capability trait representing random number generation effects. It provides basic random number generation operations that can be used in effectful computations.
  */
trait Random extends Effect {
  /** Generates a random integer.
    *
    * @return A random integer
    */
  def nextInt(): Int
  /** Generates a random boolean.
    *
    * @return A random boolean
    */
  def nextBoolean(): Boolean
  /** Generates a random double.
    *
    * @return A random double
    */
  def nextDouble(): Double
  /** Generates a random long.
    *
    * @return A random long
    */
  def nextLong(): Long
}

/** Companion object for the Random effect providing utility methods and handlers.
  *
  * This object contains:
  * - Convenience methods for random number generation using capability passing style
  * - A method to construct effect-dependent computations
  * - A handler implementation to run Random effects
  */
object Random {

  /** Creates a computation that depends on the Random capability.
    *
    * @param block The computation that requires the Random capability
    * @tparam A The type of the computation's result
    * @return A context function that requires Random and produces A
    */
  def apply[A](block: => A): Random ?=> A = block

  /** Generates a random integer using the current Random capability.
    *
    * @param r The implicit Random capability
    * @return A random integer
    */
  def nextInt(using r: Random): Int         = r.nextInt()
  /** Generates a random boolean using the current Random capability.
    *
    * @param r The implicit Random capability
    * @return A random boolean
    */
  def nextBoolean(using r: Random): Boolean = r.nextBoolean()
  /** Generates a random double using the current Random capability.
    *
    * @param r The implicit Random capability
    * @return A random double
    */
  def nextDouble(using r: Random): Double   = r.nextDouble()
  def nextLong(using r: Random): Long       = r.nextLong()

  /** Runs a computation that requires the Random capability.
    *
    * This method provides a handler that executes the Random effects using
    * the default Scala random number generator implementation.
    *
    * @param block The computation to run
    * @tparam A The type of the computation's result
    * @return The result of the computation
    */
  def run[A](block: Random ?=> A): A = {
    val handler = new Effect.Handler[Random, A, A] {
      override def handle(program: Random ?=> A): A = program(using Random.unsafe)
    }
    Effect.handle(block)(using handler)
  }

  private val unsafe: Random = new Random {
    override def nextInt(): Int         = scala.util.Random.nextInt()
    override def nextLong(): Long       = scala.util.Random.nextLong()
    override def nextBoolean(): Boolean = scala.util.Random.nextBoolean()
    override def nextDouble(): Double   = scala.util.Random.nextDouble()
  }
}
