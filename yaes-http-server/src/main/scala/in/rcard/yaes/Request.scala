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
    body: String,
    pathParams: Map[String, String] = Map.empty
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

    /** Extract a typed path parameter from the request.
      *
      * Path parameters are extracted from parameterized routes (e.g., "/users/:id") and parsed
      * into the requested type using the PathParamParser typeclass. Parsing failures are raised as
      * typed errors via the `Raise[PathParamError]` effect.
      *
      * Example:
      * {{{
      * // Route: GET /users/:id
      * (Method.GET, "/users/:id", (req: Request) => {
      *   Raise.fold {
      *     val userId = req.pathParam[Int]("id")
      *     Response.ok(s"User $userId")
      *   } {
      *     case PathParamError.InvalidType(name, value, targetType) =>
      *       Response.badRequest(s"Invalid $name: expected $targetType, got '$value'")
      *     case PathParamError.MissingParam(name) =>
      *       Response.internalServerError(s"Server error: missing param $name")
      *   } { response => response }
      * })
      * }}}
      *
      * @tparam A
      *   The type to parse the parameter into
      * @param name
      *   The parameter name (without ':' prefix)
      * @return
      *   The parsed parameter value
      */
    def pathParam[A](name: String)(using parser: PathParamParser[A]): A raises PathParamError = {
      req.pathParams.get(name) match {
        case None        => Raise.raise(PathParamError.MissingParam(name))
        case Some(value) => parser.parse(name, value)
      }
    }
  }
}
