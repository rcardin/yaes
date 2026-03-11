package in.rcard.yaes.http.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HttpErrorSpec extends AnyFlatSpec with Matchers:

  "HttpError.fromStatus" should "map 400 to BadRequest" in {
    HttpError.fromStatus(400, "bad") shouldBe HttpError.BadRequest("bad")
    HttpError.fromStatus(400, "bad").status shouldBe 400
  }

  it should "map 401 to Unauthorized" in {
    HttpError.fromStatus(401, "denied") shouldBe HttpError.Unauthorized("denied")
  }

  it should "map 404 to NotFound" in {
    HttpError.fromStatus(404, "gone") shouldBe HttpError.NotFound("gone")
  }

  it should "map unknown 4xx to OtherClientError" in {
    HttpError.fromStatus(418, "teapot") shouldBe HttpError.OtherClientError(418, "teapot")
  }

  it should "map 500 to InternalServerError" in {
    HttpError.fromStatus(500, "fail") shouldBe HttpError.InternalServerError("fail")
    HttpError.fromStatus(500, "fail").status shouldBe 500
  }

  it should "map 502 to BadGateway" in {
    HttpError.fromStatus(502, "bad gw") shouldBe HttpError.BadGateway("bad gw")
  }

  it should "map unknown 5xx to OtherServerError" in {
    HttpError.fromStatus(599, "unknown") shouldBe HttpError.OtherServerError(599, "unknown")
  }

  it should "classify 4xx as ClientHttpError" in {
    HttpError.fromStatus(400, "") shouldBe a[ClientHttpError]
    HttpError.fromStatus(418, "") shouldBe a[ClientHttpError]
  }

  it should "classify 5xx as ServerHttpError" in {
    HttpError.fromStatus(500, "") shouldBe a[ServerHttpError]
    HttpError.fromStatus(599, "") shouldBe a[ServerHttpError]
  }

  it should "map non-4xx/5xx to UnexpectedStatus" in {
    HttpError.fromStatus(200, "ok") shouldBe HttpError.UnexpectedStatus(200, "ok")
    HttpError.fromStatus(301, "moved") shouldBe HttpError.UnexpectedStatus(301, "moved")
    HttpError.fromStatus(100, "continue") shouldBe HttpError.UnexpectedStatus(100, "continue")
  }
