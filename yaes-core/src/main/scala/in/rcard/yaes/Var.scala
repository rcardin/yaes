package in.rcard.yaes

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

/**
 * Provides a mutable variable effect `Var[S]` for use within a Yaes effect context.
 *
 * A `Var[S]` represents a mutable reference to a value of type `S`, supporting get,
 * set, and update operations. This effect can be run in either a shared (global)
 * or isolated (per-thread) mode.
 *
 * @tparam S the type of the mutable state stored in the variable
 */
object Var {
  /**
   * Unsafe operations exposed by the `Var[S]` effect.
   *
   * These methods perform the low-level mutation and should only be invoked internally.
   *
   * @tparam S the state type
   */
  trait Unsafe[S] {
    /**
     * Get the current value of the variable.
     *
     * @return the current state
     */
    def get: S

    /**
     * Set the variable to a new value.
     *
     * @param newValue the value to assign
     * @return the previous state before assignment
     */
    def set(newValue: S): S

    /**
     * Update the variable by applying a function to its current value.
     *
     * @param f a function from the old state to the new state
     * @return the new state after the update
     */
    def update(f: S => S): S
  }

  /** Alias for the `Var[S]` effect in the Yaes effect system. */
  type Var[S] = Yaes[Unsafe[S]]

  /**
   * Retrieve the current value of the variable from the implicit `Var` context.
   *
   * @tparam S the state type
   * @param v  the implicit `Var[S]` effect instance
   * @return the current state
   */
  def get[S](using v: Var[S]): S =
    v.unsafe.get

  /**
   * Assign a new value to the variable in the implicit `Var` context.
   *
   * @tparam S the state type
   * @param newValue the value to assign
   * @param v        the implicit `Var[S]` effect instance
   * @return the previous state before assignment
   */
  def set[S](newValue: S)(using v: Var[S]): S =
    v.unsafe.set(newValue)

  /**
   * Update the variable using a transformation function in the implicit `Var` context.
   *
   * @tparam S the state type
   * @param f the function to apply to the current state
   * @param v the implicit `Var[S]` effect instance
   * @return the new state after update
   */
  def update[S](f: S => S)(using v: Var[S]): S =
    v.unsafe.update(f)

  /**
   * Run a computation with a single shared mutable `Var` instance.
   *
   * All accesses and updates within `block` will see and modify the same state.
   *
   * @param initial the initial value of the variable
   * @param block   the computation using the `Var` effect
   * @tparam S the state type
   * @tparam A the result type of the block
   * @return the result of the computation
   *
   * Example:
   * {{
   *   Var.runShared(0) {
   *     Var.update(_ + 1)
   *     println(Var.get) // prints 1
   *   }
   * }}
   */
  def runShared[S, A](initial: S)(block: Var[S] ?=> A): A = {
    val ref = new AtomicReference[S](initial)
    val unsafeImpl = new Unsafe[S] {
      def get: S = ref.get()

      def set(v: S): S = {
        val old = ref.getAndSet(v)
        old
      }

      def update(f: S => S): S = {
        val op = new UnaryOperator[S] {
          override def apply(t: S): S = f(t)
        }
        ref.updateAndGet(op)
      }
    }
    val handler = new Yaes.Handler[Unsafe[S], A, A] {
      override def handle(program: Var[S] ?=> A): A =
        program(using new Yaes(unsafeImpl))
    }
    Yaes.handle(block)(using handler)
  }

  /**
   * Run a computation with an isolated `Var` instance per thread.
   *
   * Each thread accessing the `Var` effect will see and modify its own copy
   * of the state, initialized to `initial` on first access.
   *
   * @param initial the initial value for each thread's variable
   * @param block   the computation using the `Var` effect
   * @tparam S the state type
   * @tparam A the result type of the block
   * @return the result of the computation
   *
   * Example:
   * {{
   *   Var.runIsolated(0) {
   *     // In each thread, Var.get starts at 0 independently
   *   }
   * }}
   */
  def runIsolated[S, A](initial: S)(block: Var[S] ?=> A): A = {
    val tl = new ThreadLocal[S] {
      override def initialValue(): S = initial
    }

    val unsafeImpl = new Unsafe[S] {
      def get: S = tl.get()
      def set(newValue: S): S = {
        val old = tl.get()
        tl.set(newValue)
        old
      }
      def update(f: S => S): S = {
        val updated = f(tl.get())
        tl.set(updated)
        updated
      }
    }

    val handler = new Yaes.Handler[Unsafe[S], A, A] {
      override def handle(program: Var[S] ?=> A): A =
        program(using new Yaes(unsafeImpl))
    }

    Yaes.handle(block)(using handler)
  }
}
