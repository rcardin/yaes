package in.rcard.yaes.http.client

import in.rcard.yaes.http.core.{BodyCodec, Headers, Method}
import java.util.Locale
import scala.concurrent.duration.Duration

/** HTTP request representation for the client.
  *
  * Immutable case class representing an outgoing HTTP request. Use the companion-object factory
  * methods to construct requests and the extension methods for a fluent builder API.
  *
  * Example:
  * {{{
  * import in.rcard.yaes.http.client.HttpRequest.*
  *
  * val req = HttpRequest.get(uri)
  *   .header("Authorization", "Bearer token")
  *   .queryParam("page", "1")
  *   .timeout(30.seconds)
  * }}}
  *
  * @param method      the HTTP method
  * @param uri         the target URI (validated via [[Uri]])
  * @param headers     request headers (keys are lowercase)
  * @param body        the encoded request body
  * @param queryParams query parameters appended to the URI at send time
  * @param timeout     optional per-request timeout
  */
case class HttpRequest(
  method: Method,
  uri: Uri,
  headers: Map[String, String] = Map.empty,
  body: String = "",
  queryParams: List[(String, String)] = List.empty,
  timeout: Option[Duration] = None
)

object HttpRequest:
  /** Creates a GET request. */
  def get(uri: Uri): HttpRequest     = HttpRequest(Method.GET, uri)
  /** Creates a HEAD request. */
  def head(uri: Uri): HttpRequest    = HttpRequest(Method.HEAD, uri)
  /** Creates a DELETE request. */
  def delete(uri: Uri): HttpRequest  = HttpRequest(Method.DELETE, uri)
  /** Creates an OPTIONS request. */
  def options(uri: Uri): HttpRequest = HttpRequest(Method.OPTIONS, uri)

  /** Creates a POST request with an encoded body.
    *
    * @param uri  the target URI
    * @param body the value to encode as the request body
    * @tparam A   the body type (resolved via [[BodyCodec]])
    */
  def post[A](uri: Uri, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.POST, uri, Map(Headers.ContentType -> codec.contentType), codec.encode(body))
  /** Creates a PUT request with an encoded body.
    *
    * @param uri  the target URI
    * @param body the value to encode as the request body
    * @tparam A   the body type (resolved via [[BodyCodec]])
    */
  def put[A](uri: Uri, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.PUT, uri, Map(Headers.ContentType -> codec.contentType), codec.encode(body))
  /** Creates a PATCH request with an encoded body.
    *
    * @param uri  the target URI
    * @param body the value to encode as the request body
    * @tparam A   the body type (resolved via [[BodyCodec]])
    */
  def patch[A](uri: Uri, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.PATCH, uri, Map(Headers.ContentType -> codec.contentType), codec.encode(body))

  extension (req: HttpRequest)
    /** Adds or replaces a header. The key is lowercased for consistency. */
    def header(name: String, value: String): HttpRequest =
      req.copy(headers = req.headers + (name.toLowerCase(Locale.ROOT) -> value))
    /** Appends a query parameter. Duplicate keys are allowed. */
    def queryParam(name: String, value: String): HttpRequest =
      req.copy(queryParams = req.queryParams :+ (name, value))
    /** Sets the per-request timeout, overriding any previous value.
      *
      * Non-finite or non-positive durations clear any previously set timeout (i.e. no timeout).
      */
    def timeout(duration: Duration): HttpRequest =
      if duration.isFinite && duration.toMillis > 0 then req.copy(timeout = Some(duration))
      else req.copy(timeout = None)
