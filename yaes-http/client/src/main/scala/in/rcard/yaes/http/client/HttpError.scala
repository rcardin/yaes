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

  case class UnexpectedStatus(status: Int, body: String) extends HttpError

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
    case s => UnexpectedStatus(s, body)
