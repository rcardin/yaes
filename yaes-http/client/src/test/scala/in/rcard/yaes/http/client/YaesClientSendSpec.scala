package in.rcard.yaes.http.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.*
import in.rcard.yaes.http.client.Uri.InvalidUri
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

class YaesClientSendSpec extends AnyFlatSpec with Matchers:

  private def uri(raw: String): Uri =
    Raise.either[InvalidUri, Uri] { Uri(raw) } match
      case Left(e)  => fail(s"Invalid URI: ${e.reason}")
      case Right(u) => u

  "YaesClient.send" should "return status, headers, and body for GET" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      exchange.getResponseHeaders.add("X-Custom", "test-value")
      val body = "hello"
      exchange.sendResponseHeaders(200, body.length)
      exchange.getResponseBody.write(body.getBytes)
      exchange.close()
    }
    try
      Sync
        .runBlocking(10.seconds) {
          Resource.run {
            val client = YaesClient.make()
            val resp   = Raise.run {
              client.send(HttpRequest.get(uri(baseUrl)))
            }
            resp match
              case r: HttpResponse =>
                r.status shouldBe 200
                r.body shouldBe "hello"
                r.header("x-custom") shouldBe Some("test-value")
              case _ => fail("Expected HttpResponse")
          }
        }
        .get
    finally server.stop(0)
  }

  it should "send encoded body and Content-Type for POST" in {
    var receivedBody        = ""
    var receivedContentType = ""
    val (server, baseUrl)   = TestServer.start { exchange =>
      receivedContentType = exchange.getRequestHeaders.getFirst("Content-type")
      receivedBody = new String(exchange.getRequestBody.readAllBytes())
      exchange.sendResponseHeaders(201, receivedBody.length)
      exchange.getResponseBody.write(receivedBody.getBytes)
      exchange.close()
    }
    try
      Sync
        .runBlocking(10.seconds) {
          Resource.run {
            val client = YaesClient.make()
            val resp   = Raise.run { client.send(HttpRequest.post(uri(baseUrl), "payload")) }
            resp.asInstanceOf[HttpResponse].status shouldBe 201
            receivedBody shouldBe "payload"
            receivedContentType shouldBe "text/plain; charset=UTF-8"
          }
        }
        .get
    finally server.stop(0)
  }

  it should "append URL-encoded query parameters" in {
    var receivedUri       = ""
    val (server, baseUrl) = TestServer.start { exchange =>
      receivedUri = exchange.getRequestURI.toString
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    }
    try
      Sync
        .runBlocking(10.seconds) {
          Resource.run {
            val client = YaesClient.make()
            Raise.run {
              client.send(
                HttpRequest
                  .get(uri(baseUrl))
                  .queryParam("q", "hello world")
                  .queryParam("tag", "a&b")
              )
            }
            receivedUri should include("q=hello")
            receivedUri should include("tag=a%26b")
          }
        }
        .get
    finally server.stop(0)
  }

  it should "send custom headers" in {
    var receivedAuth      = ""
    val (server, baseUrl) = TestServer.start { exchange =>
      receivedAuth = exchange.getRequestHeaders.getFirst("Authorization")
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    }
    try
      Sync
        .runBlocking(10.seconds) {
          Resource.run {
            val client = YaesClient.make()
            Raise.run {
              client.send(
                HttpRequest.get(uri(baseUrl)).header("Authorization", "Bearer tok")
              )
            }
            receivedAuth shouldBe "Bearer tok"
          }
        }
        .get
    finally server.stop(0)
  }

  it should "return non-2xx status without raising ConnectionError" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      val body = "not found"
      exchange.sendResponseHeaders(404, body.length)
      exchange.getResponseBody.write(body.getBytes)
      exchange.close()
    }
    try
      Sync
        .runBlocking(10.seconds) {
          Resource.run {
            val client = YaesClient.make()
            val resp   = Raise.run { client.send(HttpRequest.get(uri(baseUrl))) }
            resp match
              case r: HttpResponse =>
                r.status shouldBe 404
                r.body shouldBe "not found"
              case err => fail(s"Expected HttpResponse, got error: $err")
          }
        }
        .get
    finally server.stop(0)
  }

  it should "handle multiple requests on same client" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      val body = exchange.getRequestURI.getPath
      exchange.sendResponseHeaders(200, body.length)
      exchange.getResponseBody.write(body.getBytes)
      exchange.close()
    }
    try
      Sync
        .runBlocking(10.seconds) {
          Resource.run {
            val client = YaesClient.make()
            Raise.run {
              val r1 = client.send(HttpRequest.get(uri(baseUrl + "/a")))
              val r2 = client.send(HttpRequest.get(uri(baseUrl + "/b")))
              r1
            }
          }
        }
        .get
    finally server.stop(0)
  }

  it should "raise ConnectionRefused when no server is listening" in {
    val port = TestServer.findFreePort()
    Sync
      .runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          val result = Raise.either[ConnectionError, HttpResponse] {
            client.send(HttpRequest.get(uri(s"http://localhost:$port")))
          }
          result match
            case Left(ConnectionError.ConnectionRefused(host, p)) =>
              host shouldBe "localhost"
              p shouldBe port
            case other => fail(s"Expected ConnectionRefused, got: $other")
        }
      }
      .get
  }

  it should "raise RequestTimeout when per-request timeout exceeded" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      Thread.sleep(5000)
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    }
    try
      Sync
        .runBlocking(10.seconds) {
          Resource.run {
            val client = YaesClient.make()
            val result = Raise.either[ConnectionError, HttpResponse] {
              client.send(HttpRequest.get(uri(baseUrl)).timeout(100.millis))
            }
            result match
              case Left(ConnectionError.RequestTimeout(url)) =>
                url shouldBe baseUrl
              case other => fail(s"Expected RequestTimeout, got: $other")
          }
        }
        .get
    finally server.stop(0)
  }

  it should "append query params to URL that already has query string" in {
    var receivedUri       = ""
    val (server, baseUrl) = TestServer.start { exchange =>
      receivedUri = exchange.getRequestURI.toString
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    }
    try
      Sync
        .runBlocking(10.seconds) {
          Resource.run {
            val client = YaesClient.make()
            Raise.run {
              client.send(
                HttpRequest.get(uri(baseUrl + "?existing=1")).queryParam("new", "2")
              )
            }
            receivedUri should include("existing=1")
            receivedUri should include("new=2")
            receivedUri should include("&")
          }
        }
        .get
    finally server.stop(0)
  }
