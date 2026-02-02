package in.rcard.yaes.http.server

import in.rcard.yaes.Async.Deadline

import scala.concurrent.duration.*

/** Configuration for the YAES HTTP server.
  *
  * @param port
  *   The port number to listen on (default: 8080)
  * @param deadline
  *   The graceful shutdown deadline (default: 30 seconds)
  * @param maxBodySize
  *   Maximum request body size in bytes (default: 1 MB)
  * @param maxHeaderSize
  *   Maximum total header size in bytes (default: 16 KB)
  */
case class ServerConfig(
    port: Int = 8080,
    deadline: Deadline = Deadline.after(30.seconds),
    maxBodySize: Int = 1048576,    // 1 MB
    maxHeaderSize: Int = 16384      // 16 KB
)

/** Extension methods for size DSL.
  *
  * Allows writing readable size specifications:
  * {{{
  * val config = ServerConfig(
  *   maxBodySize = 5.megabytes,
  *   maxHeaderSize = 32.kilobytes
  * )
  * }}}
  */
extension (n: Int)
  /** Returns the value in bytes (identity function). */
  def bytes: Int = n

  /** Converts kilobytes to bytes. */
  def kilobytes: Int = n * 1024

  /** Converts megabytes to bytes. */
  def megabytes: Int = n * 1024 * 1024
