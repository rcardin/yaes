package in.rcard.yaes

/** The Reader effect type, representing a computation that can read a value of type `R`.
  *
  * @tparam R
  *   the type of the environment value
  */
type Reader[R] = Reader.Unsafe[R]

/** Infix type alias for Reader effect. `A reads R` is equivalent to `Reader[R] ?=> A`.
  *
  * @tparam A
  *   the result type of the computation
  * @tparam R
  *   the type of the environment value
  *
  * @example
  * {{{
  * def getRetries: Int reads Config =
  *   Reader.read[Config].maxRetries
  * }}}
  */
infix type reads[A, R] = Reader[R] ?=> A

/** Reader effect for read-only access to environment values.
  *
  * The Reader effect allows computations to access a shared environment value of type `R` in a
  * purely functional manner. The value is immutable within a scope and can be temporarily overridden
  * via [[local]].
  *
  * @example
  * {{{
  * case class Config(maxRetries: Int, timeout: Int)
  *
  * val result = Reader.run(Config(3, 5000)) {
  *   val retries = Reader.read[Config].maxRetries // 3
  *   val modified = Reader.local(_.copy(maxRetries = 10)) {
  *     Reader.read[Config].maxRetries // 10
  *   }
  *   val restored = Reader.read[Config].maxRetries // 3
  *   (retries, modified, restored)
  * }
  * // result = (3, 10, 3)
  * }}}
  */
object Reader {

  /** Reads the current environment value.
    *
    * @tparam R
    *   the type of the environment value
    * @param interpreter
    *   the Reader effect interpreter
    * @return
    *   the current environment value
    *
    * @example
    * {{{
    * Reader.run(42) {
    *   val value = Reader.read[Int] // 42
    * }
    * }}}
    */
  def read[R](using interpreter: Reader[R]): R =
    interpreter.value

  /** Runs a block with a modified environment value. The modification function `f` is applied to the
    * current value to produce a new one for the inner block. The original value is restored after the
    * block completes.
    *
    * Thread-safe by construction: creates a fresh `Reader` instance for the inner block rather than
    * mutating shared state.
    *
    * @tparam R
    *   the type of the environment value
    * @tparam A
    *   the result type of the block
    * @param f
    *   the function to transform the current environment value
    * @param block
    *   the computation to execute with the modified environment
    * @param interpreter
    *   the Reader effect interpreter
    * @return
    *   the result of the block
    *
    * @example
    * {{{
    * Reader.run(Config(3, 5000)) {
    *   Reader.local(_.copy(maxRetries = 10)) {
    *     Reader.read[Config].maxRetries // 10
    *   }
    *   // maxRetries is 3 again here
    * }
    * }}}
    */
  def local[R, A](f: R => R)(block: Reader[R] ?=> A)(using interpreter: Reader[R]): A =
    run(f(interpreter.value))(block)

  /** Runs a computation with the Reader effect, providing a value to be read.
    *
    * Returns `A` directly (not a tuple), since the environment value is immutable.
    *
    * @tparam R
    *   the type of the environment value
    * @tparam A
    *   the result type of the computation
    * @param value
    *   the environment value to provide
    * @param block
    *   the computation to execute with access to the environment value
    * @return
    *   the result of the computation
    *
    * @example
    * {{{
    * val result = Reader.run(42) {
    *   Reader.read[Int] * 2
    * }
    * // result = 84
    * }}}
    */
  def run[R, A](value: R)(block: Reader[R] ?=> A): A = {
    val currentValue = value
    val interpreter = new Unsafe[R] {
      override def value: R = currentValue
    }
    block(using interpreter)
  }

  /** Unsafe interface for Reader operations.
    *
    * This trait defines the low-level interface for reading the environment value. It is marked as
    * "Unsafe" because it provides direct access to the value without the safety guarantees provided
    * by the higher-level Reader effect API. Users should typically use the safe Reader effect
    * operations instead of implementing this trait directly.
    *
    * @tparam R
    *   the type of the environment value
    */
  trait Unsafe[R] {

    /** Returns the current environment value.
      *
      * @return
      *   the environment value
      */
    def value: R
  }
}
