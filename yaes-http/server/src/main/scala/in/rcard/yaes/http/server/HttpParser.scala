package in.rcard.yaes.http.server

import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader
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
    * @return Either an error Response (400, 501, 505) or a tuple of (method, path, version)
    */
  def parseRequestLine(line: String): Either[Response, (String, String, String)] = {
    val parts = line.split(" ", 3)

    if (parts.length != 3) {
      return Left(Response(400, body = "Bad Request"))
    }

    val method = parts(0)
    val path = parts(1)
    val version = parts(2)

    // Check if method is supported
    if (!SupportedMethods.contains(method)) {
      return Left(Response(501, body = "Not Implemented"))
    }

    // Check if HTTP version is supported (only HTTP/1.0 and HTTP/1.1)
    if (version != "HTTP/1.1" && version != "HTTP/1.0") {
      return Left(Response(505, body = "HTTP Version Not Supported"))
    }

    Right((method, path, version))
  }

  /** Parses HTTP headers.
    *
    * Format: `Header-Name: Header-Value` (one per line)
    *
    * @param headerLines the list of header lines to parse
    * @param maxHeaderSize the maximum total size of headers in bytes
    * @return Either an error Response (400) or a map of header names to values
    */
  def parseHeaders(headerLines: List[String], maxHeaderSize: Int): Either[Response, Map[String, String]] = {
    // Calculate total size: each line + \r\n
    val totalSize = headerLines.map(_.length + 2).sum

    if (totalSize > maxHeaderSize) {
      return Left(Response(400, body = "Bad Request"))
    }

    val headers = scala.collection.mutable.Map[String, String]()

    for (line <- headerLines) {
      val colonIndex = line.indexOf(':')

      if (colonIndex == -1) {
        return Left(Response(400, body = "Bad Request"))
      }

      val name = line.substring(0, colonIndex)
      val value = if (colonIndex + 1 < line.length) {
        val afterColon = line.substring(colonIndex + 1)
        // Trim leading space if present
        if (afterColon.startsWith(" ")) afterColon.substring(1) else afterColon
      } else {
        ""
      }

      headers(name) = value
    }

    Right(headers.toMap)
  }

  /** Parses the request body based on Content-Length header.
    *
    * Reads the body from the input stream if Content-Length is present and valid.
    * Always uses UTF-8 encoding.
    *
    * @param inputStream the input stream to read from
    * @param headers the parsed HTTP headers
    * @param maxBodySize the maximum allowed body size in bytes
    * @return Either an error Response (400, 413) or the body string
    */
  def parseBody(inputStream: InputStream, headers: Map[String, String], maxBodySize: Int): Either[Response, String] = {
    headers.get("Content-Length") match {
      case None =>
        // No Content-Length header = empty body
        Right("")

      case Some(lengthStr) =>
        // Try to parse Content-Length as an integer
        val contentLength = try {
          lengthStr.toInt
        } catch {
          case _: NumberFormatException =>
            return Left(Response(400, body = "Bad Request"))
        }

        // Check for negative Content-Length
        if (contentLength < 0) {
          return Left(Response(400, body = "Bad Request"))
        }

        // Check if body size exceeds max
        if (contentLength > maxBodySize) {
          return Left(Response(413, body = "Payload Too Large"))
        }

        // Content-Length of 0 means empty body
        if (contentLength == 0) {
          return Right("")
        }

        // Read the body
        val buffer = new Array[Byte](contentLength)
        var totalRead = 0

        while (totalRead < contentLength) {
          val bytesRead = inputStream.read(buffer, totalRead, contentLength - totalRead)
          if (bytesRead == -1) {
            // Unexpected end of stream
            return Left(Response(400, body = "Bad Request"))
          }
          totalRead += bytesRead
        }

        // Convert to string using UTF-8
        Right(new String(buffer, "UTF-8"))
    }
  }

  /** Parses a complete HTTP request from an input stream.
    *
    * Reads and parses the request line, headers, and body according to HTTP/1.1 specification.
    * Uses the provided configuration for size limits.
    *
    * @param inputStream the input stream to read from
    * @param config the server configuration with size limits
    * @return Either an error Response or a complete Request
    */
  def parseRequest(inputStream: InputStream, config: ServerConfig): Either[Response, Request] = {
    val reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))

    // Read request line
    val requestLine = reader.readLine()
    if (requestLine == null) {
      return Left(Response(400, body = "Bad Request"))
    }

    // Parse request line
    parseRequestLine(requestLine) match {
      case Left(response) => return Left(response)
      case Right((methodStr, pathWithQuery, _)) =>
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
        parseHeaders(headerLines.toList, config.maxHeaderSize) match {
          case Left(response) => return Left(response)
          case Right(headers) =>
            // Parse body from reader instead of raw inputStream
            parseBodyFromReader(reader, headers, config.maxBodySize) match {
              case Left(response) => Left(response)
              case Right(body) =>
                Right(Request(
                  method = method,
                  path = path,
                  headers = headers,
                  body = body,
                  queryString = queryString
                ))
            }
        }
    }
  }

  /** Parses the request body from a BufferedReader based on Content-Length header.
    *
    * Similar to parseBody but works with a BufferedReader that has already consumed
    * the request line and headers.
    *
    * @param reader the BufferedReader positioned after headers
    * @param headers the parsed HTTP headers
    * @param maxBodySize the maximum allowed body size in bytes
    * @return Either an error Response (400, 413) or the body string
    */
  private def parseBodyFromReader(reader: BufferedReader, headers: Map[String, String], maxBodySize: Int): Either[Response, String] = {
    headers.get("Content-Length") match {
      case None =>
        // No Content-Length header = empty body
        Right("")

      case Some(lengthStr) =>
        // Try to parse Content-Length as an integer
        val contentLength = try {
          lengthStr.toInt
        } catch {
          case _: NumberFormatException =>
            return Left(Response(400, body = "Bad Request"))
        }

        // Check for negative Content-Length
        if (contentLength < 0) {
          return Left(Response(400, body = "Bad Request"))
        }

        // Check if body size exceeds max
        if (contentLength > maxBodySize) {
          return Left(Response(413, body = "Payload Too Large"))
        }

        // Content-Length of 0 means empty body
        if (contentLength == 0) {
          return Right("")
        }

        // Read the body using the reader
        val buffer = new Array[Char](contentLength)
        var totalRead = 0

        while (totalRead < contentLength) {
          val charsRead = reader.read(buffer, totalRead, contentLength - totalRead)
          if (charsRead == -1) {
            // Unexpected end of stream
            return Left(Response(400, body = "Bad Request"))
          }
          totalRead += charsRead
        }

        // Convert to string (already decoded as UTF-8 by InputStreamReader)
        Right(new String(buffer))
    }
  }

  /** Splits a path with query string into path and parsed query parameters.
    *
    * Example: "/search?q=scala&lang=en" -> ("/search", Map("q" -> List("scala"), "lang" -> List("en")))
    *
    * @param pathWithQuery the path potentially containing a query string
    * @return tuple of (path, queryString map)
    */
  private def parsePathAndQuery(pathWithQuery: String): (String, Map[String, List[String]]) = {
    val queryIndex = pathWithQuery.indexOf('?')
    if (queryIndex == -1) {
      // No query string
      (pathWithQuery, Map.empty)
    } else {
      val path = pathWithQuery.substring(0, queryIndex)
      val queryPart = pathWithQuery.substring(queryIndex + 1)
      val queryString = parseQueryString(queryPart)
      (path, queryString)
    }
  }

  /** Parses a query string into a map of parameter names to lists of values.
    *
    * Example: "q=scala&lang=en&tag=fp&tag=java" -> Map("q" -> List("scala"), "lang" -> List("en"), "tag" -> List("fp", "java"))
    *
    * @param query the query string without the leading '?'
    * @return map of parameter names to lists of values
    */
  private def parseQueryString(query: String): Map[String, List[String]] = {
    if (query.isEmpty) {
      return Map.empty
    }

    val params = mutable.Map[String, mutable.ListBuffer[String]]()

    for (pair <- query.split("&")) {
      val eqIndex = pair.indexOf('=')
      if (eqIndex != -1) {
        val name = pair.substring(0, eqIndex)
        val value = pair.substring(eqIndex + 1)

        if (!params.contains(name)) {
          params(name) = mutable.ListBuffer[String]()
        }
        params(name) += value
      }
    }

    params.view.mapValues(_.toList).toMap
  }
}
