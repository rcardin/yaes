package in.rcard.yaes.http.server.parsing

import in.rcard.yaes.*
import in.rcard.yaes.http.core.Method
import in.rcard.yaes.http.server.*
import in.rcard.yaes.http.server.parsing.{HttpParser, HttpParseError}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream
import scala.util.{Failure, Success, Try}
import in.rcard.yaes.http.server.ServerConfig
import in.rcard.yaes.http.server.Request

class HttpParserSpec extends AnyFlatSpec with Matchers {

  "HttpParser.parseRequestLine" should "parse valid GET request" in {
    val line = "GET /path HTTP/1.1"
    val result = Raise.either[HttpParseError, (String, String, String)] {
      HttpParser.parseRequestLine(line)
    }

    result should matchPattern {
      case Right((method, path, version)) if method == "GET" && path == "/path" && version == "HTTP/1.1" =>
    }
  }

  it should "parse valid POST request with HTTP/1.0" in {
    val line = "POST /users HTTP/1.0"
    val result = Raise.either[HttpParseError, (String, String, String)] {
      HttpParser.parseRequestLine(line)
    }

    result should matchPattern {
      case Right((method, path, version)) if method == "POST" && path == "/users" && version == "HTTP/1.0" =>
    }
  }

  it should "parse method with query string" in {
    val line = "GET /search?q=test HTTP/1.1"
    val result = Raise.either[HttpParseError, (String, String, String)] {
      HttpParser.parseRequestLine(line)
    }

    result should matchPattern {
      case Right((method, path, version)) if method == "GET" && path == "/search?q=test" && version == "HTTP/1.1" =>
    }
  }

  it should "parse all supported HTTP methods" in {
    val methods = List("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

    methods.foreach { method =>
      val line = s"$method /test HTTP/1.1"
      val result = Raise.either[HttpParseError, (String, String, String)] {
        HttpParser.parseRequestLine(line)
      }

      result should matchPattern {
        case Right((m, _, _)) if m == method =>
      }
    }
  }

  it should "return 400 Bad Request for malformed line with no spaces" in {
    val line = "GET/pathHTTP/1.1"
    val error = Raise.either { HttpParser.parseRequestLine(line) }

    error shouldBe Left(HttpParseError.MalformedRequestLine)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "return 400 Bad Request for line with only method" in {
    val line = "GET"
    val error = Raise.either { HttpParser.parseRequestLine(line) }

    error shouldBe Left(HttpParseError.MalformedRequestLine)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "return 400 Bad Request for line with only method and path" in {
    val line = "GET /path"
    val error = Raise.either { HttpParser.parseRequestLine(line) }

    error shouldBe Left(HttpParseError.MalformedRequestLine)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "return 501 Not Implemented for unknown method" in {
    val line = "TRACE /path HTTP/1.1"
    val error = Raise.either { HttpParser.parseRequestLine(line) }

    error shouldBe Left(HttpParseError.UnsupportedMethod("TRACE"))
    error.left.toOption.map(_.toResponse.status) shouldBe Some(501)
  }

  it should "return 501 Not Implemented for CONNECT method" in {
    val line = "CONNECT example.com:443 HTTP/1.1"
    val error = Raise.either { HttpParser.parseRequestLine(line) }

    error shouldBe Left(HttpParseError.UnsupportedMethod("CONNECT"))
    error.left.toOption.map(_.toResponse.status) shouldBe Some(501)
  }

  it should "return 505 HTTP Version Not Supported for HTTP/2.0" in {
    val line = "GET /path HTTP/2.0"
    val error = Raise.either { HttpParser.parseRequestLine(line) }

    error shouldBe Left(HttpParseError.UnsupportedHttpVersion("HTTP/2.0"))
    error.left.toOption.map(_.toResponse.status) shouldBe Some(505)
  }

  it should "return 505 HTTP Version Not Supported for HTTP/0.9" in {
    val line = "GET /path HTTP/0.9"
    val error = Raise.either { HttpParser.parseRequestLine(line) }

    error shouldBe Left(HttpParseError.UnsupportedHttpVersion("HTTP/0.9"))
    error.left.toOption.map(_.toResponse.status) shouldBe Some(505)
  }

  it should "return 505 HTTP Version Not Supported for malformed version" in {
    val line = "GET /path HTTPS/1.1"
    val error = Raise.either { HttpParser.parseRequestLine(line) }

    error shouldBe Left(HttpParseError.UnsupportedHttpVersion("HTTPS/1.1"))
    error.left.toOption.map(_.toResponse.status) shouldBe Some(505)
  }

  "HttpParser.parseHeaders" should "parse a single header" in {
    val headerLines = List("Content-Type: application/json")
    val result = Raise.either[HttpParseError, Map[String, String]] {
      HttpParser.parseHeaders(headerLines, 16384)
    }

    result shouldBe Right(Map("content-type" -> "application/json"))
  }

  it should "parse multiple headers" in {
    val headerLines = List(
      "Content-Type: application/json",
      "Content-Length: 42",
      "Host: example.com"
    )
    val result = Raise.either[HttpParseError, Map[String, String]] {
      HttpParser.parseHeaders(headerLines, 16384)
    }

    result shouldBe Right(Map(
      "content-type" -> "application/json",
      "content-length" -> "42",
      "host" -> "example.com"
    ))
  }

  it should "parse header with colon in value" in {
    val headerLines = List("Location: http://example.com:8080/path")
    val result = Raise.either[HttpParseError, Map[String, String]] {
      HttpParser.parseHeaders(headerLines, 16384)
    }

    result shouldBe Right(Map("location" -> "http://example.com:8080/path"))
  }

  it should "parse header with empty value" in {
    val headerLines = List("X-Custom-Header: ")
    val result = Raise.either[HttpParseError, Map[String, String]] {
      HttpParser.parseHeaders(headerLines, 16384)
    }

    result shouldBe Right(Map("x-custom-header" -> ""))
  }

  it should "parse empty header list" in {
    val headerLines = List.empty[String]
    val result = Raise.either[HttpParseError, Map[String, String]] {
      HttpParser.parseHeaders(headerLines, 16384)
    }

    result shouldBe Right(Map.empty[String, String])
  }

  it should "normalize header names to lowercase" in {
    val headerLines = List(
      "Content-Type: text/html",
      "content-length: 123"
    )
    val result = Raise.either[HttpParseError, Map[String, String]] {
      HttpParser.parseHeaders(headerLines, 16384)
    }

    result shouldBe Right(Map(
      "content-type" -> "text/html",
      "content-length" -> "123"
    ))
  }

  it should "store different casings of same header as lowercase" in {
    val headerLines = List(
      "CONTENT-TYPE: text/html",
      "Content-Length: 456",
      "X-Custom-Header: value"
    )
    val result = Raise.either[HttpParseError, Map[String, String]] {
      HttpParser.parseHeaders(headerLines, 16384)
    }

    result shouldBe Right(Map(
      "content-type" -> "text/html",
      "content-length" -> "456",
      "x-custom-header" -> "value"
    ))
  }

  it should "return 400 Bad Request when headers exceed max size" in {
    val largeValue = "x" * 10000
    val headerLines = List(
      s"Header1: $largeValue",
      s"Header2: $largeValue"
    )
    val maxSize = 16384
    val error = Raise.either { HttpParser.parseHeaders(headerLines, maxSize) }

    error shouldBe Left(HttpParseError.MalformedHeaders)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "handle headers at exactly max size" in {
    // Each header line is "Name: Value\r\n" = 12 bytes for "X: abcd\r\n"
    val headerLines = List.fill(1000)("X: abcdefgh") // ~12KB of headers
    val result = Raise.either[HttpParseError, Map[String, String]] {
      HttpParser.parseHeaders(headerLines, 16384)
    }

    result.isRight shouldBe true
  }

  it should "return 400 Bad Request for malformed header without colon" in {
    val headerLines = List("MalformedHeader")
    val error = Raise.either { HttpParser.parseHeaders(headerLines, 16384) }

    error shouldBe Left(HttpParseError.MalformedHeaders)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  "HttpParser.parseBody" should "read body with Content-Length" in {
    val bodyContent = "Hello, World!"
    val inputStream = new ByteArrayInputStream(bodyContent.getBytes("UTF-8"))
    val headers = Map("content-length" -> "13")

    val result = Raise.either[HttpParseError, String] {
      HttpParser.parseBody(inputStream, headers, 1048576)
    }

    result shouldBe Right("Hello, World!")
  }

  it should "return empty body when Content-Length is absent" in {
    val inputStream = new ByteArrayInputStream(Array.empty[Byte])
    val headers = Map.empty[String, String]

    val result = Raise.either[HttpParseError, String] {
      HttpParser.parseBody(inputStream, headers, 1048576)
    }

    result shouldBe Right("")
  }

  it should "return empty body when Content-Length is 0" in {
    val inputStream = new ByteArrayInputStream(Array.empty[Byte])
    val headers = Map("content-length" -> "0")

    val result = Raise.either[HttpParseError, String] {
      HttpParser.parseBody(inputStream, headers, 1048576)
    }

    result shouldBe Right("")
  }

  it should "handle body at exactly max size" in {
    val maxSize = 1024
    val bodyContent = "x" * maxSize
    val inputStream = new ByteArrayInputStream(bodyContent.getBytes("UTF-8"))
    val headers = Map("content-length" -> maxSize.toString)

    val result = Raise.either[HttpParseError, String] {
      HttpParser.parseBody(inputStream, headers, maxSize)
    }

    result shouldBe Right(bodyContent)
  }

  it should "return 413 Payload Too Large when body exceeds max size" in {
    val maxSize = 1024
    val bodySize = maxSize + 1
    val inputStream = new ByteArrayInputStream(new Array[Byte](bodySize))
    val headers = Map("content-length" -> bodySize.toString)

    val error = Raise.either { HttpParser.parseBody(inputStream, headers, maxSize) }

    error shouldBe Left(HttpParseError.PayloadTooLarge(bodySize, maxSize))
    error.left.toOption.map(_.toResponse.status) shouldBe Some(413)
  }

  it should "return 400 Bad Request for invalid Content-Length (non-numeric)" in {
    val inputStream = new ByteArrayInputStream(Array.empty[Byte])
    val headers = Map("content-length" -> "invalid")

    val error = Raise.either { HttpParser.parseBody(inputStream, headers, 1048576) }

    error shouldBe Left(HttpParseError.InvalidContentLength)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "return 400 Bad Request for negative Content-Length" in {
    val inputStream = new ByteArrayInputStream(Array.empty[Byte])
    val headers = Map("content-length" -> "-1")

    val error = Raise.either { HttpParser.parseBody(inputStream, headers, 1048576) }

    error shouldBe Left(HttpParseError.InvalidContentLength)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "handle UTF-8 encoded body correctly" in {
    val bodyContent = "Hëllö, Wørld! 你好"
    val inputStream = new ByteArrayInputStream(bodyContent.getBytes("UTF-8"))
    val bodyBytes = bodyContent.getBytes("UTF-8")
    val headers = Map("content-length" -> bodyBytes.length.toString)

    val result = Raise.either[HttpParseError, String] {
      HttpParser.parseBody(inputStream, headers, 1048576)
    }

    result shouldBe Right(bodyContent)
  }

  "HttpParser.parseRequest" should "parse complete GET request with no body" in {
    val requestText =
      "GET /users HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "User-Agent: Test/1.0\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.method shouldBe Method.GET
        request.path shouldBe "/users"
        request.headers shouldBe Map("host" -> "example.com", "user-agent" -> "Test/1.0")
        request.body shouldBe ""
        request.queryString shouldBe Map.empty[String, List[String]]
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "parse complete POST request with body" in {
    val bodyContent = """{"name":"John","age":30}"""
    val requestText =
      s"POST /users HTTP/1.1\r\n" +
      s"Content-Type: application/json\r\n" +
      s"Content-Length: ${bodyContent.length}\r\n" +
      s"\r\n" +
      s"$bodyContent"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.method shouldBe Method.POST
        request.path shouldBe "/users"
        request.headers("content-type") shouldBe "application/json"
        request.headers("content-length") shouldBe bodyContent.length.toString
        request.body shouldBe bodyContent
        request.queryString shouldBe Map.empty[String, List[String]]
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "parse request with query parameters" in {
    val requestText =
      "GET /search?q=scala&lang=en HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.method shouldBe Method.GET
        request.path shouldBe "/search"
        request.queryString shouldBe Map("q" -> List("scala"), "lang" -> List("en"))
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "parse request with multiple values for same query parameter" in {
    val requestText =
      "GET /filter?tag=java&tag=scala&tag=fp HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.method shouldBe Method.GET
        request.path shouldBe "/filter"
        request.queryString shouldBe Map("tag" -> List("java", "scala", "fp"))
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "return error response for malformed request line" in {
    val requestText = "INVALID REQUEST\r\n\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val error = Raise.either { HttpParser.parseRequest(inputStream, config) }

    error shouldBe Left(HttpParseError.MalformedRequestLine)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "return error response when headers exceed max size" in {
    val largeHeader = "x" * 20000
    val requestText =
      s"GET /path HTTP/1.1\r\n" +
      s"Large-Header: $largeHeader\r\n" +
      s"\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig(maxHeaderSize = 16.kilobytes)

    val error = Raise.either { HttpParser.parseRequest(inputStream, config) }

    error shouldBe Left(HttpParseError.MalformedHeaders)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "return error response when body exceeds max size" in {
    val bodyContent = "x" * 2000000  // 2MB
    val requestText =
      s"POST /data HTTP/1.1\r\n" +
      s"Content-Length: ${bodyContent.length}\r\n" +
      s"\r\n" +
      s"$bodyContent"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig(maxBodySize = 1.megabytes)

    val error = Raise.either { HttpParser.parseRequest(inputStream, config) }

    error shouldBe Left(HttpParseError.PayloadTooLarge(bodyContent.length, 1.megabytes))
    error.left.toOption.map(_.toResponse.status) shouldBe Some(413)
  }

  it should "parse body with lowercase content-length header" in {
    val bodyContent = """{"id":42}"""
    val requestText =
      s"POST /api HTTP/1.1\r\n" +
      s"content-length: ${bodyContent.length}\r\n" +
      s"\r\n" +
      s"$bodyContent"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.body shouldBe bodyContent
        request.headers("content-length") shouldBe bodyContent.length.toString
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "parse body with mixed-case content-length header" in {
    val bodyContent = "test data"
    val requestText =
      s"POST /api HTTP/1.1\r\n" +
      s"CoNtEnT-LeNgTh: ${bodyContent.length}\r\n" +
      s"\r\n" +
      s"$bodyContent"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.body shouldBe bodyContent
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  "Request.header" should "perform case-insensitive header lookup" in {
    val request = Request(
      method = Method.GET,
      path = "/test",
      headers = Map("content-type" -> "application/json", "x-custom" -> "value"),
      body = "",
      queryString = Map.empty
    )

    request.header("Content-Type") shouldBe Some("application/json")
    request.header("content-type") shouldBe Some("application/json")
    request.header("CONTENT-TYPE") shouldBe Some("application/json")
    request.header("X-Custom") shouldBe Some("value")
    request.header("x-custom") shouldBe Some("value")
  }

  it should "return None for non-existent headers" in {
    val request = Request(
      method = Method.GET,
      path = "/test",
      headers = Map("content-type" -> "text/html"),
      body = "",
      queryString = Map.empty
    )

    request.header("Authorization") shouldBe None
    request.header("X-Missing") shouldBe None
  }

  "HttpParser.parseRequest with URL-encoded query parameters" should "decode percent-encoded spaces" in {
    val requestText =
      "GET /search?name=hello%20world HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.queryString shouldBe Map("name" -> List("hello world"))
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "decode plus-encoded spaces" in {
    val requestText =
      "GET /search?name=hello+world HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.queryString shouldBe Map("name" -> List("hello world"))
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "decode percent-encoded special characters" in {
    val requestText =
      "GET /search?key=a%26b HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.queryString shouldBe Map("key" -> List("a&b"))
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "decode UTF-8 encoded characters" in {
    val requestText =
      "GET /search?symbol=%E2%9C%93 HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.queryString shouldBe Map("symbol" -> List("✓"))
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "decode both parameter names and values" in {
    val requestText =
      "GET /search?search%20term=scala%20programming HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.queryString shouldBe Map("search term" -> List("scala programming"))
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "decode multiple encoded query parameters" in {
    val requestText =
      "GET /search?q=scala%20lang&filter=type%3Dbook&tag=fp+guide HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.queryString shouldBe Map(
          "q" -> List("scala lang"),
          "filter" -> List("type=book"),
          "tag" -> List("fp guide")
        )
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "return 400 Bad Request for invalid percent-encoding" in {
    val requestText =
      "GET /search?name=%ZZ HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val error = Raise.either { HttpParser.parseRequest(inputStream, config) }

    error shouldBe Left(HttpParseError.MalformedQueryString)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "return 400 Bad Request for incomplete percent-encoding" in {
    val requestText =
      "GET /search?name=test%2 HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val error = Raise.either { HttpParser.parseRequest(inputStream, config) }

    error shouldBe Left(HttpParseError.MalformedQueryString)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "decode empty query parameter values" in {
    val requestText =
      "GET /search?key= HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.queryString shouldBe Map("key" -> List(""))
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  "HttpParser.parseRequest" should "decode URL-encoded path segments with spaces" in {
    val requestText =
      "GET /users/john%20doe HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.path shouldBe "/users/john doe"
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "decode URL-encoded path segments with special characters" in {
    val requestText =
      "GET /files/my%2Ffile.txt HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.path shouldBe "/files/my/file.txt"
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "decode UTF-8 encoded characters in path" in {
    val requestText =
      "GET /%E2%9C%93 HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.path shouldBe "/✓"
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }

  it should "return 400 Bad Request for invalid percent-encoding in path" in {
    val requestText =
      "GET /users/%ZZ HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val error = Raise.either { HttpParser.parseRequest(inputStream, config) }

    error shouldBe Left(HttpParseError.MalformedPath)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "reject path traversal attempts with literal .." in {
    val requestText =
      "GET /files/../etc/passwd HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val error = Raise.either { HttpParser.parseRequest(inputStream, config) }

    error shouldBe Left(HttpParseError.MalformedPath)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "reject path traversal attempts with encoded .." in {
    val requestText =
      "GET /files/%2e%2e/etc/passwd HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val error = Raise.either { HttpParser.parseRequest(inputStream, config) }

    error shouldBe Left(HttpParseError.MalformedPath)
    error.left.toOption.map(_.toResponse.status) shouldBe Some(400)
  }

  it should "decode path and query parameters independently" in {
    val requestText =
      "GET /users/jane%20doe?role=admin%20user HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = Raise.either[HttpParseError, Request] {
      HttpParser.parseRequest(inputStream, config)
    }

    result match {
      case Right(request) =>
        request.path shouldBe "/users/jane doe"
        request.queryString shouldBe Map("role" -> List("admin user"))
      case Left(error) =>
        fail(s"Expected Right but got error: ${error.message}")
    }
  }
}
