package in.rcard.yaes

import scala.collection.mutable.ArrayBuffer

/** The Writer effect type, representing a computation that can accumulate values of type `W`.
  *
  * @tparam W
  *   the type of the values to accumulate
  */
type Writer[W] = Yaes[Writer.Unsafe[W]]

/** Infix type alias for Writer effect. `A writes W` is equivalent to `Writer[W] ?=> A`.
  *
  * @tparam A
  *   the result type of the computation
  * @tparam W
  *   the type of the values to accumulate
  *
  * @example
  * {{{
  * def computation: Int writes String = {
  *   Writer.write("log entry")
  *   42
  * }
  * }}}
  */
infix type writes[A, W] = Writer[W] ?=> A

/** Writer effect for pure, append-only value accumulation.
  *
  * The Writer effect allows computations to accumulate values (such as logs, events, or metrics) in
  * a purely functional manner. Values are collected into a `Vector[W]` and returned alongside the
  * computation result as a tuple `(Vector[W], A)`.
  *
  * @example
  * {{{
  * val (log, result) = Writer.run[String, Int] {
  *   Writer.write("starting")
  *   Writer.write("computing")
  *   42
  * }
  * // log = Vector("starting", "computing"), result = 42
  * }}}
  */
object Writer {

  /** Appends a single value to the accumulated output.
    *
    * @tparam W
    *   the type of the value to write
    * @param w
    *   the value to append
    * @param interpreter
    *   the Writer effect interpreter
    *
    * @example
    * {{{
    * Writer.run[String, Unit] {
    *   Writer.write("hello")
    *   Writer.write("world")
    * }
    * // (Vector("hello", "world"), ())
    * }}}
    */
  def write[W](w: W)(using interpreter: Writer[W]): Unit =
    interpreter.unsafe.write(w)

  /** Appends multiple values to the accumulated output.
    *
    * @tparam W
    *   the type of the values to write
    * @param ws
    *   the values to append
    * @param interpreter
    *   the Writer effect interpreter
    *
    * @example
    * {{{
    * Writer.run[Int, Unit] {
    *   Writer.writeAll(List(1, 2, 3))
    * }
    * // (Vector(1, 2, 3), ())
    * }}}
    */
  def writeAll[W](ws: IterableOnce[W])(using interpreter: Writer[W]): Unit =
    interpreter.unsafe.writeAll(ws)

  /** Captures the writes from a block, returning them alongside the block's result. Writes are also
    * forwarded to the outer Writer scope.
    *
    * @tparam W
    *   the type of the values being accumulated
    * @tparam A
    *   the result type of the block
    * @param block
    *   the computation whose writes should be captured
    * @param interpreter
    *   the Writer effect interpreter
    * @return
    *   a tuple of the captured writes and the block's result
    *
    * @example
    * {{{
    * val (outerLog, (innerLog, result)) = Writer.run[String, (Vector[String], Int)] {
    *   Writer.write("before")
    *   val captured = Writer.capture[String, Int] {
    *     Writer.write("inside")
    *     99
    *   }
    *   Writer.write("after")
    *   captured
    * }
    * // outerLog = Vector("before", "inside", "after")
    * // innerLog = Vector("inside"), result = 99
    * }}}
    */
  def capture[W, A](block: Writer[W] ?=> A)(using interpreter: Writer[W]): (Vector[W], A) = {
    val snapshot = interpreter.unsafe.snapshot
    val result   = block
    val captured = interpreter.unsafe.snapshot.drop(snapshot.size)
    (captured, result)
  }

  /** Runs a computation with the Writer effect, returning the accumulated values and the result.
    *
    * '''Note:''' This implementation is not thread-safe. If you need to run writer computations
    * concurrently, ensure proper synchronization or use separate writer instances for each thread.
    *
    * @tparam W
    *   the type of the values to accumulate
    * @tparam A
    *   the result type of the computation
    * @param block
    *   the computation to execute with the Writer effect
    * @return
    *   a tuple containing the accumulated values as a `Vector[W]` and the computation result
    *
    * @example
    * {{{
    * val (log, result) = Writer.run[String, Int] {
    *   Writer.write("start")
    *   Writer.writeAll(List("a", "b"))
    *   42
    * }
    * // log = Vector("start", "a", "b"), result = 42
    * }}}
    */
  def run[W, A](block: Writer[W] ?=> A): (Vector[W], A) = {

    val buffer = ArrayBuffer.empty[W]

    val handler = new Yaes.Handler[Writer.Unsafe[W], A, A] {

      override def handle(program: Writer[W] ?=> A): A = {
        val interpreter = new Unsafe[W] {

          override def write(w: W): Unit =
            buffer += w

          override def writeAll(ws: IterableOnce[W]): Unit =
            buffer ++= ws

          override def snapshot: Vector[W] =
            buffer.toVector
        }
        program(using Yaes(interpreter))
      }
    }

    val result = Yaes.handle(block)(using handler)
    (buffer.toVector, result)
  }

  /** Unsafe interface for Writer operations.
    *
    * This trait defines the low-level interface for writer operations. It is marked as "Unsafe"
    * because it provides direct access to mutable state without the safety guarantees provided by
    * the higher-level Writer effect API. Users should typically use the safe Writer effect operations
    * instead of implementing this trait directly.
    *
    * @tparam W
    *   the type of the values to accumulate
    */
  trait Unsafe[W] {

    /** Appends a single value to the accumulated output.
      *
      * @param w
      *   the value to append
      */
    def write(w: W): Unit

    /** Appends multiple values to the accumulated output.
      *
      * @param ws
      *   the values to append
      */
    def writeAll(ws: IterableOnce[W]): Unit

    /** Returns a snapshot of the currently accumulated values.
      *
      * @return
      *   the accumulated values as a `Vector[W]`
      */
    def snapshot: Vector[W]
  }
}
