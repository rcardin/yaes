package in.rcard.yaes.http.server.parsing

import in.rcard.yaes.http.server.Response
import java.io.OutputStream

/** HTTP response writer.
  *
  * Provides functions to write HTTP/1.1 responses to an OutputStream.
  *
  * Example:
  * {{{
  * val response = Response(200, body = "Hello")
  * val outputStream = socket.getOutputStream
  * HttpWriter.writeResponse(outputStream, response)
  * }}}
  */
object HttpWriter {

  /** Maps HTTP status codes to their standard reason phrases.
    *
    * Unknown status codes return an empty string.
    *
    * @param status
    *   HTTP status code
    * @return
    *   Reason phrase for the status code, or empty string if unknown
    */
  def reasonPhrase(status: Int): String = status match {
    case 200 => "OK"
    case 201 => "Created"
    case 202 => "Accepted"
    case 204 => "No Content"
    case 400 => "Bad Request"
    case 404 => "Not Found"
    case 405 => "Method Not Allowed"
    case 413 => "Payload Too Large"
    case 500 => "Internal Server Error"
    case 501 => "Not Implemented"
    case 503 => "Service Unavailable"
    case 505 => "HTTP Version Not Supported"
    case _   => ""
  }

  /** Writes an HTTP response to the given output stream.
    *
    * Formats the response according to HTTP/1.1 specification:
    * - Status line: HTTP/1.1 {status} {reason}
    * - Headers (including Content-Length)
    * - Blank line
    * - Body
    *
    * Content-Length is automatically computed from the body's UTF-8 byte length.
    *
    * Example:
    * {{{
    * val response = Response(
    *   status = 200,
    *   headers = Map("Content-Type" -> "text/plain"),
    *   body = "Hello, World!"
    * )
    * HttpWriter.writeResponse(outputStream, response)
    * }}}
    *
    * @param outputStream
    *   The stream to write the response to
    * @param response
    *   The response to write
    */
  def writeResponse(outputStream: OutputStream, response: Response): Unit = {
    val bodyBytes = response.body.getBytes("UTF-8")
    val contentLength = bodyBytes.length

    // Write status line
    val statusLine = s"HTTP/1.1 ${response.status} ${reasonPhrase(response.status)}\r\n"
    outputStream.write(statusLine.getBytes("UTF-8"))

    // Write custom headers
    response.headers.foreach { case (name, value) =>
      val headerLine = s"$name: $value\r\n"
      outputStream.write(headerLine.getBytes("UTF-8"))
    }

    // Write Content-Length header
    val contentLengthLine = s"Content-Length: $contentLength\r\n"
    outputStream.write(contentLengthLine.getBytes("UTF-8"))

    // Write blank line to end headers
    outputStream.write("\r\n".getBytes("UTF-8"))

    // Write body
    outputStream.write(bodyBytes)

    // Flush to ensure all data is sent
    outputStream.flush()
  }
}
