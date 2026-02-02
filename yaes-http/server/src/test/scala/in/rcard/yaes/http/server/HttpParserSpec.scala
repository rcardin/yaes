package in.rcard.yaes.http.server

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream
import scala.util.{Failure, Success, Try}

class HttpParserSpec extends AnyFlatSpec with Matchers {

  "HttpParser.parseRequestLine" should "parse valid GET request" in {
    val line = "GET /path HTTP/1.1"
    val result = HttpParser.parseRequestLine(line)

    result should matchPattern {
      case Right((method, path, version)) if method == "GET" && path == "/path" && version == "HTTP/1.1" =>
    }
  }

  it should "parse valid POST request with HTTP/1.0" in {
    val line = "POST /users HTTP/1.0"
    val result = HttpParser.parseRequestLine(line)

    result should matchPattern {
      case Right((method, path, version)) if method == "POST" && path == "/users" && version == "HTTP/1.0" =>
    }
  }

  it should "parse method with query string" in {
    val line = "GET /search?q=test HTTP/1.1"
    val result = HttpParser.parseRequestLine(line)

    result should matchPattern {
      case Right((method, path, version)) if method == "GET" && path == "/search?q=test" && version == "HTTP/1.1" =>
    }
  }

  it should "parse all supported HTTP methods" in {
    val methods = List("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

    methods.foreach { method =>
      val line = s"$method /test HTTP/1.1"
      val result = HttpParser.parseRequestLine(line)

      result should matchPattern {
        case Right((m, _, _)) if m == method =>
      }
    }
  }

  it should "return 400 Bad Request for malformed line with no spaces" in {
    val line = "GET/pathHTTP/1.1"
    val result = HttpParser.parseRequestLine(line)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(400)
  }

  it should "return 400 Bad Request for line with only method" in {
    val line = "GET"
    val result = HttpParser.parseRequestLine(line)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(400)
  }

  it should "return 400 Bad Request for line with only method and path" in {
    val line = "GET /path"
    val result = HttpParser.parseRequestLine(line)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(400)
  }

  it should "return 501 Not Implemented for unknown method" in {
    val line = "TRACE /path HTTP/1.1"
    val result = HttpParser.parseRequestLine(line)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(501)
  }

  it should "return 501 Not Implemented for CONNECT method" in {
    val line = "CONNECT example.com:443 HTTP/1.1"
    val result = HttpParser.parseRequestLine(line)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(501)
  }

  it should "return 505 HTTP Version Not Supported for HTTP/2.0" in {
    val line = "GET /path HTTP/2.0"
    val result = HttpParser.parseRequestLine(line)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(505)
  }

  it should "return 505 HTTP Version Not Supported for HTTP/0.9" in {
    val line = "GET /path HTTP/0.9"
    val result = HttpParser.parseRequestLine(line)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(505)
  }

  it should "return 505 HTTP Version Not Supported for malformed version" in {
    val line = "GET /path HTTPS/1.1"
    val result = HttpParser.parseRequestLine(line)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(505)
  }

  "HttpParser.parseHeaders" should "parse a single header" in {
    val headerLines = List("Content-Type: application/json")
    val result = HttpParser.parseHeaders(headerLines, 16384)

    result shouldBe Right(Map("Content-Type" -> "application/json"))
  }

  it should "parse multiple headers" in {
    val headerLines = List(
      "Content-Type: application/json",
      "Content-Length: 42",
      "Host: example.com"
    )
    val result = HttpParser.parseHeaders(headerLines, 16384)

    result shouldBe Right(Map(
      "Content-Type" -> "application/json",
      "Content-Length" -> "42",
      "Host" -> "example.com"
    ))
  }

  it should "parse header with colon in value" in {
    val headerLines = List("Location: http://example.com:8080/path")
    val result = HttpParser.parseHeaders(headerLines, 16384)

    result shouldBe Right(Map("Location" -> "http://example.com:8080/path"))
  }

  it should "parse header with empty value" in {
    val headerLines = List("X-Custom-Header: ")
    val result = HttpParser.parseHeaders(headerLines, 16384)

    result shouldBe Right(Map("X-Custom-Header" -> ""))
  }

  it should "parse empty header list" in {
    val headerLines = List.empty[String]
    val result = HttpParser.parseHeaders(headerLines, 16384)

    result shouldBe Right(Map.empty[String, String])
  }

  it should "preserve header name casing" in {
    val headerLines = List(
      "Content-Type: text/html",
      "content-length: 123"
    )
    val result = HttpParser.parseHeaders(headerLines, 16384)

    result shouldBe Right(Map(
      "Content-Type" -> "text/html",
      "content-length" -> "123"
    ))
  }

  it should "return 400 Bad Request when headers exceed max size" in {
    val largeValue = "x" * 10000
    val headerLines = List(
      s"Header1: $largeValue",
      s"Header2: $largeValue"
    )
    val maxSize = 16384
    val result = HttpParser.parseHeaders(headerLines, maxSize)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(400)
  }

  it should "handle headers at exactly max size" in {
    // Each header line is "Name: Value\r\n" = 12 bytes for "X: abcd\r\n"
    val headerLines = List.fill(1000)("X: abcdefgh") // ~12KB of headers
    val result = HttpParser.parseHeaders(headerLines, 16384)

    result.isRight shouldBe true
  }

  it should "return 400 Bad Request for malformed header without colon" in {
    val headerLines = List("MalformedHeader")
    val result = HttpParser.parseHeaders(headerLines, 16384)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(400)
  }

  "HttpParser.parseBody" should "read body with Content-Length" in {
    val bodyContent = "Hello, World!"
    val inputStream = new ByteArrayInputStream(bodyContent.getBytes("UTF-8"))
    val headers = Map("Content-Length" -> "13")

    val result = HttpParser.parseBody(inputStream, headers, 1048576)

    result shouldBe Right("Hello, World!")
  }

  it should "return empty body when Content-Length is absent" in {
    val inputStream = new ByteArrayInputStream(Array.empty[Byte])
    val headers = Map.empty[String, String]

    val result = HttpParser.parseBody(inputStream, headers, 1048576)

    result shouldBe Right("")
  }

  it should "return empty body when Content-Length is 0" in {
    val inputStream = new ByteArrayInputStream(Array.empty[Byte])
    val headers = Map("Content-Length" -> "0")

    val result = HttpParser.parseBody(inputStream, headers, 1048576)

    result shouldBe Right("")
  }

  it should "handle body at exactly max size" in {
    val maxSize = 1024
    val bodyContent = "x" * maxSize
    val inputStream = new ByteArrayInputStream(bodyContent.getBytes("UTF-8"))
    val headers = Map("Content-Length" -> maxSize.toString)

    val result = HttpParser.parseBody(inputStream, headers, maxSize)

    result shouldBe Right(bodyContent)
  }

  it should "return 413 Payload Too Large when body exceeds max size" in {
    val maxSize = 1024
    val bodySize = maxSize + 1
    val inputStream = new ByteArrayInputStream(new Array[Byte](bodySize))
    val headers = Map("Content-Length" -> bodySize.toString)

    val result = HttpParser.parseBody(inputStream, headers, maxSize)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(413)
  }

  it should "return 400 Bad Request for invalid Content-Length (non-numeric)" in {
    val inputStream = new ByteArrayInputStream(Array.empty[Byte])
    val headers = Map("Content-Length" -> "invalid")

    val result = HttpParser.parseBody(inputStream, headers, 1048576)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(400)
  }

  it should "return 400 Bad Request for negative Content-Length" in {
    val inputStream = new ByteArrayInputStream(Array.empty[Byte])
    val headers = Map("Content-Length" -> "-1")

    val result = HttpParser.parseBody(inputStream, headers, 1048576)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(400)
  }

  it should "handle UTF-8 encoded body correctly" in {
    val bodyContent = "Hëllö, Wørld! 你好"
    val inputStream = new ByteArrayInputStream(bodyContent.getBytes("UTF-8"))
    val bodyBytes = bodyContent.getBytes("UTF-8")
    val headers = Map("Content-Length" -> bodyBytes.length.toString)

    val result = HttpParser.parseBody(inputStream, headers, 1048576)

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

    val result = HttpParser.parseRequest(inputStream, config)

    result match {
      case Right(request) =>
        request.method shouldBe Method.GET
        request.path shouldBe "/users"
        request.headers shouldBe Map("Host" -> "example.com", "User-Agent" -> "Test/1.0")
        request.body shouldBe ""
        request.queryString shouldBe Map.empty[String, List[String]]
      case Left(response) =>
        fail(s"Expected Right but got error response: ${response.status}")
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

    val result = HttpParser.parseRequest(inputStream, config)

    result match {
      case Right(request) =>
        request.method shouldBe Method.POST
        request.path shouldBe "/users"
        request.headers("Content-Type") shouldBe "application/json"
        request.headers("Content-Length") shouldBe bodyContent.length.toString
        request.body shouldBe bodyContent
        request.queryString shouldBe Map.empty[String, List[String]]
      case Left(response) =>
        fail(s"Expected Right but got error response: ${response.status}")
    }
  }

  it should "parse request with query parameters" in {
    val requestText =
      "GET /search?q=scala&lang=en HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = HttpParser.parseRequest(inputStream, config)

    result match {
      case Right(request) =>
        request.method shouldBe Method.GET
        request.path shouldBe "/search"
        request.queryString shouldBe Map("q" -> List("scala"), "lang" -> List("en"))
      case Left(response) =>
        fail(s"Expected Right but got error response: ${response.status}")
    }
  }

  it should "parse request with multiple values for same query parameter" in {
    val requestText =
      "GET /filter?tag=java&tag=scala&tag=fp HTTP/1.1\r\n" +
      "Host: example.com\r\n" +
      "\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = HttpParser.parseRequest(inputStream, config)

    result match {
      case Right(request) =>
        request.method shouldBe Method.GET
        request.path shouldBe "/filter"
        request.queryString shouldBe Map("tag" -> List("java", "scala", "fp"))
      case Left(response) =>
        fail(s"Expected Right but got error response: ${response.status}")
    }
  }

  it should "return error response for malformed request line" in {
    val requestText = "INVALID REQUEST\r\n\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig()

    val result = HttpParser.parseRequest(inputStream, config)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(400)
  }

  it should "return error response when headers exceed max size" in {
    val largeHeader = "x" * 20000
    val requestText =
      s"GET /path HTTP/1.1\r\n" +
      s"Large-Header: $largeHeader\r\n" +
      s"\r\n"
    val inputStream = new ByteArrayInputStream(requestText.getBytes("UTF-8"))
    val config = ServerConfig(maxHeaderSize = 16.kilobytes)

    val result = HttpParser.parseRequest(inputStream, config)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(400)
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

    val result = HttpParser.parseRequest(inputStream, config)

    result.isLeft shouldBe true
    result.left.map(_.status) shouldBe Left(413)
  }
}
