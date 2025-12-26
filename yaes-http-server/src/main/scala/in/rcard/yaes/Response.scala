package in.rcard.yaes

/** HTTP response representation.
  *
  * Immutable case class representing an HTTP response to be sent back to the client.
  *
  * Example:
  * {{{
  * val response = Response(
  *   status = 200,
  *   headers = Map("Content-Type" -> "application/json"),
  *   body = """{"message": "Success"}"""
  * )
  * }}}
  *
  * @param status
  *   HTTP status code (200, 404, 500, etc.)
  * @param headers
  *   Response headers as a Map of header name to value
  * @param body
  *   Response body as a String
  */
case class Response(
    status: Int,
    headers: Map[String, String] = Map.empty,
    body: String = ""
)

object Response {

  /** Creates a 200 OK response with plain text body.
    *
    * Example:
    * {{{
    * Response.ok("Hello, World!")
    * }}}
    *
    * @param body
    *   The response body
    * @return
    *   A Response with status 200 and Content-Type: text/plain
    */
  def ok(body: String): Response =
    Response(200, Map("Content-Type" -> "text/plain"), body)

  /** Creates a 201 Created response with optional body.
    *
    * @param body
    *   The response body (optional)
    * @return
    *   A Response with status 201
    */
  def created(body: String = ""): Response =
    Response(201, Map("Content-Type" -> "text/plain"), body)

  /** Creates a 202 Accepted response with optional body.
    *
    * @param body
    *   The response body (optional)
    * @return
    *   A Response with status 202
    */
  def accepted(body: String = ""): Response =
    Response(202, Map("Content-Type" -> "text/plain"), body)

  /** Creates a 204 No Content response.
    *
    * @return
    *   A Response with status 204 and empty body
    */
  def noContent(): Response =
    Response(204, Map.empty, "")

  /** Creates a 400 Bad Request response.
    *
    * @param message
    *   Error message describing the bad request
    * @return
    *   A Response with status 400
    */
  def badRequest(message: String = "Bad Request"): Response =
    Response(400, Map("Content-Type" -> "text/plain"), message)

  /** Creates a 404 Not Found response.
    *
    * @param message
    *   Error message (optional, defaults to "Not Found")
    * @return
    *   A Response with status 404
    */
  def notFound(message: String = "Not Found"): Response =
    Response(404, Map("Content-Type" -> "text/plain"), message)

  /** Creates a 500 Internal Server Error response.
    *
    * @param message
    *   Error message describing the internal error
    * @return
    *   A Response with status 500
    */
  def internalServerError(message: String = "Internal Server Error"): Response =
    Response(500, Map("Content-Type" -> "text/plain"), message)
}
