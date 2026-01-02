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
    * Sends a shutdown signal to the server. The server will stop accepting new connections, run
    * the shutdown hook (if registered), and clean up resources.
    *
    * YaesServer.run will return after shutdown completes.
    *
    * This method is idempotent - calling it multiple times is safe. If the server is already
    * shutting down or has been shut down, subsequent calls will print a message and return
    * immediately.
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
