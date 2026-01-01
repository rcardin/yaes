package in.rcard.yaes.http.server

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

  /** Creates a 200 OK response with encoded body and automatic Content-Type.
    *
    * The codec is resolved automatically from the context using Scala 3's `using` clauses. The
    * Content-Type header is set based on the codec's specification.
    *
    * Example:
    * {{{
    * Response.ok("Hello, World!")  // Uses BodyCodec[String] for text/plain
    * Response.ok(user)              // Uses BodyCodec[User] with application/json
    * Response.ok(42)                // Uses BodyCodec[Int] for text/plain
    * }}}
    *
    * @param value
    *   The value to encode as the response body
    * @tparam A
    *   The type of the value
    * @return
    *   A Response with status 200 and appropriate Content-Type
    */
  def ok[A](value: A)(using codec: BodyCodec[A]): Response =
    Response(
      status = 200,
      headers = Map("Content-Type" -> codec.contentType),
      body = codec.encode(value)
    )

  /** Creates a 201 Created response with encoded body.
    *
    * @param value
    *   The value to encode as the response body
    * @tparam A
    *   The type of the value
    * @return
    *   A Response with status 201 and appropriate Content-Type
    */
  def created[A](value: A)(using codec: BodyCodec[A]): Response =
    Response(
      status = 201,
      headers = Map("Content-Type" -> codec.contentType),
      body = codec.encode(value)
    )

  /** Creates a 202 Accepted response with encoded body.
    *
    * @param value
    *   The value to encode as the response body
    * @tparam A
    *   The type of the value
    * @return
    *   A Response with status 202 and appropriate Content-Type
    */
  def accepted[A](value: A)(using codec: BodyCodec[A]): Response =
    Response(
      status = 202,
      headers = Map("Content-Type" -> codec.contentType),
      body = codec.encode(value)
    )

  /** Creates a 204 No Content response.
    *
    * @return
    *   A Response with status 204 and empty body
    */
  def noContent(): Response =
    Response(status = 204)

  /** Creates a 400 Bad Request response.
    *
    * @param value
    *   The value to encode as the error message
    * @tparam A
    *   The type of the value
    * @return
    *   A Response with status 400 and appropriate Content-Type
    */
  def badRequest[A](value: A)(using codec: BodyCodec[A]): Response =
    Response(
      status = 400,
      headers = Map("Content-Type" -> codec.contentType),
      body = codec.encode(value)
    )

  /** Creates a 404 Not Found response.
    *
    * @param value
    *   The value to encode as the error message
    * @tparam A
    *   The type of the value
    * @return
    *   A Response with status 404 and appropriate Content-Type
    */
  def notFound[A](value: A)(using codec: BodyCodec[A]): Response =
    Response(
      status = 404,
      headers = Map("Content-Type" -> codec.contentType),
      body = codec.encode(value)
    )

  /** Creates a 500 Internal Server Error response.
    *
    * @param value
    *   The value to encode as the error message
    * @tparam A
    *   The type of the value
    * @return
    *   A Response with status 500 and appropriate Content-Type
    */
  def internalServerError[A](value: A)(using codec: BodyCodec[A]): Response =
    Response(
      status = 500,
      headers = Map("Content-Type" -> codec.contentType),
      body = codec.encode(value)
    )
}
