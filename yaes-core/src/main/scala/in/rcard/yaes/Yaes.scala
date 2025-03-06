package in.rcard.yaes

class Yaes[+F <: Yaes.Effect](val unsafe: F)

object Yaes {

  /** Represents an effect. An Effect is an unpredictable interaction, usually with an external
    * system.
    */
  trait Effect

  extension [F <: Effect, A](eff: Yaes[F] ?=> A) {
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
  inline def handle[F <: Effect, A, B](
      inline program: Yaes[F] ?=> A
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
    def handle(program: Yaes[F] ?=> A): B // FIXME Make it inline
  }
}
