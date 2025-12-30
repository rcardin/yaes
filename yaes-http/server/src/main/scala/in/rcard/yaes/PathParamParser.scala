package in.rcard.yaes

/** Typeclass for parsing path parameters from strings to typed values.
  *
  * PathParamParser provides type-safe conversion of path parameter strings to specific types. It
  * uses the YAES `Raise[PathParamError]` effect to signal conversion failures.
  *
  * Custom parsers can be defined by implementing this trait:
  * {{{
  * given PathParamParser[UUID] with {
  *   def parse(name: String, value: String): UUID raises PathParamError = {
  *     try {
  *       UUID.fromString(value)
  *     } catch {
  *       case _: IllegalArgumentException =>
  *         Raise.raise(PathParamError.InvalidType(name, value, "UUID"))
  *     }
  *   }
  * }
  * }}}
  *
  * @tparam A
  *   The type to parse path parameters into
  */
trait PathParamParser[A] {
  /** Parse a path parameter string into a typed value.
    *
    * @param name
    *   The name of the parameter (for error messages)
    *   @param value
    *     The string value extracted from the path
    *     @return
    *       The parsed typed value, or raises PathParamError if parsing fails
    */
  def parse(name: String, value: String): A raises PathParamError
}

object PathParamParser {
  /** Parser for String parameters (identity function).
    *
    * String parameters never fail to parse since they're already strings.
    */
  given PathParamParser[String] with {
    def parse(name: String, value: String): String raises PathParamError = value
  }

  /** Parser for Int parameters.
    *
    * Raises InvalidType error if the string cannot be parsed as an Int.
    *
    * Example:
    * {{{
    * // Route: /users/:id
    * val userId = req.pathParam[Int]("id")  // uses this parser
    * }}}
    */
  given PathParamParser[Int] with {
    def parse(name: String, value: String): Int raises PathParamError = {
      value.toIntOption match {
        case Some(i) => i
        case None    => Raise.raise(PathParamError.InvalidType(name, value, "Int"))
      }
    }
  }

  /** Parser for Long parameters.
    *
    * Raises InvalidType error if the string cannot be parsed as a Long.
    *
    * Example:
    * {{{
    * // Route: /items/:id
    * val itemId = req.pathParam[Long]("id")  // uses this parser
    * }}}
    */
  given PathParamParser[Long] with {
    def parse(name: String, value: String): Long raises PathParamError = {
      value.toLongOption match {
        case Some(l) => l
        case None    => Raise.raise(PathParamError.InvalidType(name, value, "Long"))
      }
    }
  }
}
