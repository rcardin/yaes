package in.rcard.yaes

import in.rcard.yaes.{IO => YaesIO}
import in.rcard.yaes.Raise
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
    * Effect's IO monad using `IO.fromFuture`. The yaesProgram can use `Raise[Throwable]`
    * to handle exceptions in a typed way before conversion.
    *
    * Any errors raised via `Raise[Throwable]` will be converted to thrown exceptions
    * and propagated through the Cats Effect IO, maintaining semantic equivalence with
    * exception-based error handling.
    *
    * Example with error handling:
    * {{{
    * import in.rcard.yaes.{IO => YaesIO, Raise}
    * import in.rcard.yaes.Cats
    * import scala.concurrent.ExecutionContext.Implicits.global
    *
    * val yaesProgram: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
    *   Raise.catching {
    *     println("Hello from YAES")
    *     riskyOperation()  // Might throw
    *   } { ex => ex }
    * }
    *
    * val catsIO: CatsIO[Int] = Cats.run(yaesProgram)
    * // Exceptions are propagated through Cats Effect IO
    * }}}
    *
    * Example without explicit error handling:
    * {{{
    * val yaesProgram: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
    *   42
    * }
    *
    * val catsIO: CatsIO[Int] = Cats.run(yaesProgram)
    * val result = catsIO.unsafeRunSync()  // 42
    * }}}
    *
    * @param yaesProgram
    *   The YAES IO program to convert (can use Raise[Throwable] for typed error handling)
    * @param ec
    *   The execution context for running the YAES handler
    * @tparam A
    *   The result type of the program
    * @return
    *   A Cats Effect IO containing the same computation with errors as failures
    */
  def run[A](yaesProgram: (in.rcard.yaes.IO, Raise[Throwable]) ?=> A)(using ec: ExecutionContext): CatsIO[A] = {
    CatsIO.fromFuture(CatsIO {
      given Raise[Throwable] = new in.rcard.yaes.Yaes(new Raise.Unsafe[Throwable] {
        def raise(error: => Throwable): Nothing = throw error
      })
      in.rcard.yaes.IO.run(yaesProgram)
    })
  }

  /** Converts a Cats Effect IO to a YAES IO program.
    *
    * This conversion executes the Cats Effect IO within a YAES IO context. The Cats IO is
    * converted to a Future using `unsafeToFuture`, which is then blocked on within the YAES IO
    * effect. This method uses the Cats Effect global runtime for execution.
    *
    * Exceptions from both the Cats Effect execution and the blocking operation are raised
    * via `Raise[Throwable]`, allowing for typed error handling using Raise combinators
    * like `either`, `fold`, `option`, or `recover`.
    *
    * Note: This method uses blocking operations and should be used at application boundaries
    * rather than in hot paths. Virtual Threads handle blocking efficiently.
    *
    * Example with error handling:
    * {{{
    * import cats.effect.{IO => CatsIO}
    * import in.rcard.yaes.{IO => YaesIO, Raise}
    * import in.rcard.yaes.Cats
    *
    * val catsIO: CatsIO[Int] = CatsIO.raiseError(new RuntimeException("Error"))
    *
    * val result = YaesIO.run {
    *   Raise.either {
    *     Cats.value(catsIO)
    *   } match {
    *     case Right(value) => println(s"Success: $value")
    *     case Left(error) => println(s"Error: ${error.getMessage}")
    *   }
    * }
    * }}}
    *
    * Example with Raise.fold:
    * {{{
    * val result = YaesIO.run {
    *   Raise.fold(
    *     Cats.value(catsIO)
    *   )(
    *     error => 0  // Default value on error
    *   )(
    *     value => value
    *   )
    * }
    * }}}
    *
    * Common exceptions raised:
    * - `ExecutionException`: When the Future execution fails
    * - `RuntimeException`: From Cats Effect computation failures
    * - Any other `Throwable` from the computation
    *
    * @param catsIO
    *   The Cats Effect IO to convert
    * @tparam A
    *   The result type
    * @return
    *   A YAES IO program that executes the Cats Effect computation and raises exceptions via Raise[Throwable]
    */
  def value[A](catsIO: CatsIO[A]): (in.rcard.yaes.IO, Raise[Throwable]) ?=> A = {
    valueImpl(catsIO, Duration.Inf)
  }

  /** Converts a Cats Effect IO to a YAES IO program with a timeout.
    *
    * This conversion executes the Cats Effect IO within a YAES IO context with timeout protection.
    * The Cats IO is converted to a Future using `unsafeToFuture`, which is then blocked on within
    * the YAES IO effect. This method uses the Cats Effect global runtime for execution.
    *
    * Exceptions including `TimeoutException` from the blocking operation are raised
    * via `Raise[Throwable]`, allowing for typed error handling using Raise combinators.
    *
    * Note: This method uses blocking operations and should be used at application boundaries
    * rather than in hot paths. Virtual Threads handle blocking efficiently.
    *
    * Example with timeout handling:
    * {{{
    * import cats.effect.{IO => CatsIO}
    * import in.rcard.yaes.{IO => YaesIO, Raise}
    * import in.rcard.yaes.Cats
    * import scala.concurrent.duration._
    *
    * val catsIO: CatsIO[Int] = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)
    *
    * val result = YaesIO.run {
    *   Raise.fold(
    *     Cats.value(catsIO, 5.seconds)
    *   )(
    *     error => -1  // Default value on timeout or error
    *   )(
    *     value => value
    *   )
    * }
    * // Will return -1 after timeout
    * }}}
    *
    * Common exceptions raised:
    * - `TimeoutException`: When `Await.result` times out
    * - `ExecutionException`: When the Future execution fails
    * - `RuntimeException`: From Cats Effect computation failures
    * - Any other `Throwable` from the computation
    *
    * @param catsIO
    *   The Cats Effect IO to convert
    * @param timeout
    *   Maximum time to wait for completion
    * @tparam A
    *   The result type
    * @return
    *   A YAES IO program with timeout protection that raises exceptions via Raise[Throwable]
    */
  def value[A](catsIO: CatsIO[A], timeout: Duration): (in.rcard.yaes.IO, Raise[Throwable]) ?=> A = {
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
  private def valueImpl[A](catsIO: CatsIO[A], timeout: Duration): (in.rcard.yaes.IO, Raise[Throwable]) ?=> A = {
    in.rcard.yaes.IO.apply {
      Raise.catching {
        import cats.effect.unsafe.implicits.global as runtime
        val future = catsIO.unsafeToFuture()(using runtime)
        Await.result(future, timeout)
      } { ex => ex }
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
      * to [[value]]. Exceptions are raised via `Raise[Throwable]`.
      *
      * Example:
      * {{{
      * import cats.effect.{IO => CatsIO}
      * import in.rcard.yaes.{IO => YaesIO, Raise}
      * import in.rcard.yaes.Cats
      *
      * val catsIO: CatsIO[Int] = CatsIO.pure(42)
      *
      * val result = YaesIO.run {
      *   Raise.either {
      *     catsIO.value  // Fluent style
      *   }
      * }
      * }}}
      *
      * @return
      *   A YAES IO program that executes the Cats Effect computation
      */
    @targetName("valueExtension")
    def value: (in.rcard.yaes.IO, Raise[Throwable]) ?=> A =
      valueImpl(catsIO, Duration.Inf)

    /** Converts this Cats Effect IO to a YAES IO program with a timeout.
      *
      * This is an extension method that provides fluent syntax for the conversion with timeout
      * protection. It delegates to [[value]]. Exceptions including `TimeoutException` are raised
      * via `Raise[Throwable]`.
      *
      * Example:
      * {{{
      * import cats.effect.{IO => CatsIO}
      * import in.rcard.yaes.{IO => YaesIO, Raise}
      * import in.rcard.yaes.Cats
      * import scala.concurrent.duration._
      *
      * val catsIO: CatsIO[Int] = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)
      *
      * val result = YaesIO.run {
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
      *   A YAES IO program with timeout protection
      */
    @targetName("valueWithTimeoutExtension")
    def value(timeout: Duration): (in.rcard.yaes.IO, Raise[Throwable]) ?=> A =
      valueImpl(catsIO, timeout)
}
