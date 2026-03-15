package in.rcard.yaes.http.client

import java.net.http.{HttpClient => JHttpClient}

/** HTTP protocol version for the client connection.
  *
  * @see [[YaesClientConfig]]
  */
enum HttpVersion:
  /** HTTP/1.1 */
  case Http11
  /** HTTP/2 */
  case Http2

  /** Converts to the corresponding [[java.net.http.HttpClient.Version]] constant. */
  def toJava: JHttpClient.Version = this match
    case Http11 => JHttpClient.Version.HTTP_1_1
    case Http2  => JHttpClient.Version.HTTP_2
