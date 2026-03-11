# TASK-3B | YaesClient.send

status: [x]
requires: 3A
verify: `sbt client/testOnly *YaesClientSendSpec`
gate: PHASE 3 (`sbt client/test`)

## Edit `YaesClient.scala` — replace `send` stub

Replace `def send(request: HttpRequest): HttpResponse raises ConnectionError = ???` with:

```scala
import java.net.{URI, URLEncoder}
import java.net.http.{HttpRequest => JHttpRequest, HttpResponse => JHttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*

// inside class YaesClient:

def send(request: HttpRequest)(using Sync, Raise[ConnectionError]): HttpResponse =
  val uri = buildUri(request.url, request.queryParams)
  val bodyPublisher =
    if request.body.isEmpty then JHttpRequest.BodyPublishers.noBody()
    else JHttpRequest.BodyPublishers.ofString(request.body)
  val jReqBuilder = JHttpRequest.newBuilder()
    .uri(uri)
    .method(request.method.toString, bodyPublisher)
  request.headers.foreach((k, v) => jReqBuilder.header(k, v))
  request.timeout.foreach(d =>
    jReqBuilder.timeout(JDuration.ofMillis(d.toMillis))
  )
  val jReq = jReqBuilder.build()
  try
    val jResp = underlying.send(jReq, JHttpResponse.BodyHandlers.ofString())
    val headers = jResp.headers().map().asScala.map { (k, vs) =>
      k.toLowerCase -> vs.asScala.headOption.getOrElse("")
    }.toMap
    HttpResponse(jResp.statusCode(), headers, jResp.body())
  catch
    case e: java.net.URISyntaxException =>
      Raise.raise(ConnectionError.MalformedUrl(request.url))
    case e: java.net.ConnectException =>
      val parsedUri = tryParseUri(request.url)
      Raise.raise(ConnectionError.ConnectionRefused(
        parsedUri.map(_.getHost).getOrElse("unknown"),
        parsedUri.map(u => if u.getPort == -1 then 80 else u.getPort).getOrElse(0)
      ))
    case e: java.net.http.HttpConnectTimeoutException =>
      val parsedUri = tryParseUri(request.url)
      Raise.raise(ConnectionError.ConnectTimeout(
        parsedUri.map(_.getHost).getOrElse("unknown")
      ))
    case e: java.net.http.HttpTimeoutException =>
      Raise.raise(ConnectionError.RequestTimeout(request.url))

private def buildUri(url: String, queryParams: List[(String, String)]): URI =
  if queryParams.isEmpty then URI(url)
  else
    val encoded = queryParams.map { (k, v) =>
      URLEncoder.encode(k, UTF_8) + "=" + URLEncoder.encode(v, UTF_8)
    }.mkString("&")
    val separator = if url.contains("?") then "&" else "?"
    URI(url + separator + encoded)

private def tryParseUri(url: String): Option[URI] =
  try Some(URI(url)) catch case _: Exception => None
```

Note: The original stub signature `HttpResponse raises ConnectionError` must be updated to include `Sync` as a using parameter: `(using Sync, Raise[ConnectionError])`.

## `YaesClientSendSpec.scala` (test dir)

```scala
package in.rcard.yaes.http.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.*
import in.rcard.yaes.http.client.HttpRequest.*
import scala.concurrent.duration.*

class YaesClientSendSpec extends AnyFlatSpec with Matchers:

  "YaesClient.send" should "return status, headers, and body for GET" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      exchange.getResponseHeaders.add("X-Custom", "test-value")
      val body = "hello"
      exchange.sendResponseHeaders(200, body.length)
      exchange.getResponseBody.write(body.getBytes)
      exchange.close()
    }
    try
      Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          val resp = Raise.run {
            client.send(HttpRequest.get(baseUrl))
          }
          resp match
            case r: HttpResponse =>
              r.status shouldBe 200
              r.body shouldBe "hello"
              r.header("x-custom") shouldBe Some("test-value")
            case _ => fail("Expected HttpResponse")
        }
      }
    finally server.stop(0)
  }

  it should "send encoded body and Content-Type for POST" in {
    var receivedBody = ""
    var receivedContentType = ""
    val (server, baseUrl) = TestServer.start { exchange =>
      receivedContentType = exchange.getRequestHeaders.getFirst("Content-type")
      receivedBody = new String(exchange.getRequestBody.readAllBytes())
      exchange.sendResponseHeaders(201, receivedBody.length)
      exchange.getResponseBody.write(receivedBody.getBytes)
      exchange.close()
    }
    try
      Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          val resp = Raise.run { client.send(HttpRequest.post(baseUrl, "payload")) }
          resp.asInstanceOf[HttpResponse].status shouldBe 201
          receivedBody shouldBe "payload"
          receivedContentType shouldBe "text/plain"
        }
      }
    finally server.stop(0)
  }

  it should "append URL-encoded query parameters" in {
    var receivedUri = ""
    val (server, baseUrl) = TestServer.start { exchange =>
      receivedUri = exchange.getRequestURI.toString
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    }
    try
      Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          Raise.run {
            client.send(
              HttpRequest.get(baseUrl)
                .queryParam("q", "hello world")
                .queryParam("tag", "a&b")
            )
          }
          receivedUri should include("q=hello")
          receivedUri should include("tag=a%26b")
        }
      }
    finally server.stop(0)
  }

  it should "send custom headers" in {
    var receivedAuth = ""
    val (server, baseUrl) = TestServer.start { exchange =>
      receivedAuth = exchange.getRequestHeaders.getFirst("Authorization")
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    }
    try
      Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          Raise.run {
            client.send(
              HttpRequest.get(baseUrl).header("Authorization", "Bearer tok")
            )
          }
          receivedAuth shouldBe "Bearer tok"
        }
      }
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
      Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          val resp = Raise.run { client.send(HttpRequest.get(baseUrl)) }
          resp match
            case r: HttpResponse =>
              r.status shouldBe 404
              r.body shouldBe "not found"
            case err => fail(s"Expected HttpResponse, got error: $err")
        }
      }
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
      Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          Raise.run {
            val r1 = client.send(HttpRequest.get(baseUrl + "/a"))
            val r2 = client.send(HttpRequest.get(baseUrl + "/b"))
            r1
          }
        }
      }
    finally server.stop(0)
  }

  it should "raise MalformedUrl for invalid URL" in {
    Sync.runBlocking(10.seconds) {
      Resource.run {
        val client = YaesClient.make()
        val result = Raise.either[ConnectionError] {
          client.send(HttpRequest.get(":::bad"))
        }
        result shouldBe Left(ConnectionError.MalformedUrl(":::bad"))
      }
    }
  }

  it should "raise ConnectionRefused when no server is listening" in {
    val port = TestServer.findFreePort()
    Sync.runBlocking(10.seconds) {
      Resource.run {
        val client = YaesClient.make()
        val result = Raise.either[ConnectionError] {
          client.send(HttpRequest.get(s"http://localhost:$port"))
        }
        result match
          case Left(ConnectionError.ConnectionRefused(host, p)) =>
            host shouldBe "localhost"
            p shouldBe port
          case other => fail(s"Expected ConnectionRefused, got: $other")
      }
    }
  }

  it should "raise RequestTimeout when per-request timeout exceeded" in {
    val (server, baseUrl) = TestServer.start { exchange =>
      Thread.sleep(5000)
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    }
    try
      Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          val result = Raise.either[ConnectionError] {
            client.send(HttpRequest.get(baseUrl).timeout(100.millis))
          }
          result match
            case Left(ConnectionError.RequestTimeout(url)) =>
              url shouldBe baseUrl
            case other => fail(s"Expected RequestTimeout, got: $other")
        }
      }
    finally server.stop(0)
  }

  it should "append query params to URL that already has query string" in {
    var receivedUri = ""
    val (server, baseUrl) = TestServer.start { exchange =>
      receivedUri = exchange.getRequestURI.toString
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    }
    try
      Sync.runBlocking(10.seconds) {
        Resource.run {
          val client = YaesClient.make()
          Raise.run {
            client.send(
              HttpRequest.get(baseUrl + "?existing=1").queryParam("new", "2")
            )
          }
          receivedUri should include("existing=1")
          receivedUri should include("new=2")
          receivedUri should include("&")
        }
      }
    finally server.stop(0)
  }
```
