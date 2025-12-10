package in.rcard

/** YAES (Yet Another Effect System) - Core package.
  *
  * This package object provides type aliases for the Cats Effect integration.
  */
package object yaes {
  // Type aliases for Cats Effect integration
  type CatsIO[A] = _root_.cats.effect.IO[A]
}
