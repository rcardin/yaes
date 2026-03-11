# TASK-2B | Error ADTs

status: [x]
requires: 1A
verify: `sbt client/testOnly *HttpErrorSpec`

Package: `in.rcard.yaes.http.client`
Dir: `yaes-http/client/src/main/scala/in/rcard/yaes/http/client/`
Test dir: `yaes-http/client/src/test/scala/in/rcard/yaes/http/client/`

## `ConnectionError.scala`

```scala
package in.rcard.yaes.http.client

sealed trait ConnectionError

object ConnectionError:
  case class MalformedUrl(url: String) extends ConnectionError
  case class ConnectionRefused(host: String, port: Int) extends ConnectionError
  case class ConnectTimeout(host: String) extends ConnectionError
  case class RequestTimeout(url: String) extends ConnectionError
```

No tests — tested via YaesClient.send in phase 3.

## `HttpError.scala`

```scala
package in.rcard.yaes.http.client

sealed trait HttpError:
  def status: Int
  def body: String

sealed trait ClientHttpError extends HttpError
sealed trait ServerHttpError extends HttpError

object HttpError:
  case class BadRequest(body: String) extends ClientHttpError          { val status = 400 }
  case class Unauthorized(body: String) extends ClientHttpError        { val status = 401 }
  case class Forbidden(body: String) extends ClientHttpError           { val status = 403 }
  case class NotFound(body: String) extends ClientHttpError            { val status = 404 }
  case class MethodNotAllowed(body: String) extends ClientHttpError    { val status = 405 }
  case class Conflict(body: String) extends ClientHttpError            { val status = 409 }
  case class Gone(body: String) extends ClientHttpError                { val status = 410 }
  case class UnprocessableEntity(body: String) extends ClientHttpError { val status = 422 }
  case class TooManyRequests(body: String) extends ClientHttpError     { val status = 429 }
  case class OtherClientError(status: Int, body: String) extends ClientHttpError

  case class InternalServerError(body: String) extends ServerHttpError   { val status = 500 }
  case class BadGateway(body: String) extends ServerHttpError            { val status = 502 }
  case class ServiceUnavailable(body: String) extends ServerHttpError    { val status = 503 }
  case class GatewayTimeout(body: String) extends ServerHttpError        { val status = 504 }
  case class OtherServerError(status: Int, body: String) extends ServerHttpError

  def fromStatus(status: Int, body: String): HttpError = status match
    case 400 => BadRequest(body)
    case 401 => Unauthorized(body)
    case 403 => Forbidden(body)
    case 404 => NotFound(body)
    case 405 => MethodNotAllowed(body)
    case 409 => Conflict(body)
    case 410 => Gone(body)
    case 422 => UnprocessableEntity(body)
    case 429 => TooManyRequests(body)
    case s if s >= 400 && s < 500 => OtherClientError(s, body)
    case 500 => InternalServerError(body)
    case 502 => BadGateway(body)
    case 503 => ServiceUnavailable(body)
    case 504 => GatewayTimeout(body)
    case s if s >= 500 && s < 600 => OtherServerError(s, body)
```

## `HttpErrorSpec.scala`

```scala
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
```
