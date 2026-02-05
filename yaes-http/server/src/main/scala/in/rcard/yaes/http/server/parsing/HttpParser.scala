package in.rcard.yaes.http.server.parsing

import in.rcard.yaes.*
import in.rcard.yaes.http.server.{Request, ServerConfig, Method}
import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import scala.collection.mutable

/** HTTP/1.x request parser for YaesServer.
  *
  * Parses HTTP requests according to the HTTP/1.1 specification with configurable limits.
  */
object HttpParser {

  /** Supported HTTP methods */
  private val SupportedMethods = Set("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

  /** Parses the HTTP request line.
    *
    * Format: `METHOD /path HTTP/version`
    *
    * @param line the request line to parse
    * @return a tuple of (method, path, version), or raises HttpParseError
    */
  def parseRequestLine(line: String): (String, String, String) raises HttpParseError = {
    val parts = line.split(" ", 3)

    if (parts.length != 3) {
      Raise.raise(HttpParseError.MalformedRequestLine)
    }

    val method = parts(0)
    val path = parts(1)
    val version = parts(2)

    // Check if method is supported
    if (!SupportedMethods.contains(method)) {
      Raise.raise(HttpParseError.UnsupportedMethod(method))
    }

    // Check if HTTP version is supported (only HTTP/1.0 and HTTP/1.1)
    if (version != "HTTP/1.1" && version != "HTTP/1.0") {
      Raise.raise(HttpParseError.UnsupportedHttpVersion(version))
    }

    (method, path, version)
  }

  /** Parses HTTP headers.
    *
    * Format: `Header-Name: Header-Value` (one per line)
    *
    * @param headerLines the list of header lines to parse
    * @param maxHeaderSize the maximum total size of headers in bytes
    * @return a map of header names to values, or raises HttpParseError
    */
  def parseHeaders(headerLines: List[String], maxHeaderSize: Int): Map[String, String] raises HttpParseError = {
    // Calculate total size: each line + \r\n
    val totalSize = headerLines.map(_.length + 2).sum

    if (totalSize > maxHeaderSize) {
      Raise.raise(HttpParseError.MalformedHeaders)
    }

    val headers = scala.collection.mutable.Map[String, String]()

    for (line <- headerLines) {
      val colonIndex = line.indexOf(':')

      if (colonIndex == -1) {
        Raise.raise(HttpParseError.MalformedHeaders)
      }

      val name = line.substring(0, colonIndex)
      val value = if (colonIndex + 1 < line.length) {
        val afterColon = line.substring(colonIndex + 1)
        // Trim leading space if present
        if (afterColon.startsWith(" ")) afterColon.substring(1) else afterColon
      } else {
        ""
      }

      // HTTP/1.1 header names are case-insensitive (RFC 7230 Section 3.2)
      headers(name.toLowerCase) = value
    }

    headers.toMap
  }

  /** Parses the request body based on Content-Length header.
    *
    * Reads the body from the input stream if Content-Length is present and valid.
    * Always uses UTF-8 encoding.
    *
    * @param inputStream the input stream to read from
    * @param headers the parsed HTTP headers
    * @param maxBodySize the maximum allowed body size in bytes
    * @return the body string, or raises HttpParseError
    */
  def parseBody(inputStream: InputStream, headers: Map[String, String], maxBodySize: Int): String raises HttpParseError = {
    headers.get("content-length") match {
      case None =>
        // No Content-Length header = empty body
        ""

      case Some(lengthStr) =>
        // Try to parse Content-Length as an integer
        val contentLength = try {
          lengthStr.toInt
        } catch {
          case _: NumberFormatException =>
            Raise.raise(HttpParseError.InvalidContentLength)
        }

        // Check for negative Content-Length
        if (contentLength < 0) {
          Raise.raise(HttpParseError.InvalidContentLength)
        }

        // Check if body size exceeds max
        if (contentLength > maxBodySize) {
          Raise.raise(HttpParseError.PayloadTooLarge(contentLength, maxBodySize))
        }

        // Content-Length of 0 means empty body
        if (contentLength == 0) {
          return ""
        }

        // Read the body
        val buffer = new Array[Byte](contentLength)
        var totalRead = 0

        while (totalRead < contentLength) {
          val bytesRead = inputStream.read(buffer, totalRead, contentLength - totalRead)
          if (bytesRead == -1) {
            // Unexpected end of stream
            Raise.raise(HttpParseError.UnexpectedEndOfStream)
          }
          totalRead += bytesRead
        }

        // Convert to string using UTF-8
        new String(buffer, "UTF-8")
    }
  }

  /** Parses a complete HTTP request from an input stream.
    *
    * Reads and parses the request line, headers, and body according to HTTP/1.1 specification.
    * Uses the provided configuration for size limits.
    *
    * @param inputStream the input stream to read from
    * @param config the server configuration with size limits
    * @return a complete Request, or raises HttpParseError
    */
  def parseRequest(inputStream: InputStream, config: ServerConfig): Request raises HttpParseError = {
    val reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))

    // Read request line
    val requestLine = reader.readLine()
    if (requestLine == null) {
      Raise.raise(HttpParseError.MalformedRequestLine)
    }

    // Parse request line
    val (methodStr, pathWithQuery, _) = parseRequestLine(requestLine)

    // Split path and query string
    val (path, queryString) = parsePathAndQuery(pathWithQuery)

    // Convert method string to Method enum
    val method = Method.valueOf(methodStr)

    // Read header lines until blank line
    val headerLines = mutable.ListBuffer[String]()
    var line = reader.readLine()
    while (line != null && line.nonEmpty) {
      headerLines += line
      line = reader.readLine()
    }

    // Parse headers
    val headers = parseHeaders(headerLines.toList, config.maxHeaderSize)

    // Parse body from reader instead of raw inputStream
    val body = parseBodyFromReader(reader, headers, config.maxBodySize)

    Request(
      method = method,
      path = path,
      headers = headers,
      body = body,
      queryString = queryString
    )
  }

  /** Parses the request body from a BufferedReader based on Content-Length header.
    *
    * Similar to parseBody but works with a BufferedReader that has already consumed
    * the request line and headers.
    *
    * @param reader the BufferedReader positioned after headers
    * @param headers the parsed HTTP headers
    * @param maxBodySize the maximum allowed body size in bytes
    * @return the body string, or raises HttpParseError
    */
  private def parseBodyFromReader(reader: BufferedReader, headers: Map[String, String], maxBodySize: Int): String raises HttpParseError = {
    headers.get("content-length") match {
      case None =>
        // No Content-Length header = empty body
        ""

      case Some(lengthStr) =>
        // Try to parse Content-Length as an integer
        val contentLength = try {
          lengthStr.toInt
        } catch {
          case _: NumberFormatException =>
            Raise.raise(HttpParseError.InvalidContentLength)
        }

        // Check for negative Content-Length
        if (contentLength < 0) {
          Raise.raise(HttpParseError.InvalidContentLength)
        }

        // Check if body size exceeds max
        if (contentLength > maxBodySize) {
          Raise.raise(HttpParseError.PayloadTooLarge(contentLength, maxBodySize))
        }

        // Content-Length of 0 means empty body
        if (contentLength == 0) {
          return ""
        }

        // Read the body using the reader
        val buffer = new Array[Char](contentLength)
        var totalRead = 0

        while (totalRead < contentLength) {
          val charsRead = reader.read(buffer, totalRead, contentLength - totalRead)
          if (charsRead == -1) {
            // Unexpected end of stream
            Raise.raise(HttpParseError.UnexpectedEndOfStream)
          }
          totalRead += charsRead
        }

        // Convert to string (already decoded as UTF-8 by InputStreamReader)
        new String(buffer)
    }
  }

  /** Splits a path with query string into path and parsed query parameters.
    *
    * URL-decodes the path according to RFC 3986 and validates against path traversal attempts.
    *
    * Example: "/search?q=scala&lang=en" -> ("/search", Map("q" -> List("scala"), "lang" -> List("en")))
    *
    * @param pathWithQuery the path potentially containing a query string
    * @return tuple of (decoded path, queryString map), or raises HttpParseError
    */
  private def parsePathAndQuery(pathWithQuery: String): (String, Map[String, List[String]]) raises HttpParseError = {
    val queryIndex = pathWithQuery.indexOf('?')
    val rawPath = if (queryIndex == -1) {
      pathWithQuery
    } else {
      pathWithQuery.substring(0, queryIndex)
    }

    // Decode and validate the path
    val decodedPath = decodeAndValidatePath(rawPath)

    val queryString = if (queryIndex == -1) {
      Map.empty
    } else {
      val queryPart = pathWithQuery.substring(queryIndex + 1)
      parseQueryString(queryPart)
    }

    (decodedPath, queryString)
  }

  /** Decodes a URL-encoded path and validates it for security.
    *
    * Performs the following:
    * - URL-decodes each path segment
    * - Rejects path traversal attempts (.. segments)
    * - Raises MalformedPath on invalid encoding
    *
    * @param path the raw path to decode
    * @return the decoded path, or raises HttpParseError.MalformedPath
    */
  private def decodeAndValidatePath(path: String): String raises HttpParseError = {
    if (path.isEmpty) {
      return path
    }

    try {
      // Split into segments, preserving leading slash
      val startsWithSlash = path.startsWith("/")
      val segments = path.split("/").filter(_.nonEmpty)

      // Decode each segment and check for path traversal
      val decodedSegments = segments.map { segment =>
        val decoded = URLDecoder.decode(segment, StandardCharsets.UTF_8)

        // Reject path traversal attempts
        if (decoded == ".." || decoded.contains("..")) {
          Raise.raise(HttpParseError.MalformedPath)
        }

        decoded
      }

      // Reconstruct path with leading slash if present
      val result = decodedSegments.mkString("/")
      if (startsWithSlash) s"/$result" else result

    } catch {
      case _: IllegalArgumentException =>
        // Invalid percent-encoding
        Raise.raise(HttpParseError.MalformedPath)
    }
  }

  /** Parses a query string into a map of parameter names to lists of values.
    *
    * URL-decodes both parameter names and values according to RFC 3986.
    * Handles both percent-encoding (%20) and plus-encoding (+) for spaces.
    *
    * Example: "q=scala%20lang&tag=fp&tag=java" -> Map("q" -> List("scala lang"), "tag" -> List("fp", "java"))
    *
    * @param query the query string without the leading '?'
    * @return map of parameter names to lists of values, or raises HttpParseError.MalformedQueryString
    */
  private def parseQueryString(query: String): Map[String, List[String]] raises HttpParseError = {
    if (query.isEmpty) {
      return Map.empty
    }

    val params = mutable.Map[String, mutable.ListBuffer[String]]()

    for (pair <- query.split("&")) {
      val eqIndex = pair.indexOf('=')
      if (eqIndex != -1) {
        val rawName = pair.substring(0, eqIndex)
        val rawValue = pair.substring(eqIndex + 1)

        // Decode both name and value, raising error if decoding fails
        val name = try {
          URLDecoder.decode(rawName, StandardCharsets.UTF_8)
        } catch {
          case _: IllegalArgumentException =>
            Raise.raise(HttpParseError.MalformedQueryString)
        }

        val value = try {
          URLDecoder.decode(rawValue, StandardCharsets.UTF_8)
        } catch {
          case _: IllegalArgumentException =>
            Raise.raise(HttpParseError.MalformedQueryString)
        }

        if (!params.contains(name)) {
          params(name) = mutable.ListBuffer[String]()
        }
        params(name) += value
      }
    }

    params.view.mapValues(_.toList).toMap
  }
}
