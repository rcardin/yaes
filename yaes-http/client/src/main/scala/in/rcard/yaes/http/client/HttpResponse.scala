package in.rcard.yaes.http.client

import in.rcard.yaes.*
import in.rcard.yaes.http.core.{BodyCodec, DecodingError}
import java.util.Locale

/** HTTP response returned by [[YaesClient.send]].
  *
  * Contains the raw status code, headers, and body. Use the [[as]] extension method to decode
  * the body into a typed value; non-2xx responses will raise an [[HttpError]] instead.
  *
  * Example:
  * {{{
  * val resp: HttpResponse = client.send(request)
  * resp.header("content-type")  // Option[String]
  * resp.as[User]                // User raises (HttpError | DecodingError)
  * }}}
  *
  * @param status  the HTTP status code
  * @param headers response headers (keys are lowercase)
  * @param body    the raw response body
  */
case class HttpResponse(
  status: Int,
  headers: Map[String, String],
  body: String
)

extension (resp: HttpResponse)
  /** Looks up a response header by name (case-insensitive).
    *
    * @param name the header name
    * @return the header value, or `None` if absent
    */
  def header(name: String): Option[String] =
    resp.headers.get(name.toLowerCase(Locale.ROOT))

  /** Decodes the response body into a typed value.
    *
    * Raises [[HttpError]] for non-2xx status codes (checked before decoding).
    * Raises [[DecodingError]] if the body cannot be decoded by the codec.
    *
    * @tparam A the target type
    * @return the decoded value
    */
  def as[A](using codec: BodyCodec[A]): A raises (HttpError | DecodingError) =
    if resp.status < 200 || resp.status >= 300 then
      Raise.raise(HttpError.fromStatus(resp.status, resp.body))
    else
      codec.decode(resp.body)
