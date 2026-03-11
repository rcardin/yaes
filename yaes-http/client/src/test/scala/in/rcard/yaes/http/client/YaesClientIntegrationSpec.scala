package in.rcard.yaes.http.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.*
import in.rcard.yaes.http.core.{BodyCodec, DecodingError, Headers}
import in.rcard.yaes.http.client.HttpRequest.*
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

class YaesClientIntegrationSpec extends AnyFlatSpec with Matchers:

  private def sendAndDecode[A](client: YaesClient, request: HttpRequest)(
      using BodyCodec[A],
      Sync
  ): Either[ConnectionError | HttpError | DecodingError, A] =
    Raise.either[ConnectionError, HttpResponse] { client.send(request) }
      .left.map(e => e: ConnectionError | HttpError | DecodingError)
      .flatMap { resp =>
        Raise.either[HttpError | DecodingError, A] { resp.as[A] }
          .left.map(e => e: ConnectionError | HttpError | DecodingError)
      }

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
          sendAndDecode[Int](client, HttpRequest.get(baseUrl))
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
          sendAndDecode[String](
            client,
            HttpRequest.post(baseUrl, "payload").header(Headers.Authorization, "Bearer token")
          )
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
          sendAndDecode[String](client, HttpRequest.get(baseUrl))
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
          Raise.either[ConnectionError, HttpResponse] {
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
          sendAndDecode[Int](client, HttpRequest.get(baseUrl))
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
        Raise.either[ConnectionError, HttpResponse] {
          client.send(HttpRequest.get(s"http://localhost:$port"))
        }
      }
    }
    result.get match
      case Left(ConnectionError.ConnectionRefused(_, _)) => succeed
      case other => fail(s"Expected ConnectionRefused, got: $other")
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
          val r1     = sendAndDecode[Int](client, HttpRequest.get(baseUrl))
          val r2     = sendAndDecode[Int](client, HttpRequest.get(baseUrl))
          for { a <- r1; b <- r2 } yield (a, b)
        }
      }
      val (a, b) = result.get.getOrElse(fail("Expected Right"))
      a shouldBe 1
      b shouldBe 2
    finally server.stop(0)
  }
