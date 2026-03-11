# TASK-2C | HttpRequest

status: [x]
requires: 1B
verify: `sbt client/testOnly *HttpRequestSpec`

Package: `in.rcard.yaes.http.client`
Dir: `yaes-http/client/src/main/scala/in/rcard/yaes/http/client/`
Test dir: `yaes-http/client/src/test/scala/in/rcard/yaes/http/client/`

## `HttpRequest.scala`

```scala
package in.rcard.yaes.http.client

import in.rcard.yaes.http.core.{BodyCodec, Headers, Method}
import scala.concurrent.duration.Duration

case class HttpRequest(
  method: Method,
  url: String,
  headers: Map[String, String] = Map.empty,
  body: String = "",
  queryParams: List[(String, String)] = List.empty,
  timeout: Option[Duration] = None
)

object HttpRequest:
  def get(url: String): HttpRequest     = HttpRequest(Method.GET, url)
  def head(url: String): HttpRequest    = HttpRequest(Method.HEAD, url)
  def delete(url: String): HttpRequest  = HttpRequest(Method.DELETE, url)
  def options(url: String): HttpRequest = HttpRequest(Method.OPTIONS, url)

  def post[A](url: String, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.POST, url, Map(Headers.ContentType -> codec.contentType), codec.encode(body))
  def put[A](url: String, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.PUT, url, Map(Headers.ContentType -> codec.contentType), codec.encode(body))
  def patch[A](url: String, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.PATCH, url, Map(Headers.ContentType -> codec.contentType), codec.encode(body))

  extension (req: HttpRequest)
    def header(name: String, value: String): HttpRequest =
      req.copy(headers = req.headers + (name.toLowerCase -> value))
    def queryParam(name: String, value: String): HttpRequest =
      req.copy(queryParams = req.queryParams :+ (name, value))
    def timeout(duration: Duration): HttpRequest =
      req.copy(timeout = Some(duration))
```

## `HttpRequestSpec.scala`

```scala
package in.rcard.yaes.http.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.http.core.{BodyCodec, Headers, Method}
import in.rcard.yaes.http.client.HttpRequest.*
import scala.concurrent.duration.*

class HttpRequestSpec extends AnyFlatSpec with Matchers:

  "HttpRequest.get" should "create GET with empty body and headers" in {
    val req = HttpRequest.get("http://example.com")
    req.method shouldBe Method.GET
    req.url shouldBe "http://example.com"
    req.headers shouldBe Map.empty
    req.body shouldBe ""
    req.queryParams shouldBe List.empty
    req.timeout shouldBe None
  }

  "HttpRequest.head" should "create HEAD request" in {
    HttpRequest.head("http://example.com").method shouldBe Method.HEAD
  }

  "HttpRequest.delete" should "create DELETE request" in {
    HttpRequest.delete("http://example.com").method shouldBe Method.DELETE
  }

  "HttpRequest.options" should "create OPTIONS request" in {
    HttpRequest.options("http://example.com").method shouldBe Method.OPTIONS
  }

  "HttpRequest.post" should "encode body and set Content-Type from codec" in {
    val req = HttpRequest.post("http://example.com", "hello")
    req.method shouldBe Method.POST
    req.body shouldBe "hello"
    req.headers(Headers.ContentType) shouldBe "text/plain"
  }

  "HttpRequest.put" should "encode body and set Content-Type" in {
    val req = HttpRequest.put("http://example.com", 42)
    req.method shouldBe Method.PUT
    req.body shouldBe "42"
    req.headers(Headers.ContentType) shouldBe "text/plain"
  }

  "HttpRequest.patch" should "encode body and set Content-Type" in {
    val req = HttpRequest.patch("http://example.com", "data")
    req.method shouldBe Method.PATCH
    req.body shouldBe "data"
    req.headers(Headers.ContentType) shouldBe "text/plain"
  }

  "header" should "add header with lowercase key" in {
    val req = HttpRequest.get("http://example.com").header("X-Custom", "val1")
    req.headers shouldBe Map("x-custom" -> "val1")
  }

  it should "replace existing header with same name (last-write-wins)" in {
    val req = HttpRequest.get("http://example.com")
      .header("Authorization", "old")
      .header("Authorization", "new")
    req.headers("authorization") shouldBe "new"
  }

  it should "allow overriding Content-Type set by codec" in {
    val req = HttpRequest.post("http://example.com", "body")
      .header(Headers.ContentType, "text/xml")
    req.headers(Headers.ContentType) shouldBe "text/xml"
  }

  "queryParam" should "append a query parameter" in {
    val req = HttpRequest.get("http://example.com").queryParam("key", "value")
    req.queryParams shouldBe List(("key", "value"))
  }

  it should "allow duplicate keys" in {
    val req = HttpRequest.get("http://example.com")
      .queryParam("tag", "a")
      .queryParam("tag", "b")
    req.queryParams shouldBe List(("tag", "a"), ("tag", "b"))
  }

  "timeout" should "set per-request timeout" in {
    val req = HttpRequest.get("http://example.com").timeout(30.seconds)
    req.timeout shouldBe Some(30.seconds)
  }

  it should "replace previous timeout" in {
    val req = HttpRequest.get("http://example.com")
      .timeout(30.seconds)
      .timeout(10.seconds)
    req.timeout shouldBe Some(10.seconds)
  }

  "builder methods" should "not modify original request" in {
    val original = HttpRequest.get("http://example.com")
    val modified = original.header("X-A", "1")
    original.headers shouldBe Map.empty
    modified.headers shouldBe Map("x-a" -> "1")
  }

  "HttpRequest constructor" should "allow body on GET" in {
    val req = HttpRequest(method = Method.GET, url = "http://example.com", body = "data")
    req.body shouldBe "data"
  }
```
