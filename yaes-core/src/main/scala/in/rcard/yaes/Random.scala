package in.rcard.yaes

type Random = Random.Unsafe

/** Companion object for the Random effect providing utility methods and handlers.
  *
  * This object contains:
  *   - Convenience methods for random number generation
  *   - A method to construct effect-dependent computations
  *   - A handler implementation to run Random effects
  */
object Random {

  /** Creates a computation that depends on the Random effect.
    *
    * @param block
    *   The computation that requires the Random effect
    * @tparam A
    *   The type of the computation's result
    * @return
    *   A context function that requires Random and produces A
    */
  def apply[A](block: => A): Random ?=> A = block

  /** Generates a random integer using the current Random effect.
    *
    * @param r
    *   The implicit Random effect
    * @return
    *   A random integer
    */
  def nextInt(using r: Random): Int = r.nextInt()

  /** Generates a random boolean using the current Random effect.
    *
    * @param r
    *   The implicit Random effect
    * @return
    *   A random boolean
    */
  def nextBoolean(using r: Random): Boolean = r.nextBoolean()

  /** Generates a random double using the current Random effect.
    *
    * @param r
    *   The implicit Random effect
    * @return
    *   A random double
    */
  def nextDouble(using r: Random): Double = r.nextDouble()

  /** Generates a random long using the current Random effect.
    *
    * @param r
    *   The implicit Random effect
    * @return
    *   A random long
    */
  def nextLong(using r: Random): Long     = r.nextLong()

  /** Runs a computation that requires the Random effect.
    *
    * This method provides a handler that executes the Random effects using the default Scala random
    * number generator implementation.
    *
    * @param block
    *   The computation to run
    * @tparam A
    *   The type of the computation's result
    * @return
    *   The result of the computation
    */
  def run[A](block: Random ?=> A): A = block(using Random.unsafe)

  private val unsafe = new Random.Unsafe {
    override def nextInt(): Int         = scala.util.Random.nextInt()
    override def nextLong(): Long       = scala.util.Random.nextLong()
    override def nextBoolean(): Boolean = scala.util.Random.nextBoolean()
    override def nextDouble(): Double   = scala.util.Random.nextDouble()
  }

  /** An effect trait representing random number generation effects. It provides basic random
    * number generation operations that can be used in effectful computations.
    *
    * This trait is unsafe because it provides direct access to the random number generator
    * implementation.
    */
  trait Unsafe {

    /** Generates a random integer.
      *
      * @return
      *   A random integer
      */
    def nextInt(): Int

    /** Generates a random boolean.
      *
      * @return
      *   A random boolean
      */
    def nextBoolean(): Boolean

    /** Generates a random double.
      *
      * @return
      *   A random double
      */
    def nextDouble(): Double

    /** Generates a random long.
      *
      * @return
      *   A random long
      */
    def nextLong(): Long
  }
}
