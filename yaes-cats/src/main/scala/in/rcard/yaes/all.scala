package in.rcard.yaes

import in.rcard.yaes.instances.RaiseInstances
import in.rcard.yaes.syntax.AccumulateSyntax

/** Unified import object for all YAES Cats integrations.
  *
  * This object provides a single import for all Cats-related functionality:
  * - Typeclass instances (MonadError for Raise)
  * - Syntax extensions (combineErrors, combineErrorsS)
  * - Cats Effect conversions (Cats.run, Cats.value)
  * - Validated conversions (CatsValidated.validated, etc.)
  * - Accumulation functions (CatsAccumulate.mapAccumulating, etc.)
  *
  * Example:
  * {{{
  * import in.rcard.yaes.all.given
  * import in.rcard.yaes.all.*
  * import cats.syntax.all.*
  *
  * def computation1: Int raises String = Raise.raise("error")
  * def computation2: Int raises String = 42
  *
  * // Use Cats combinators with Raise
  * val result: Int raises String = computation1.handleError(_ => computation2)
  *
  * // Use accumulation syntax
  * val accumulated: List[Int] raises NonEmptyList[String] = 
  *   List(computation1, computation2).combineErrors
  * }}}
  */
object all extends RaiseInstances with AccumulateSyntax {
  
  // Re-export Cats Effect conversions
  export Cats.*
  
  // Re-export Validated conversions
  export CatsValidated.*
  
  // Re-export accumulation functions (not syntax, which is inherited)
  export CatsAccumulate.{mapAccumulating, mapAccumulatingS}
}
