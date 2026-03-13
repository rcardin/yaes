package in.rcard.yaes.http.client

/** HTTP protocol version for the client connection.
  *
  * @see [[YaesClientConfig]]
  */
enum HttpVersion:
  /** HTTP/1.1 */
  case Http11
  /** HTTP/2 */
  case Http2
