package in.rcard.yaes.http.server


import in.rcard.yaes.*
/** HTTP method enumeration.
  *
  * Represents the standard HTTP methods used in REST APIs.
  */
enum Method:
  case GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS

/** HTTP request representation.
  *
  * Immutable case class representing an incoming HTTP request. This is a simplified model focusing
  * on the most common request properties.
  *
  * Example:
  * {{{
  * val request = Request(
  *   method = Method.GET,
  *   path = "/users/123",
  *   headers = Map("Content-Type" -> "application/json"),
  *   body = "",
  *   queryString = Map("page" -> List("1"), "limit" -> List("20"))
  * )
  * }}}
  *
  * @param method
  *   HTTP method (GET, POST, etc.)
  * @param path
  *   Request path without query parameters
  * @param headers
  *   Request headers as a Map of header name to value
  * @param body
  *   Request body as a String (may be empty for GET requests)
  * @param queryString
  *   Parsed query string as a Map of parameter names to lists of values. Multi-valued parameters
  *   (e.g., ?tag=a&tag=b) are represented as lists with multiple elements.
  */
case class Request(
    method: Method,
    path: String,
    headers: Map[String, String],
    body: String,
    queryString: Map[String, List[String]]
)

object Request {
  /** Extension methods for body decoding */
  extension (req: Request) {
    /** Decode request body using the implicit codec.
      *
      * The codec is resolved automatically from the context using Scala 3's `using` clauses. Decoding
      * failures are raised as typed errors via the `Raise[DecodingError]` effect.
      *
      * Example:
      * {{{
      * // With a custom User codec in scope
      * val user: User raises DecodingError = request.as[User]
      *
      * // In a handler that declares Raise[DecodingError]
      * def handleCreateUser(req: Request): Response raises DecodingError = {
      *   val user = req.as[User]
      *   // ... process user ...
      *   Response.created(user)
      * }
      * }}}
      *
      * @tparam A
      *   The type to decode to
      * @return
      *   The decoded value
      */
    def as[A](using codec: BodyCodec[A]): A raises DecodingError =
      codec.decode(req.body)
  }
}
