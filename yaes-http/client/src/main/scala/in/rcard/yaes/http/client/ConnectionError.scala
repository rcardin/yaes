package in.rcard.yaes.http.client

/** Transport-level errors that occur before an HTTP response is received.
  *
  * These errors represent failures in the network layer, such as the server being unreachable,
  * connection timeouts, or malformed URLs. They are raised as typed errors via the
  * [[in.rcard.yaes.Raise]] effect in [[YaesClient.send]].
  *
  * Example:
  * {{{
  * val result = Raise.either[ConnectionError, HttpResponse] {
  *   client.send(HttpRequest.get(uri))
  * }
  * result match
  *   case Left(ConnectionError.ConnectionRefused(host, port)) => // handle
  *   case Left(ConnectionError.ConnectTimeout(host))          => // handle
  *   case Right(response)                                     => // success
  * }}}
  */
sealed trait ConnectionError

object ConnectionError:
  /** The target host refused the TCP connection.
    *
    * @param host the hostname that refused the connection
    * @param port the port that was targeted
    */
  case class ConnectionRefused(host: String, port: Int) extends ConnectionError

  /** The TCP connection could not be established within the configured timeout.
    *
    * @param host the hostname that timed out
    */
  case class ConnectTimeout(host: String) extends ConnectionError

  /** The server accepted the connection but did not respond within the per-request timeout.
    *
    * @param url the full URL of the request that timed out
    */
  case class RequestTimeout(url: String) extends ConnectionError

  /** An unexpected exception occurred during the HTTP exchange.
    *
    * @param cause the underlying exception
    */
  case class Unexpected(cause: Throwable) extends ConnectionError
