package in.rcard.yaes.interop.cats

import in.rcard.yaes.{IO => YaesIO}
import cats.effect.{IO => CatsIO}
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration.Duration
import scala.annotation.targetName

/** Conversion utilities between YAES IO and Cats Effect IO.
  *
  * This object provides extension methods to convert between YAES's context function-based IO
  * effect and Cats Effect's monadic IO.
  */
object Cats {

  /** Converts a YAES IO program to Cats Effect IO.
    *
    * This conversion runs the YAES handler to obtain a Future[A], then lifts it into Cats
    * Effect's IO monad using `IO.fromFuture`.
    *
    * Example:
    * {{{
    * import in.rcard.yaes.{IO => YaesIO}
    * import in.rcard.yaes.interop.cats._
    * import scala.concurrent.ExecutionContext.Implicits.global
    *
    * val yaesProgram: YaesIO ?=> Int = YaesIO {
    *   println("Hello from YAES")
    *   42
    * }
    *
    * val catsIO: CatsIO[Int] = Cats.run(yaesProgram)
    * }}}
    *
    * @param yaesProgram
    *   The YAES IO program to convert
    * @param ec
    *   The execution context for running the YAES handler
    * @tparam A
    *   The result type of the program
    * @return
    *   A Cats Effect IO containing the same computation
    */
  def run[A](yaesProgram: in.rcard.yaes.IO ?=> A)(using ec: ExecutionContext): CatsIO[A] = {
    CatsIO.fromFuture(CatsIO(in.rcard.yaes.IO.run(yaesProgram)))
  }

  /** Converts a Cats Effect IO to a YAES IO program.
    *
    * This conversion executes the Cats Effect IO within a YAES IO context. The Cats IO is
    * converted to a Future using `unsafeToFuture`, which is then blocked on within the YAES IO
    * effect. This method uses the Cats Effect global runtime for execution.
    *
    * Note: This method uses blocking operations and should be used at application boundaries
    * rather than in hot paths.
    *
    * Example:
    * {{{
    * import cats.effect.{IO => CatsIO}
    * import in.rcard.yaes.{IO => YaesIO}
    * import in.rcard.yaes.interop.cats._
    *
    * val catsIO: CatsIO[Int] = CatsIO.pure(42)
    *
    * val result = YaesIO.run {
    *   Cats.value(catsIO)
    * }
    * }}}
    *
    * @param catsIO
    *   The Cats Effect IO to convert
    * @tparam A
    *   The result type
    * @return
    *   A YAES IO program that executes the Cats Effect computation
    */
  def value[A](catsIO: CatsIO[A]): in.rcard.yaes.IO ?=> A = {
    valueImpl(catsIO, Duration.Inf)
  }

  /** Converts a Cats Effect IO to a YAES IO program with a timeout.
    *
    * This conversion executes the Cats Effect IO within a YAES IO context with timeout protection.
    * The Cats IO is converted to a Future using `unsafeToFuture`, which is then blocked on within
    * the YAES IO effect. This method uses the Cats Effect global runtime for execution.
    *
    * Note: This method uses blocking operations and should be used at application boundaries
    * rather than in hot paths.
    *
    * Example:
    * {{{
    * import cats.effect.{IO => CatsIO}
    * import in.rcard.yaes.{IO => YaesIO}
    * import in.rcard.yaes.interop.cats._
    * import scala.concurrent.duration._
    *
    * val catsIO: CatsIO[Int] = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)
    *
    * val result = YaesIO.run {
    *   Cats.value(catsIO, 5.seconds)
    * }
    * // Will timeout after 5 seconds
    * }}}
    *
    * @param catsIO
    *   The Cats Effect IO to convert
    * @param timeout
    *   Maximum time to wait for completion
    * @tparam A
    *   The result type
    * @return
    *   A YAES IO program with timeout protection
    */
  def value[A](catsIO: CatsIO[A], timeout: Duration): in.rcard.yaes.IO ?=> A = {
    valueImpl(catsIO, timeout)
  }

  /** Internal implementation for converting Cats Effect IO to YAES IO.
    *
    * @param catsIO
    *   The Cats Effect IO to convert
    * @param timeout
    *   Maximum time to wait for completion
    * @tparam A
    *   The result type
    * @return
    *   A YAES IO program that executes the Cats Effect computation
    */
  private def valueImpl[A](catsIO: CatsIO[A], timeout: Duration): in.rcard.yaes.IO ?=> A = {
    in.rcard.yaes.IO.apply {
      import cats.effect.unsafe.implicits.global as runtime
      val future = catsIO.unsafeToFuture()(using runtime)
      Await.result(future, timeout)
    }
  }

  /** Extension methods for converting Cats Effect IO to YAES IO.
    *
    * These extension methods provide a fluent API for converting Cats Effect computations to YAES
    * IO programs. They delegate to the corresponding functions in Cats.
    */
  extension [A](catsIO: CatsIO[A])
    /** Converts this Cats Effect IO to a YAES IO program.
      *
      * This is an extension method that provides fluent syntax for the conversion. It delegates
      * to [[value]].
      *
      * Example:
      * {{{
      * import cats.effect.{IO => CatsIO}
      * import in.rcard.yaes.{IO => YaesIO}
      * import in.rcard.yaes.interop.cats._
      *
      * val catsIO: CatsIO[Int] = CatsIO.pure(42)
      *
      * val result = YaesIO.run {
      *   catsIO.value  // Fluent style
      * }
      * }}}
      *
      * @return
      *   A YAES IO program that executes the Cats Effect computation
      */
    @targetName("valueExtension")
    def value: in.rcard.yaes.IO ?=> A =
      valueImpl(catsIO, Duration.Inf)

    /** Converts this Cats Effect IO to a YAES IO program with a timeout.
      *
      * This is an extension method that provides fluent syntax for the conversion with timeout
      * protection. It delegates to [[value]].
      *
      * Example:
      * {{{
      * import cats.effect.{IO => CatsIO}
      * import in.rcard.yaes.{IO => YaesIO}
      * import in.rcard.yaes.interop.cats._
      * import scala.concurrent.duration._
      *
      * val catsIO: CatsIO[Int] = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)
      *
      * val result = YaesIO.run {
      *   catsIO.value(5.seconds)  // Fluent style with timeout
      * }
      * }}}
      *
      * @param timeout
      *   Maximum time to wait for completion
      * @return
      *   A YAES IO program with timeout protection
      */
    @targetName("valueWithTimeoutExtension")
    def value(timeout: Duration): in.rcard.yaes.IO ?=> A =
      valueImpl(catsIO, timeout)
}
