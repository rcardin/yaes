package in.rcard.yaes

/** Represents an effect that handles the execution of a side-effecting operation.
  * @param sf
  *   The side-effecting operation to be executed.
  * @tparam F
  *   The type of the side-effecting operation.
  */
trait Effect

extension [F <: Effect, A](inline eff: F ?=> A) {
  inline def flatMap[B](inline f: A => F ?=> B): F ?=> B = f(eff)
  inline def map[B](inline f: A => B): F ?=> B           = eff.flatMap(a => f(a))
}
