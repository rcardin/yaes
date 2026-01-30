package in.rcard.yaes.interop

import in.rcard.yaes.{Sync => YaesSync, Executor}
import in.rcard.yaes.Sync as SyncObj
import in.rcard.yaes.Raise
import cats.effect.{IO => CatsIO}
import cats.effect.{Sync => CatsSync}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.util.Try

/** Bidirectional conversion between YAES Sync and Cats Effect IO.
  *
  * This object provides methods to convert between YAES's context function-based Sync
  * effect and Cats Effect's monadic IO.
  *
  * For extension methods on Cats Effect IO, import the syntax:
  * {{{
  * import in.rcard.yaes.syntax.catseffect.given
  * }}}
  */
object catseffect {

  /** A synchronous executor that runs tasks on the current thread.
    * Used internally to provide a sync-compatible execution model.
    */
  private class SyncExecutor extends Executor {
    override def submit[A](task: => A): Future[A] = Future.fromTry(Try(task))
  }

  /** Synchronous unsafe implementation that runs on the current thread. */
  private object syncUnsafe extends SyncObj.Unsafe {
    override val executor: Executor = new SyncExecutor()
  }

  /** Converts a YAES Sync program to a Cats Effect type.
    *
    * This conversion runs the YAES program using `CatsSync[F].blocking`, which shifts execution
    * to Cats Effect's blocking thread pool. This is appropriate since YAES Sync programs
    * typically perform side effects that may include blocking I/O operations.
    *
    * The yaesProgram can use `Raise[Throwable]` to handle exceptions in a typed way before
    * conversion. Any errors raised via `Raise[Throwable]` will be converted to thrown exceptions
    * and propagated through the effect `F`, maintaining semantic equivalence with
    * exception-based error handling.
    *
    * Note: This method uses `CatsSync[F].blocking` rather than `CatsSync[F].delay` to avoid blocking
    * Cats Effect's compute thread pool. For CPU-bound computations that don't perform blocking
    * I/O, consider using [[delay]] instead for better performance.
    *
    * Example with error handling:
    * {{{
    * import in.rcard.yaes.{Sync => YaesSync, Raise}
    * import in.rcard.yaes.interop.catseffect
    * import cats.effect.IO
    *
    * val yaesProgram: (YaesSync, Raise[Throwable]) ?=> Int = YaesSync {
    *   Raise.catching {
    *     println("Hello from YAES")
    *     riskyOperation()  // Might throw
    *   } { ex => ex }
    * }
    *
    * val catsIO: IO[Int] = catseffect.blocking(yaesProgram)
    * // Exceptions are propagated through Cats Effect IO
    * }}}
    *
    * Example without explicit error handling:
    * {{{
    * val yaesProgram: (YaesSync, Raise[Throwable]) ?=> Int = YaesSync {
    *   42
    * }
    *
    * val catsIO: IO[Int] = catseffect.blocking(yaesProgram)
    * val result = catsIO.unsafeRunSync()  // 42
    * }}}
    *
    * @param yaesProgram
    *   The YAES Sync program to convert (can use Raise[Throwable] for typed error handling)
    * @tparam F
    *   The target effect type (must have a Sync instance)
    * @tparam A
    *   The result type of the program
    * @return
    *   An effect `F[A]` containing the same computation with errors as failures
    * @see [[delay]] for CPU-bound computations without blocking I/O
    */
  def blocking[F[_]: CatsSync, A](yaesProgram: (in.rcard.yaes.Sync, Raise[Throwable]) ?=> A): F[A] = {
    CatsSync[F].blocking {
      yaesProgram(using new in.rcard.yaes.Yaes(syncUnsafe), Raise.rethrowError)
    }
  }

  /** Converts a YAES Sync program to a Cats Effect type, optimized for CPU-bound computations.
    *
    * This conversion runs the YAES program using `CatsSync[F].delay`, which executes on Cats Effect's
    * compute thread pool. Use this variant only when you know the YAES program does NOT perform
    * blocking I/O operations (e.g., file reads, network calls, `Thread.sleep`).
    *
    * For programs that may perform blocking I/O, use [[blocking]] instead, which properly shifts
    * execution to the blocking thread pool.
    *
    * @param yaesProgram
    *   The YAES Sync program to convert (must be CPU-bound, non-blocking)
    * @tparam F
    *   The target effect type (must have a Sync instance)
    * @tparam A
    *   The result type of the program
    * @return
    *   An effect `F[A]` containing the same computation
    * @see [[blocking]] for programs that may perform blocking I/O
    */
  def delay[F[_]: CatsSync, A](yaesProgram: (in.rcard.yaes.Sync, Raise[Throwable]) ?=> A): F[A] = {
    CatsSync[F].delay {
      yaesProgram(using new in.rcard.yaes.Yaes(syncUnsafe), Raise.rethrowError)
    }
  }

  /** Converts a YAES Sync program to Cats Effect IO.
    *
    * This is a convenience method that calls [[blocking]] with `CatsIO` as the target effect type.
    * Use this when you specifically need a `CatsIO` result and don't need polymorphism.
    *
    * @param yaesProgram
    *   The YAES Sync program to convert
    * @tparam A
    *   The result type of the program
    * @return
    *   A Cats Effect IO containing the same computation
    */
  def blockingSync[A](yaesProgram: (in.rcard.yaes.Sync, Raise[Throwable]) ?=> A): CatsIO[A] =
    blocking[CatsIO, A](yaesProgram)

  /** Converts a YAES Sync program to Cats Effect IO, optimized for CPU-bound computations.
    *
    * This is a convenience method that calls [[delay]] with `CatsIO` as the target effect type.
    * Use this when you specifically need a `CatsIO` result for CPU-bound, non-blocking computations.
    *
    * @param yaesProgram
    *   The YAES Sync program to convert (must be CPU-bound, non-blocking)
    * @tparam A
    *   The result type of the program
    * @return
    *   A Cats Effect IO containing the same computation
    * @see [[blockingSync]] for programs that may perform blocking I/O
    */
  def delaySync[A](yaesProgram: (in.rcard.yaes.Sync, Raise[Throwable]) ?=> A): CatsIO[A] =
    delay[CatsIO, A](yaesProgram)

  /** Converts a Cats Effect IO to a YAES Sync program.
    *
    * This conversion executes the Cats Effect IO within a YAES Sync context. The Cats IO is
    * converted to a Future using `unsafeToFuture`, which is then blocked on within the YAES Sync
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
    * import in.rcard.yaes.{Sync => YaesSync, Raise}
    * import in.rcard.yaes.interop.catseffect
    *
    * val catsIO: CatsIO[Int] = CatsIO.raiseError(new RuntimeException("Error"))
    *
    * val result = YaesSync.run {
    *   Raise.either {
    *     catseffect.value(catsIO)
    *   } match {
    *     case Right(value) => println(s"Success: $value")
    *     case Left(error) => println(s"Error: ${error.getMessage}")
    *   }
    * }
    * }}}
    *
    * Example with Raise.fold:
    * {{{
    * val result = YaesSync.run {
    *   Raise.fold(
    *     catseffect.value(catsIO)
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
    *   A YAES Sync program that executes the Cats Effect computation and raises exceptions via Raise[Throwable]
    */
  def value[A](catsIO: CatsIO[A]): (in.rcard.yaes.Sync, Raise[Throwable]) ?=> A = {
    valueImpl(catsIO, Duration.Inf)
  }

  /** Converts a Cats Effect IO to a YAES Sync program with a timeout.
    *
    * This conversion executes the Cats Effect IO within a YAES Sync context with timeout protection.
    * The Cats IO is converted to a Future using `unsafeToFuture`, which is then blocked on within
    * the YAES Sync effect. This method uses the Cats Effect global runtime for execution.
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
    * import in.rcard.yaes.{Sync => YaesSync, Raise}
    * import in.rcard.yaes.interop.catseffect
    * import scala.concurrent.duration._
    *
    * val catsIO: CatsIO[Int] = CatsIO.sleep(10.seconds) *> CatsIO.pure(42)
    *
    * val result = YaesSync.run {
    *   Raise.fold(
    *     catseffect.value(catsIO, 5.seconds)
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
    *   A YAES Sync program with timeout protection that raises exceptions via Raise[Throwable]
    */
  def value[A](catsIO: CatsIO[A], timeout: Duration): (in.rcard.yaes.Sync, Raise[Throwable]) ?=> A = {
    valueImpl(catsIO, timeout)
  }

  /** Internal implementation for converting Cats Effect IO to YAES Sync.
    *
    * @param catsIO
    *   The Cats Effect IO to convert
    * @param timeout
    *   Maximum time to wait for completion
    * @tparam A
    *   The result type
    * @return
    *   A YAES Sync program that executes the Cats Effect computation
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
