package in.rcard.yaes

/** Type class representing an effectful computation */
trait Effect

extension [F <: Effect, A](inline eff: F ?=> A) {

  /** Sequences the given effectful computation `eff` with the one in input
    * @param f
    *   The effectful computation to be sequenced after `eff`
    */
  inline def flatMap[B](inline f: A => F ?=> B): F ?=> B = f(eff)

  /** Maps the result of the effectful computation `eff`
    * @param f
    *   The function to be applied to the result of the effectful computation
    */
  inline def map[B](inline f: A => B): F ?=> B = eff.flatMap(a => f(a))
}

object Effect {

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
  inline def handle[F <: Effect, A, B](
      inline program: F ?=> A
  )(using inline handler: Handler[F, A, B]): B = {
    handler.handle(program)
  }

  /** Represents a handler for an effectful computation */
  trait Handler[F <: Effect, A, B] {

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
    def handle(program: F ?=> A): B // FIXME Make it inline
  }
}
