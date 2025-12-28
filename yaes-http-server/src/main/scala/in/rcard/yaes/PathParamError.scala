package in.rcard.yaes

/** Represents errors that occur during path parameter extraction.
  *
  * PathParamError is a sealed trait representing errors as values (not exceptions), designed to work
  * with the YAES `Raise[E]` effect for typed error handling.
  */
sealed trait PathParamError {
  def message: String
}

object PathParamError {
  /** Error indicating a required path parameter is missing.
    *
    * This should rarely occur if routing is working correctly, as the router only matches paths
    * with the correct structure.
    *
    * @param name
    *   The name of the missing parameter
    */
  case class MissingParam(name: String) extends PathParamError {
    def message: String = s"Missing required path parameter: $name"
  }

  /** Error converting a path parameter to the target type.
    *
    * This occurs when a path parameter cannot be parsed as the requested type (e.g., trying to
    * parse "abc" as an Int).
    *
    * Example:
    * {{{
    * // Route: /users/:id
    * // Request: /users/abc
    * val userId = req.pathParam[Int]("id")
    * // Raises: InvalidType("id", "abc", "Int")
    * }}}
    *
    * @param name
    *   The name of the parameter
    * @param value
    *   The actual string value from the path
    * @param targetType
    *   The type that was requested for conversion
    */
  case class InvalidType(name: String, value: String, targetType: String)
      extends PathParamError {
    def message: String =
      s"Cannot convert path parameter '$name' with value '$value' to $targetType"
  }
}
