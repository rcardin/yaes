package in.rcard.yaes.http.server


import in.rcard.yaes.*
/** Represents errors that occur during body decoding.
  *
  * DecodingError is a sealed trait representing errors as values (not exceptions),
  * designed to work with the YAES `Raise[E]` effect for typed error handling.
  */
sealed trait DecodingError {
  def message: String
}

object DecodingError {
  /** Error parsing the body format (e.g., invalid JSON syntax).
    *
    * @param message Description of the parsing error
    * @param cause Optional underlying exception that caused the error
    */
  case class ParseError(message: String, cause: Option[Throwable] = None)
      extends DecodingError

  /** Error validating decoded value (e.g., missing required field).
    *
    * @param message Description of the validation error
    */
  case class ValidationError(message: String) extends DecodingError
}
