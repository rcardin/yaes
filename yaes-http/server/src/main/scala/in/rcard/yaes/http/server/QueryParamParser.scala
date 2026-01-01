package in.rcard.yaes.http.server


import in.rcard.yaes.*
/** Typeclass for parsing query parameters from strings to typed values.
  *
  * QueryParamParser provides type-safe conversion of query parameter strings to specific types.
  * It uses the YAES `Raise[QueryParamError]` effect to signal conversion failures.
  *
  * Custom parsers can be defined by implementing this trait:
  * {{{
  * import java.util.UUID
  *
  * given QueryParamParser[UUID] with {
  *   def parse(name: String, values: List[String]): UUID raises QueryParamError = {
  *     values.headOption match {
  *       case None => Raise.raise(QueryParamError.MissingParam(name))
  *       case Some(v) =>
  *         try {
  *           UUID.fromString(v)
  *         } catch {
  *           case _: IllegalArgumentException =>
  *             Raise.raise(QueryParamError.InvalidType(name, v, "UUID"))
  *         }
  *     }
  *   }
  * }
  * }}}
  *
  * @tparam A
  *   The type to parse query parameters into
  */
trait QueryParamParser[A] {
  /** Parse query parameter values into a typed value.
    *
    * @param name
    *   The name of the parameter (for error messages)
    * @param values
    *   The list of string values for this parameter (may be empty for missing params, or have
    *   multiple values for multi-valued params)
    * @return
    *   The parsed typed value, or raises QueryParamError if parsing fails
    */
  def parse(name: String, values: List[String]): A raises QueryParamError
}

object QueryParamParser {
  /** Parser for String parameters.
    *
    * Requires at least one value. Returns the first value if multiple are provided.
    */
  given QueryParamParser[String] with {
    def parse(name: String, values: List[String]): String raises QueryParamError = {
      values.headOption.getOrElse(
        Raise.raise(QueryParamError.MissingParam(name))
      )
    }
  }

  /** Parser for Int parameters.
    *
    * Requires at least one value that can be parsed as an Int. Raises InvalidType error if the
    * string cannot be parsed.
    *
    * Example:
    * {{{
    * // Route: /search?limit=10
    * val limit = query.get("limit")  // uses this parser, returns 10
    * }}}
    */
  given QueryParamParser[Int] with {
    def parse(name: String, values: List[String]): Int raises QueryParamError = {
      values.headOption match {
        case None => Raise.raise(QueryParamError.MissingParam(name))
        case Some(v) =>
          v.toIntOption.getOrElse(
            Raise.raise(QueryParamError.InvalidType(name, v, "Int"))
          )
      }
    }
  }

  /** Parser for Long parameters.
    *
    * Requires at least one value that can be parsed as a Long. Raises InvalidType error if the
    * string cannot be parsed.
    */
  given QueryParamParser[Long] with {
    def parse(name: String, values: List[String]): Long raises QueryParamError = {
      values.headOption match {
        case None => Raise.raise(QueryParamError.MissingParam(name))
        case Some(v) =>
          v.toLongOption.getOrElse(
            Raise.raise(QueryParamError.InvalidType(name, v, "Long"))
          )
      }
    }
  }

  /** Parser for Boolean parameters.
    *
    * Accepts "true"/"false" (case-insensitive) as well as "1"/"0", "yes"/"no", "on"/"off". Raises
    * InvalidType error if the string cannot be parsed.
    */
  given QueryParamParser[Boolean] with {
    def parse(name: String, values: List[String]): Boolean raises QueryParamError = {
      values.headOption match {
        case None => Raise.raise(QueryParamError.MissingParam(name))
        case Some(v) =>
          v.toLowerCase match {
            case "true" | "1" | "yes" | "on"   => true
            case "false" | "0" | "no" | "off"  => false
            case _ => Raise.raise(QueryParamError.InvalidType(name, v, "Boolean"))
          }
      }
    }
  }

  /** Parser for optional parameters.
    *
    * Never raises errors. Returns None if the parameter is missing or cannot be parsed, otherwise
    * returns Some(value).
    *
    * Example:
    * {{{
    * // Route: /users?page=2
    * val page = query.get("page")  // returns Some(2)
    *
    * // Route: /users (no page param)
    * val page = query.get("page")  // returns None
    *
    * // Route: /users?page=abc (invalid Int)
    * val page = query.get("page")  // returns None (parsing failed, but no error raised)
    * }}}
    */
  given [T](using parser: QueryParamParser[T]): QueryParamParser[Option[T]] with {
    def parse(name: String, values: List[String]): Option[T] raises QueryParamError = {
      if (values.isEmpty) {
        None
      } else {
        Raise.fold(parser.parse(name, values))(_ => None)(v => Some(v))
      }
    }
  }

  /** Parser for multi-valued parameters.
    *
    * Parses all values for a parameter and returns them as a list. Returns an empty list if the
    * parameter is not present. Invalid values are skipped (not included in the result).
    *
    * Example:
    * {{{
    * // Route: /filter?tag=scala&tag=fp&tag=web
    * val tags = query.get("tag")  // returns List("scala", "fp", "web")
    *
    * // Route: /filter?tag=scala&tag=123&tag=web (where tag expects String)
    * val tags = query.get("tag")  // returns List("scala", "123", "web")
    *
    * // Route: /filter (no tag param)
    * val tags = query.get("tag")  // returns List()
    * }}}
    */
  given [T](using parser: QueryParamParser[T]): QueryParamParser[List[T]] with {
    def parse(name: String, values: List[String]): List[T] raises QueryParamError = {
      values.flatMap { v =>
        Raise.fold(parser.parse(name, List(v)))(_ => None)(result => Some(result))
      }
    }
  }
}
