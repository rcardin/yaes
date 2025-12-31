package in.rcard.yaes

/** Errors that can occur during query parameter parsing and validation.
  *
  * Query parameter errors are raised during route matching and are typically converted to 400 Bad
  * Request responses. These errors represent invalid client input.
  */
sealed trait QueryParamError extends Throwable {
  def message: String
  override def getMessage: String = message
}

object QueryParamError {

  /** A required query parameter was not provided in the request.
    *
    * Example:
    * {{{
    * // Route expects: /search?q=term
    * // Request: /search (missing 'q')
    * // Error: MissingParam("q")
    * }}}
    *
    * @param name
    *   The name of the missing parameter
    */
  case class MissingParam(name: String) extends QueryParamError {
    def message: String = s"Missing required query parameter: $name"
  }

  /** A query parameter value could not be parsed to the expected type.
    *
    * Example:
    * {{{
    * // Route expects: /search?limit=<Int>
    * // Request: /search?limit=abc
    * // Error: InvalidType("limit", "abc", "Int")
    * }}}
    *
    * @param name
    *   The name of the parameter
    * @param value
    *   The string value that failed to parse
    * @param targetType
    *   The expected type (for error messages)
    */
  case class InvalidType(name: String, value: String, targetType: String) extends QueryParamError {
    def message: String = s"Invalid query parameter '$name': expected $targetType, got '$value'"
  }

  /** The query string itself is malformed and cannot be parsed.
    *
    * This is rare as most malformed query strings can be parsed with some interpretation. This
    * error is reserved for truly unparseable input.
    *
    * @param msg
    *   Description of the parsing error
    */
  case class InvalidFormat(msg: String) extends QueryParamError {
    def message: String = s"Invalid query string format: $msg"
  }
}
