package in.rcard

import cats.data.{NonEmptyChain, NonEmptyList}

/** YAES (Yet Another Effect System) - Core package.
  *
  * This package object provides type aliases for the Cats Effect integration.
  */
package object yaes {
  // Type aliases for Cats Effect integration
  type CatsIO[A] = _root_.cats.effect.IO[A]

  // Type aliases for Raise with Cats non-empty collections

  /** Type alias for `Raise[NonEmptyList[E]]`.
    *
    * Provides a convenient shorthand for error accumulation with `NonEmptyList`,
    * ensuring at least one error is present when raised.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{Raise, RaiseNel}
    * import in.rcard.yaes.Raise.accumulating
    * import in.rcard.yaes.instances.accumulate.given
    *
    * def validatePositive(n: Int)(using Raise[String]): Int =
    *   if (n > 0) n else Raise.raise(s"$n is not positive")
    *
    * val result: RaiseNel[String] ?=> (Int, Int) =
    *   Raise.accumulate[NonEmptyList, String, (Int, Int)] {
    *     val a = accumulating { validatePositive(-1) }
    *     val b = accumulating { validatePositive(-2) }
    *     (a, b)
    *   }
    * }}}
    */
  type RaiseNel[E] = Raise[NonEmptyList[E]]

  /** Type alias for `Raise[NonEmptyChain[E]]`.
    *
    * Provides a convenient shorthand for error accumulation with `NonEmptyChain`,
    * ensuring at least one error is present when raised.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{Raise, RaiseNec}
    * import in.rcard.yaes.Raise.accumulating
    * import in.rcard.yaes.instances.accumulate.given
    *
    * def validatePositive(n: Int)(using Raise[String]): Int =
    *   if (n > 0) n else Raise.raise(s"$n is not positive")
    *
    * val result: RaiseNec[String] ?=> List[Int] =
    *   Raise.accumulate[NonEmptyChain, String, List[Int]] {
    *     List(1, -2, 3).map { n =>
    *       accumulating { validatePositive(n) }
    *     }
    *   }
    * }}}
    */
  type RaiseNec[E] = Raise[NonEmptyChain[E]]
}
