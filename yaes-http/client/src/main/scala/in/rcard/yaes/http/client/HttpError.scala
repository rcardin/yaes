package in.rcard.yaes.http.client

/** HTTP-level errors derived from non-2xx response status codes.
  *
  * Raised by [[HttpResponse.as]] when the response status code is outside the 2xx range. The error
  * hierarchy distinguishes client errors (4xx) from server errors (5xx) via the [[ClientHttpError]]
  * and [[ServerHttpError]] subtypes. Status codes outside 4xx/5xx are mapped to [[HttpError.UnexpectedStatus]].
  *
  * Example:
  * {{{
  * val result = Raise.either[HttpError | DecodingError, User] {
  *   response.as[User]
  * }
  * result match
  *   case Left(_: ClientHttpError) => // 4xx
  *   case Left(_: ServerHttpError) => // 5xx
  *   case Right(user)              => // decoded successfully
  * }}}
  */
sealed trait HttpError:
  /** The HTTP status code. */
  def status: Int
  /** The raw response body. */
  def body: String

/** Marker trait for 4xx client errors. */
sealed trait ClientHttpError extends HttpError
/** Marker trait for 5xx server errors. */
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

  /** Catch-all for status codes outside 4xx and 5xx (e.g. 1xx, 3xx). */
  case class UnexpectedStatus(status: Int, body: String) extends HttpError

  /** Maps an HTTP status code to the corresponding [[HttpError]] subtype.
    *
    * @param status the HTTP status code
    * @param body   the raw response body
    * @return the typed error
    */
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
