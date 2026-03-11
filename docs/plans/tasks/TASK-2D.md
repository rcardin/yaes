# TASK-2D | HttpResponse

status: [x]
requires: 1B, 2B
verify: `sbt client/testOnly *HttpResponseSpec`
gate: PHASE 2 (`sbt client/test`)

Package: `in.rcard.yaes.http.client`
Dir: `yaes-http/client/src/main/scala/in/rcard/yaes/http/client/`
Test dir: `yaes-http/client/src/test/scala/in/rcard/yaes/http/client/`

## `HttpResponse.scala`

Key design note: `Raise[HttpError | DecodingError]` satisfies both `Raise[HttpError]` and `Raise[DecodingError]` via contravariance of `Raise.Unsafe[-E]`. No special machinery needed.

```scala
package in.rcard.yaes.http.client

import in.rcard.yaes.*
import in.rcard.yaes.http.core.{BodyCodec, DecodingError}

case class HttpResponse(
  status: Int,
  headers: Map[String, String],
  body: String
)

extension (resp: HttpResponse)
  def header(name: String): Option[String] =
    resp.headers.get(name.toLowerCase)

  def as[A](using codec: BodyCodec[A]): A raises (HttpError | DecodingError) =
    if resp.status >= 400 && resp.status < 600 then
      Raise.raise(HttpError.fromStatus(resp.status, resp.body))
    else
      codec.decode(resp.body)
```

## `HttpResponseSpec.scala`

```scala
package in.rcard.yaes.http.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.*
import in.rcard.yaes.http.core.{BodyCodec, DecodingError}

class HttpResponseSpec extends AnyFlatSpec with Matchers:

  "HttpResponse.header" should "find header case-insensitively" in {
    val resp = HttpResponse(200, Map("content-type" -> "application/json"), "")
    resp.header("Content-Type") shouldBe Some("application/json")
    resp.header("content-type") shouldBe Some("application/json")
    resp.header("CONTENT-TYPE") shouldBe Some("application/json")
  }

  it should "return None for missing header" in {
    val resp = HttpResponse(200, Map.empty, "")
    resp.header("X-Missing") shouldBe None
  }

  "HttpResponse.as" should "decode 200 body via BodyCodec" in {
    val resp = HttpResponse(200, Map.empty, "42")
    val result = Raise.either[HttpError | DecodingError] { resp.as[Int] }
    result shouldBe Right(42)
  }

  it should "decode 201 body (any 2xx)" in {
    val resp = HttpResponse(201, Map.empty, "99")
    val result = Raise.either[HttpError | DecodingError] { resp.as[Int] }
    result shouldBe Right(99)
  }

  it should "decode 301 body (3xx passes through to decode, no HttpError)" in {
    val resp = HttpResponse(301, Map.empty, "42")
    val result = Raise.either[HttpError | DecodingError] { resp.as[Int] }
    result shouldBe Right(42)
  }

  it should "raise NotFound for 404" in {
    val resp = HttpResponse(404, Map.empty, "not found")
    val result = Raise.either[HttpError | DecodingError] { resp.as[Int] }
    result shouldBe Left(HttpError.NotFound("not found"))
  }

  it should "raise InternalServerError for 500" in {
    val resp = HttpResponse(500, Map.empty, "fail")
    val result = Raise.either[HttpError | DecodingError] { resp.as[Int] }
    result shouldBe Left(HttpError.InternalServerError("fail"))
  }

  it should "raise OtherClientError for 418" in {
    val resp = HttpResponse(418, Map.empty, "teapot")
    val result = Raise.either[HttpError | DecodingError] { resp.as[Int] }
    result shouldBe Left(HttpError.OtherClientError(418, "teapot"))
  }

  it should "raise DecodingError for invalid body on 200" in {
    val resp = HttpResponse(200, Map.empty, "not-a-number")
    val result = Raise.either[HttpError | DecodingError] { resp.as[Int] }
    result.left.getOrElse(fail()) shouldBe a[DecodingError]
  }

  it should "raise DecodingError for empty body on 200" in {
    val resp = HttpResponse(200, Map.empty, "")
    val result = Raise.either[HttpError | DecodingError] { resp.as[Int] }
    result.left.getOrElse(fail()) shouldBe a[DecodingError]
  }

  it should "raise HttpError for 404 without attempting decode" in {
    val resp = HttpResponse(404, Map.empty, "not valid as int either")
    val result = Raise.either[HttpError | DecodingError] { resp.as[Int] }
    result shouldBe Left(HttpError.NotFound("not valid as int either"))
  }
```
