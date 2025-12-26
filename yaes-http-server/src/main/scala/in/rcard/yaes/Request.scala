package in.rcard.yaes

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
  *   body = ""
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
  */
case class Request(
    method: Method,
    path: String,
    headers: Map[String, String],
    body: String
)
