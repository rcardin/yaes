package in.rcard.yaes.http.core

/** Standard HTTP header name constants.
  *
  * All values are lowercase to match the case-normalization convention used throughout the
  * HTTP client and server modules.
  */
object Headers:
  val ContentType: String   = "content-type"
  val Authorization: String = "authorization"
  val Accept: String        = "accept"
  val ContentLength: String = "content-length"
  val UserAgent: String     = "user-agent"
  val Host: String          = "host"
