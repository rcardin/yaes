package in.rcard.yaes

import in.rcard.yaes.{IO => YaesIO}
import in.rcard.yaes.Raise
import cats.effect.{IO => CatsIO}
import cats.effect.Sync
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration.Duration
import scala.annotation.targetName
import scala.util.Try

/** Conversion utilities between YAES IO and Cats Effect IO.
  *
  * This object provides extension methods to convert between YAES's context function-based IO
  * effect and Cats Effect's monadic IO.
  */
object Cats {

  /** A synchronous executor that runs tasks on the current thread.
    * Used internally to provide a sync-compatible execution model.
    */
  private class SyncExecutor extends Executor {
    override def submit[A](task: => A): Future[A] = Future.fromTry(Try(task))
  }

  /** Synchronous unsafe implementation that runs on the current thread. */
  private object syncUnsafe extends IO.Unsafe {
    override val executor: Executor = new SyncExecutor()
  }

  /** Converts a YAES IO program to a Cats Effect type.
    *
    * This conversion runs the YAES program using `Sync[F].blocking`, which shifts execution
    * to Cats Effect's blocking thread pool. This is appropriate since YAES IO programs
    * typically perform side effects that may include blocking I/O operations.
    *
    * The yaesProgram can use `Raise[Throwable]` to handle exceptions in a typed way before 
    * conversion. Any errors raised via `Raise[Throwable]` will be converted to thrown exceptions
    * and propagated through the effect `F`, maintaining semantic equivalence with
    * exception-based error handling.
    *
    * Note: This method uses `Sync[F].blocking` rather than `Sync[F].delay` to avoid blocking
    * Cats Effect's compute thread pool. For CPU-bound computations that don't perform blocking
    * I/O, consider using [[delay]] instead for better performance.
    *
    * Example with error handling:
    * {{{
    * import in.rcard.yaes.{IO => YaesIO, Raise}
    * import in.rcard.yaes.Cats
    * import cats.effect.IO
    *
    * val yaesProgram: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
    *   Raise.catching {
    *     println("Hello from YAES")
    *     riskyOperation()  // Might throw
    *   } { ex => ex }
    * }
    *
    * val catsIO: IO[Int] = Cats.blocking(yaesProgram)
    * // Exceptions are propagated through Cats Effect IO
    * }}}
    *
    * Example without explicit error handling:
    * {{{
    * val yaesProgram: (YaesIO, Raise[Throwable]) ?=> Int = YaesIO {
    *   42
    * }
    *
    * val catsIO: IO[Int] = Cats.blocking(yaesProgram)
    * val result = catsIO.unsafeRunSync()  // 42
    * }}}
    *
    * @param yaesProgram
    *   The YAES IO program to convert (can use Raise[Throwable] for typed error handling)
    * @tparam F
    *   The target effect type (must have a Sync instance)
    * @tparam A
    *   The result type of the program
    * @return
    *   An effect `F[A]` containing the same computation with errors as failures
    * @see [[delay]] for CPU-bound computations without blocking I/O
    */
  def blocking[F[_]: Sync, A](yaesProgram: (in.rcard.yaes.IO, Raise[Throwable]) ?=> A): F[A] = {
    Sync[F].blocking {
      given Raise[Throwable] = new in.rcard.yaes.Yaes(new Raise.Unsafe[Throwable] {
        def raise(error: => Throwable): Nothing = throw error
      })
      given in.rcard.yaes.IO = new in.rcard.yaes.Yaes(syncUnsafe)
      yaesProgram
    }
  }

  /** Converts a YAES IO program to a Cats Effect type, optimized for CPU-bound computations.
    *
    * This conversion runs the YAES program using `Sync[F].delay`, which executes on Cats Effect's
    * compute thread pool. Use this variant only when you know the YAES program does NOT perform
    * blocking I/O operations (e.g., file reads, network calls, `Thread.sleep`).
    *
    * For programs that may perform blocking I/O, use [[blocking]] instead, which properly shifts
    * execution to the blocking thread pool.
    *
    * @param yaesProgram
    *   The YAES IO program to convert (must be CPU-bound, non-blocking)
    * @tparam F
    *   The target effect type (must have a Sync instance)
    * @tparam A
    *   The result type of the program
    * @return
    *   An effect `F[A]` containing the same computation
    * @see [[blocking]] for programs that may perform blocking I/O
    */
  def delay[F[_]: Sync, A](yaesProgram: (in.rcard.yaes.IO, Raise[Throwable]) ?=> A): F[A] = {
    Sync[F].delay {
      given Raise[Throwable] = new in.rcard.yaes.Yaes(new Raise.Unsafe[Throwable] {
        def raise(error: => Throwable): Nothing = throw error
      })
      given in.rcard.yaes.IO = new in.rcard.yaes.Yaes(syncUnsafe)
      yaesProgram
    }
  }

  /** Converts a YAES IO program to Cats Effect IO.
    *
    * This is a convenience method that calls [[blocking]] with `CatsIO` as the target effect type.
    * Use this when you specifically need a `CatsIO` result and don't need polymorphism.
    *
    * @param yaesProgram
    *   The YAES IO program to convert
    * @tparam A
    *   The result type of the program
    * @return
    *   A Cats Effect IO containing the same computation
    */
  def blockingIO[A](yaesProgram: (in.rcard.yaes.IO, Raise[Throwable]) ?=> A): CatsIO[A] =
    blocking[CatsIO, A](yaesProgram)

  /** Converts a YAES IO program to Cats Effect IO, optimized for CPU-bound computations.
    *
    * This is a convenience method that calls [[delay]] with `CatsIO` as the target effect type.
    * Use this when you specifically need a `CatsIO` result for CPU-bound, non-blocking computations.
    *
    * @param yaesProgram
    *   The YAES IO program to convert (must be CPU-bound, non-blocking)
    * @tparam A
    *   The result type of the program
    * @return
    *   A Cats Effect IO containing the same computation
    * @see [[blockingIO]] for programs that may perform blocking I/O
    */
  def delayIO[A](yaesProgram: (in.rcard.yaes.IO, Raise[Throwable]) ?=> A): CatsIO[A] =
    delay[CatsIO, A](yaesProgram)

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
