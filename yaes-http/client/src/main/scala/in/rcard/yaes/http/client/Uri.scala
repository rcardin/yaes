package in.rcard.yaes.http.client

import in.rcard.yaes.*
import java.net.{URI, URISyntaxException, URLEncoder}
import java.nio.charset.StandardCharsets.UTF_8

/** Validated URI wrapper backed by [[java.net.URI]].
  *
  * An opaque type ensuring URIs are syntactically valid at construction time. Invalid input raises
  * [[Uri.InvalidUri]] via the [[in.rcard.yaes.Raise]] effect.
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
    * @param input
    *   the invalid input string
    * @param reason
    *   the parse error message
    */
  case class InvalidUri(input: String, reason: String)

  /** Parses a raw string into a [[Uri]], raising [[InvalidUri]] on failure.
    *
    * @param raw
    *   the URI string to parse
    * @return
    *   the validated URI
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
    def value: String = uri.toString

    /** Returns the host component, if present. */
    def host: Option[String] = Option(uri.getHost)

    /** Returns the port, defaulting to 443 for `https` and 80 for other schemes if not specified. */
    def port: Int =
      if uri.getPort != -1 then uri.getPort
      else if uri.getScheme == "https" then 443
      else 80

    /** Appends query parameters to the URI, URL-encoding keys and values.
      *
      * If the URI already contains a query string, parameters are appended with `&`.
      * Existing fragment components are preserved correctly.
      *
      * @param queryParams the parameters to append
      * @return a [[java.net.URI]] with the appended query string
      */
    def withQueryParams(queryParams: List[(String, String)]): URI =
      if queryParams.isEmpty then uri.toJavaURI
      else
        val encoded = queryParams.iterator.map { (k, v) =>
          s"${URLEncoder.encode(k, UTF_8)}=${URLEncoder.encode(v, UTF_8)}"
        }.mkString("&")
        val raw       = uri.toASCIIString
        val fragIdx   = raw.indexOf('#')
        val (base, fragment) =
          if fragIdx == -1 then (raw, "") else (raw.substring(0, fragIdx), raw.substring(fragIdx))
        val separator = if base.contains('?') then "&" else "?"
        new URI(s"$base$separator$encoded$fragment")
