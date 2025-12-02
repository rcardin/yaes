package in.rcard.yaes

import language.experimental.captureChecking
import caps.{cap, Capability}

/** Represents an effect built on top of an effect `F`. The effect is represented by the `unsafe`
  * field, and it should not be handled directly but through the [[Yaes.Handler]] interface.
  *
  * @param unsafe
  *   An instance of the effect `F`
  * @tparam F
  *   The effect type
  */
class Yaes[+F](val unsafe: F)

/** Represents a capability-aware effect built on top of an effect `F`. This wrapper extends
  * `caps.Capability` to enable Scala 3 capture checking, preventing effect contexts from escaping
  * their handler scopes.
  *
  * Used by effects that require compile-time guarantees against context leaking (e.g., Raise,
  * State).
  *
  * @param unsafe
  *   An instance of the effect `F`
  * @tparam F
  *   The effect type
  */
class CapableYaes[+F](override val unsafe: F) extends Yaes[F](unsafe) with Capability

object Yaes {

  extension [F, A](eff: Yaes[F] ?=> A) {
    inline def map[B](inline f: A => B): Yaes[F] ?=> B                 = eff.flatMap(a => f(a))
    inline def flatMap[B](inline f: A => Yaes[F] ?=> B): Yaes[F] ?=> B = f(eff)
  }

  /** Handles the effectful computation in input using the given handler
    * @param program
    *   The effectful computation to be handled
    * @param handler
    *   The handler to be used to handle the effectful computation
    * @tparam F
    *   The effect type
    * @tparam A
    *   The result type of the effectful computation
    * @tparam B
    *   The result type of the effectful computation after the handling
    */
  inline def handle[F, A, B](
      inline program: Yaes[F] ?=> A
  )(using inline handler: Handler[F, A, B]): B = {
    handler.handle(program)
  }

  /** Represents a handler for an effectful computation */
  trait Handler[F, A, B] {

    /** Handles the effectful computation in input producing a result
      * @param program
      *   The effectful computation to be handled
      * @tparam F
      *   The effect type
      * @tparam A
      *   The result type of the effectful computation
      * @tparam B
      *   The result type of the effectful computation after the handling
      */
    def handle(program: Yaes[F] ?=> A): B // FIXME Make it inline
  }
}
