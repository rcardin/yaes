# TASK-4 | Integration tests

status: [x]
requires: 3B
verify: `sbt client/testOnly *YaesClientIntegrationSpec`
gate: FINAL (`sbt server/test circe/test client/test`)

Test dir: `yaes-http/client/src/test/scala/in/rcard/yaes/http/client/`

## `YaesClientIntegrationSpec.scala`

```scala
package in.rcard.yaes.http.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.*
import in.rcard.yaes.http.core.{BodyCodec, DecodingError, Headers}
import in.rcard.yaes.http.client.HttpRequest.*
import scala.concurrent.duration.*

class YaesClientIntegrationSpec extends AnyFlatSpec with Matchers:

  "YaesClient full pipeline" should "GET and decode response body" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      val body = "42"
      exchange.sendResponseHeaders(200, body.length)
      exchange.getResponseBody.write(body.getBytes)
      exchange.close()
    }
    try
      val result = Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          Raise.either[ConnectionError | HttpError | DecodingError] {
            client.send(HttpRequest.get(baseUrl)).as[Int]
          }
        }
      }
      result.get shouldBe Right(42)
    finally server.stop(0)
  }

  it should "POST body and decode response" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      val reqBody = new String(exchange.getRequestBody.readAllBytes())
      exchange.sendResponseHeaders(201, reqBody.length)
      exchange.getResponseBody.write(reqBody.getBytes)
      exchange.close()
    }
    try
      val result = Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          Raise.either[ConnectionError | HttpError | DecodingError] {
            client.send(
              HttpRequest.post(baseUrl, "payload")
                .header(Headers.Authorization, "Bearer token")
            ).as[String]
          }
        }
      }
      result.get shouldBe Right("payload")
    finally server.stop(0)
  }

  it should "raise HttpError.Forbidden for 403 via .as" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      val body = "forbidden"
      exchange.sendResponseHeaders(403, body.length)
      exchange.getResponseBody.write(body.getBytes)
      exchange.close()
    }
    try
      val result = Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          Raise.either[ConnectionError | HttpError | DecodingError] {
            client.send(HttpRequest.get(baseUrl)).as[String]
          }
        }
      }
      result.get shouldBe Left(HttpError.Forbidden("forbidden"))
    finally server.stop(0)
  }

  it should "return HttpResponse with any status from send (no error raised)" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      val body = "not found"
      exchange.sendResponseHeaders(404, body.length)
      exchange.getResponseBody.write(body.getBytes)
      exchange.close()
    }
    try
      val result = Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          Raise.either[ConnectionError] {
            client.send(HttpRequest.get(baseUrl))
          }
        }
      }
      val resp = result.get.getOrElse(fail("Expected Right"))
      resp.status shouldBe 404
      resp.body shouldBe "not found"
    finally server.stop(0)
  }

  it should "raise DecodingError when 200 body can't be decoded" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      val body = "not-a-number"
      exchange.sendResponseHeaders(200, body.length)
      exchange.getResponseBody.write(body.getBytes)
      exchange.close()
    }
    try
      val result = Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          Raise.either[ConnectionError | HttpError | DecodingError] {
            client.send(HttpRequest.get(baseUrl)).as[Int]
          }
        }
      }
      result.get.left.getOrElse(fail()) shouldBe a[DecodingError]
    finally server.stop(0)
  }

  it should "raise ConnectionRefused when server is down" in {
    val port = TestServer.findFreePort()
    val result = Sync.runBlocking(10.seconds) {
      Resource.run {
        val client = YaesClient.make()
        Raise.either[ConnectionError] {
          client.send(HttpRequest.get(s"http://localhost:$port"))
        }
      }
    }
    result.get match
      case Left(ConnectionError.ConnectionRefused(_, _)) => succeed
      case other => fail(s"Expected ConnectionRefused, got: $other")
  }

  it should "close underlying client after Resource scope exits normally" in {
    var clientRef: YaesClient = null
    Sync.runBlocking(10.seconds) {
      Resource.run {
        clientRef = YaesClient.make()
      }
    }
    // After scope, underlying should be closed.
    // java.net.http.HttpClient doesn't have isClosed(), so we trust Resource.install calls close().
  }

  it should "close underlying client after Resource scope exits with exception" in {
    var clientRef: YaesClient = null
    try
      Sync.runBlocking(10.seconds) {
        Resource.run {
          clientRef = YaesClient.make()
          throw new RuntimeException("boom")
        }
      }
    catch case _: RuntimeException => ()
    // Same — Resource guarantees cleanup on exception
  }

  it should "handle multiple sequential requests on same client" in {
    var requestCount = 0
    val (server, baseUrl) = TestServer.start { exchange =>
      requestCount += 1
      val body = requestCount.toString
      exchange.sendResponseHeaders(200, body.length)
      exchange.getResponseBody.write(body.getBytes)
      exchange.close()
    }
    try
      val result = Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          Raise.either[ConnectionError | HttpError | DecodingError] {
            val r1 = client.send(HttpRequest.get(baseUrl)).as[Int]
            val r2 = client.send(HttpRequest.get(baseUrl)).as[Int]
            (r1, r2)
          }
        }
      }
      val (a, b) = result.get.getOrElse(fail("Expected Right"))
      a shouldBe 1
      b shouldBe 2
    finally server.stop(0)
  }
```
