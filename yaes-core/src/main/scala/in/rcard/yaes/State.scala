package in.rcard.yaes

import language.experimental.captureChecking
import caps.cap
import in.rcard.yaes.Yaes.Handler

type State[S] = CapableYaes[State.Unsafe[S]]

/**
 * State effect for managing stateful computations in a functional way.
 *
 * The State effect allows you to work with mutable state in a purely functional manner,
 * providing operations to get, set, update, and use the current state value. The state
 * is managed within a controlled scope and can be safely accessed and modified through
 * the provided operations.
 *
 * @example
 * {{{
 * val (finalState, result) = State.run(0) {
 *   val current = State.get[Int]
 *   State.set(current + 1)
 *   State.get[Int] * 2
 * }
 * // finalState = 1, result = 2
 * }}}
 */
object State {

  /**
   * Retrieves the current state value.
   *
   * @tparam S the type of the state
   * @param interpreter the State effect interpreter
   * @return the current state value
   *
   * @example
   * {{{
   * State.run(42) {
   *   val currentValue = State.get[Int]
   *   // currentValue = 42
   * }
   * }}}
   */
  def get[S](using interpreter: State[S]^{cap}): S = {
    interpreter.unsafe.run(StateOp.Get())
  }

  /**
   * Sets the state to a new value and returns the previous state value.
   *
   * @tparam S the type of the state
   * @param value the new state value to set
   * @param interpreter the State effect interpreter
   * @return the previous state value
   *
   * @example
   * {{{
   * State.run(10) {
   *   val oldValue = State.set(20) // returns 10
   *   val newValue = State.get[Int] // returns 20
   * }
   * }}}
   */
  def set[S](value: S)(using interpreter: State[S]^{cap}): S = {
    interpreter.unsafe.run(StateOp.Set(value))
  }

  /**
   * Updates the current state using a transformation function and returns the new state.
   *
   * @tparam S the type of the state
   * @param f the function to transform the current state
   * @param interpreter the State effect interpreter
   * @return the updated state value
   *
   * @example
   * {{{
   * State.run(5) {
   *   val newValue = State.update[Int](_ * 2) // returns 10
   * }
   * }}}
   */
  def update[S](f: S -> S)(using interpreter: State[S]^{cap}): S = {
    interpreter.unsafe.run(StateOp.Update(f))
  }

  /**
   * Applies a function to the current state and returns the result without modifying the state.
   *
   * @tparam S the type of the state
   * @tparam A the type of the result
   * @param f the function to apply to the current state
   * @param interpreter the State effect interpreter
   * @return the result of applying the function to the current state
   *
   * @example
   * {{{
   * State.run("hello") {
   *   val length = State.use[String, Int](_.length) // returns 5
   *   val state = State.get[String] // still "hello"
   * }
   * }}}
   */
  def use[S, A](f: S -> A)(using interpreter: State[S]^{cap}): A = {
    interpreter.unsafe.run(StateOp.Use(f))
  }

  /**
   * Algebraic data type representing the operations available in the State effect.
   *
   * @tparam S the type of the state being managed
   * @tparam A the type of the result produced by the operation
   */
  enum StateOp[S, A] {
    /** Retrieves the current state value. */
    case Get()           extends StateOp[S, S]
    /** Sets the state to a new value, returning the previous value. */
    case Set(value: S)   extends StateOp[S, S]
    /** Updates the state using a transformation function, returning the new value. */
    case Update(f: S -> S) extends StateOp[S, S]
    /** Applies a function to the current state without modifying it. */
    case Use(f: S -> A)  extends StateOp[S, A]
  }

  /**
   * Runs a stateful computation with an initial state value.
   *
   * This function creates a controlled environment for stateful computations, where
   * the state can be safely accessed and modified through the State effect operations.
   * The computation is executed and both the final state and the result are returned.
   *
   * '''Note:''' This implementation is not thread-safe. If you need to run stateful
   * computations concurrently, ensure proper synchronization or use separate state
   * instances for each thread.
   *
   * @tparam S the type of the state being managed
   * @tparam A the type of the result produced by the computation
   * @param initialState the initial value of the state
   * @param block the stateful computation to execute
   * @return a tuple containing the final state and the computation result
   *
   * @example
   * {{{
   * val (finalState, result) = State.run(List.empty[Int]) {
   *   State.update[List[Int]](_ :+ 1)
   *   State.update[List[Int]](_ :+ 2)
   *   State.use[List[Int], Int](_.sum)
   * }
   * // finalState = List(1, 2), result = 3
   * }}}
   */
  def run[S, A](initialState: S)(block: State[S]^{cap} ?=> A): (S, A) = {

    var currentState = initialState

    val interpreter = new Unsafe[S] {

      override def run[A](op: StateOp[S, A]): A = op match {
        case StateOp.Get() =>
          currentState
        case StateOp.Set(value) =>
          val oldState = currentState
          currentState = value
          oldState
        case StateOp.Update(f) =>
          currentState = f(currentState)
          currentState
        case StateOp.Use(f) =>
          f(currentState)
      }
    }

    given state: State[S] = CapableYaes(interpreter)
    val result = block
    (currentState, result)
  }

  /**
   * Unsafe interface for executing state operations.
   *
   * This trait defines the low-level interface for executing state operations.
   * It is marked as "Unsafe" because it provides direct access to state mutation
   * without the safety guarantees provided by the higher-level State effect API.
   * Users should typically use the safe State effect operations instead of
   * implementing this trait directly.
   *
   * @tparam S the type of the state being managed
   */
  trait Unsafe[S] {
    /**
     * Executes a state operation and returns its result.
     *
     * @tparam A the type of the result produced by the operation
     * @param op the state operation to execute
     * @return the result of executing the operation
     */
    def run[A](op: StateOp[S, A]): A
  }

}
