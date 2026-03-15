package in.rcard.yaes.http.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.*
import in.rcard.yaes.http.client.Uri.InvalidUri

class UriWithQueryParamsSpec extends AnyFlatSpec with Matchers:

  private def uri(raw: String): Uri =
    Raise.either[InvalidUri, Uri] { Uri(raw) } match
      case Left(e)  => fail(s"Invalid URI: ${e.reason}")
      case Right(u) => u

  // -- Special characters --

  "withQueryParams" should "encode an empty value" in {
    val result = uri("http://host/path").withQueryParams(List("key" -> ""))
    result.getRawQuery shouldBe "key="
  }

  it should "encode equals sign in value" in {
    val result = uri("http://host/path").withQueryParams(List("expr" -> "a=b"))
    result.getRawQuery shouldBe "expr=a%3Db"
  }

  it should "encode unicode characters" in {
    val result = uri("http://host/path").withQueryParams(List("name" -> "café"))
    result.getRawQuery shouldBe "name=caf%C3%A9"
  }

  it should "encode spaces as plus" in {
    val result = uri("http://host/path").withQueryParams(List("q" -> "a b"))
    result.getRawQuery shouldBe "q=a+b"
  }

  // -- Structural edge cases --

  it should "preserve fragment when adding query params" in {
    val result = uri("http://host/path#frag").withQueryParams(List("key" -> "val"))
    result.getRawQuery shouldBe "key=val"
    result.getFragment shouldBe "frag"
  }

  it should "preserve fragment when appending to existing query" in {
    val result = uri("http://host/path?a=1#frag").withQueryParams(List("key" -> "val"))
    result.getRawQuery shouldBe "a=1&key=val"
    result.getFragment shouldBe "frag"
  }

  it should "return original URI for empty query params list" in {
    val original = uri("http://host/path?existing=1")
    val result   = original.withQueryParams(List.empty)
    result shouldBe original.toJavaURI
  }

  // -- Boundary conditions --

  it should "preserve duplicate keys" in {
    val result = uri("http://host/path").withQueryParams(List("k" -> "1", "k" -> "2"))
    result.getRawQuery shouldBe "k=1&k=2"
  }

  it should "not double-encode pre-encoded percent sequences in existing query" in {
    val result = uri("http://host/path?x=a%26b").withQueryParams(List("y" -> "1"))
    result.getRawQuery shouldBe "x=a%26b&y=1"
  }
