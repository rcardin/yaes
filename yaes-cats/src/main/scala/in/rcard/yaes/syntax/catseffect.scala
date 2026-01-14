package in.rcard.yaes.syntax

import cats.effect.{IO => CatsIO}
import in.rcard.yaes.{Sync => YaesSync}
import in.rcard.yaes.Raise
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.annotation.targetName

/** Syntax extensions for Cats Effect IO to integrate with YAES Sync effect.
  *
  * Import this object to get extension methods for converting Cats Effect IO
  * computations to YAES Sync programs.
  *
  * Example:
  * {{{
  * import in.rcard.yaes.syntax.catseffect.given
  * import in.rcard.yaes.{IO => YaesSync}
  * import cats.effect.{IO => CatsIO}
  *
  * val catsIO: CatsIO[Int] = CatsIO.pure(42)
  *
  * val result = YaesSync.run {
  *   Raise.either {
  *     catsIO.value  // Fluent style
  *   }
  * }
  * }}}
  */
object catseffect extends CatsEffectSyntax

/** Trait containing Cats Effect IO syntax extensions.
  *
  * This trait can be mixed in to provide Cats Effect IO extension methods.
  */
trait CatsEffectSyntax {

  extension [A](io: CatsIO[A])
    /** Converts this Cats Effect IO to a YAES Sync program.
      *
      * This is an extension method that provides fluent syntax for the conversion.
      * Exceptions are raised via `Raise[Throwable]`.
      *
      * Example:
      * {{{
      * import in.rcard.yaes.syntax.catseffect.given
      * import cats.effect.{IO => CatsIO}
      * import in.rcard.yaes.{IO => YaesSync, Raise}
      *
      * val catsIO: CatsIO[Int] = CatsIO.pure(42)
      *
      * val result = YaesSync.run {
      *   Raise.either {
      *     catsIO.value  // Fluent style
      *   }
      * }
      * }}}
      *
      * @return
      *   A YAES Sync program that executes the Cats Effect computation
      */
    @targetName("valueExtension")
    def value: (in.rcard.yaes.Sync, Raise[Throwable]) ?=> A =
      valueImpl(io, Duration.Inf)

    /** Converts this Cats Effect IO to a YAES Sync program with a timeout.
      *
      * This is an extension method that provides fluent syntax for the conversion with timeout
      * protection. Exceptions including `TimeoutException` are raised via `Raise[Throwable]`.
      *
      * Example:
      * {{{
      * import in.rcard.yaes.syntax.catseffect.given
      * import cats.effect.{IO => CatsIO}
      * import in.rcard.yaes.{IO => YaesSync, Raise}
      * import scala.concurrent.duration._
      *
      * val catsIO: CatsIO[Int] = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)
      *
      * val result = YaesSync.run {
      *   Raise.fold(
      *     catsIO.value(5.seconds)  // Fluent style with timeout
      *   )(
      *     error => -1  // Handle timeout
      *   )(
      *     value => value
      *   )
      * }
      * }}}
      *
      * @param timeout
      *   Maximum time to wait for completion
      * @return
      *   A YAES Sync program with timeout protection
      */
    @targetName("valueWithTimeoutExtension")
    def value(timeout: Duration): (in.rcard.yaes.Sync, Raise[Throwable]) ?=> A =
      valueImpl(io, timeout)

  /** Internal implementation for converting Cats Effect IO to YAES Sync.
    */
  private def valueImpl[A](catsIO: CatsIO[A], timeout: Duration): (in.rcard.yaes.Sync, Raise[Throwable]) ?=> A = {
    in.rcard.yaes.Sync.apply {
      Raise.catching {
        import cats.effect.unsafe.implicits.global as runtime
        val future = catsIO.unsafeToFuture()(using runtime)
        Await.result(future, timeout)
      } { ex => ex }
    }
  }
}
