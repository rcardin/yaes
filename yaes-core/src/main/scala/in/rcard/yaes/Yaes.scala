package in.rcard.yaes

/** Represents an effect built on top af an effect `F`. The effect is represented by the `unsafe`
  * field, and it should not be handled directly but throught the [[Yaes.Handler]] interface.
  *
  * @param unsafe
  *   An instance of the effect `F`
  * @tparam F
  *   The effect type
  */
class Yaes[F](val unsafe: F)

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
