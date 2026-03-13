package in.rcard.yaes.http.client

import in.rcard.yaes.*
import java.net.URISyntaxException

/** Validated URI wrapper backed by [[java.net.URI]].
  *
  * An opaque type ensuring URIs are syntactically valid at construction time. Invalid input
  * raises [[Uri.InvalidUri]] via the [[in.rcard.yaes.Raise]] effect.
  *
  * Example:
  * {{{
  * val uri: Uri raises Uri.InvalidUri = Uri("https://example.com/api")
  * }}}
  */
opaque type Uri = java.net.URI

object Uri:

  /** Raised when a raw string cannot be parsed as a valid URI.
    *
    * @param input  the invalid input string
    * @param reason the parse error message
    */
  case class InvalidUri(input: String, reason: String)

  /** Parses a raw string into a [[Uri]], raising [[InvalidUri]] on failure.
    *
    * @param raw the URI string to parse
    * @return the validated URI
    */
  def apply(raw: String): Uri raises InvalidUri =
    try new java.net.URI(raw)
    catch
      case e: URISyntaxException =>
        Raise.raise(InvalidUri(raw, e.getMessage))

  extension (uri: Uri)
    /** Returns the underlying [[java.net.URI]]. */
    def toJavaURI: java.net.URI = uri
    /** Returns the URI as a string. */
    def value: String           = uri.toString
    /** Returns the host component, if present. */
    def host: Option[String]    = Option(uri.getHost)
    /** Returns the port, defaulting to 80 if not specified. */
    def port: Int               = if uri.getPort == -1 then 80 else uri.getPort
