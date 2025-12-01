package in.rcard

/** YAES (Yet Another Effect System) - Core package.
  *
  * This package object provides type aliases and re-exports for the Cats Effect integration.
  */
package object yaes {
  // Re-export Cats conversions for convenient access
  export Cats._

  // Type aliases for Cats Effect integration
  type CatsIO[A] = _root_.cats.effect.IO[A]
}
