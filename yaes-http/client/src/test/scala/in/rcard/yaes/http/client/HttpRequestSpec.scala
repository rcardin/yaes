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
    req.headers(Headers.ContentType) shouldBe "text/plain; charset=UTF-8"
  }

  "HttpRequest.put" should "encode body and set Content-Type" in {
    val req = HttpRequest.put("http://example.com", 42)
    req.method shouldBe Method.PUT
    req.body shouldBe "42"
    req.headers(Headers.ContentType) shouldBe "text/plain; charset=UTF-8"
  }

  "HttpRequest.patch" should "encode body and set Content-Type" in {
    val req = HttpRequest.patch("http://example.com", "data")
    req.method shouldBe Method.PATCH
    req.body shouldBe "data"
    req.headers(Headers.ContentType) shouldBe "text/plain; charset=UTF-8"
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
