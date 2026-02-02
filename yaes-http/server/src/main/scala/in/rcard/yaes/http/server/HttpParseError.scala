package in.rcard.yaes.http.server

/** Represents errors that occur during HTTP request parsing.
  *
  * HttpParseError is a sealed trait representing parsing errors as values (not exceptions), designed
  * to work with the YAES `Raise[E]` effect for typed error handling.
  *
  * Each error type can be converted to an appropriate HTTP Response via the `toResponse` method.
  */
sealed trait HttpParseError {
  def message: String
  def toResponse: Response
}

object HttpParseError {
  /** Error indicating the HTTP request line is malformed.
    *
    * The request line must follow the format: METHOD /path HTTP/version
    * This error occurs when the request line doesn't have exactly 3 parts.
    *
    * HTTP Status: 400 Bad Request
    */
  case object MalformedRequestLine extends HttpParseError {
    def message: String = "Bad Request"
    def toResponse: Response = Response(400, body = message)
  }

  /** Error indicating the HTTP method is not supported.
    *
    * Supported methods are: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
    *
    * HTTP Status: 501 Not Implemented
    *
    * @param method
    *   The unsupported HTTP method
    */
  case class UnsupportedMethod(method: String) extends HttpParseError {
    def message: String = "Not Implemented"
    def toResponse: Response = Response(501, body = message)
  }

  /** Error indicating the HTTP version is not supported.
    *
    * Only HTTP/1.0 and HTTP/1.1 are supported.
    *
    * HTTP Status: 505 HTTP Version Not Supported
    *
    * @param version
    *   The unsupported HTTP version
    */
  case class UnsupportedHttpVersion(version: String) extends HttpParseError {
    def message: String = "HTTP Version Not Supported"
    def toResponse: Response = Response(505, body = message)
  }

  /** Error indicating HTTP headers are malformed.
    *
    * Headers must follow the format: Header-Name: Header-Value
    * This error occurs when headers exceed size limits or don't contain a colon separator.
    *
    * HTTP Status: 400 Bad Request
    */
  case object MalformedHeaders extends HttpParseError {
    def message: String = "Bad Request"
    def toResponse: Response = Response(400, body = message)
  }

  /** Error indicating the Content-Length header has an invalid value.
    *
    * Content-Length must be a non-negative integer.
    *
    * HTTP Status: 400 Bad Request
    */
  case object InvalidContentLength extends HttpParseError {
    def message: String = "Bad Request"
    def toResponse: Response = Response(400, body = message)
  }

  /** Error indicating the request body exceeds the maximum allowed size.
    *
    * HTTP Status: 413 Payload Too Large
    *
    * @param contentLength
    *   The actual Content-Length value from the request
    * @param maxBodySize
    *   The maximum allowed body size in bytes
    */
  case class PayloadTooLarge(contentLength: Int, maxBodySize: Int) extends HttpParseError {
    def message: String = "Payload Too Large"
    def toResponse: Response = Response(413, body = message)
  }

  /** Error indicating an unexpected end of stream while reading the request body.
    *
    * This occurs when the connection is closed before all expected bytes (per Content-Length) are
    * received.
    *
    * HTTP Status: 400 Bad Request
    */
  case object UnexpectedEndOfStream extends HttpParseError {
    def message: String = "Bad Request"
    def toResponse: Response = Response(400, body = message)
  }
}
