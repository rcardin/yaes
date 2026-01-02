package in.rcard.yaes.http.server

import in.rcard.yaes.{Async, Channel, Raise}

/** A running HTTP server instance.
  *
  * Represents an active server that can be programmatically stopped. Obtain via [[YaesServer.run]].
  *
  * Example:
  * {{{
  * Async.run {
  *   IO.run {
  *     val server = YaesServer.route(...)
  *       .onShutdown(() => println("Goodbye!"))
  *       .run(port = 8080)
  *
  *     // Server is running...
  *
  *     server.shutdown()  // Stop when ready
  *     // Server is now stopped and cleaned up
  *   }
  * }
  * }}}
  */
class Server private[server] (
  private val shutdownChannel: Channel[Unit]
) {

  /** Request the server to shut down.
    *
    * Sends a shutdown signal to the server. The server will:
    * 1. Reject new requests with 503 Service Unavailable
    * 2. Wait for all in-flight requests to complete (graceful shutdown)
    * 3. Run the shutdown hook (if registered)
    * 4. Clean up resources
    *
    * **Graceful Shutdown Guarantee:**
    * The shutdown process waits for all active request handlers to complete
    * before proceeding with cleanup. This is enforced by YAES structured concurrency:
    * the [[in.rcard.yaes.Async.run]] handler's `StructuredTaskScope.join()` waits for all forked
    * fibers (including all request handlers) to finish.
    *
    * **Observability:**
    * During shutdown, the server logs:
    * - Initial active request count when shutdown begins
    * - Periodic updates as requests complete (if any are in-flight)
    * - Confirmation when all requests have finished
    *
    * **Idempotency:**
    * This method is idempotent - calling it multiple times is safe. If the server is already
    * shutting down or has been shut down, subsequent calls will print a message and return
    * immediately.
    *
    * @param async Async context for sending shutdown signal
    */
  def shutdown()(using Async): Unit = {
    Raise.fold(
      shutdownChannel.send(())
    )(
      onError = (error: Channel.ChannelClosed.type) =>
        println("Server is already shutting down or has been shut down")
    )(
      onSuccess = (_: Unit) => ()
    )
  }
}
