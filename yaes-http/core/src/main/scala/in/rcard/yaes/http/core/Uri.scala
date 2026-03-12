package in.rcard.yaes.http.core

import in.rcard.yaes.*
import java.net.URISyntaxException

opaque type Uri = java.net.URI

object Uri:

  case class InvalidUri(input: String, reason: String)

  def apply(raw: String): Uri raises InvalidUri =
    try new java.net.URI(raw)
    catch
      case e: URISyntaxException =>
        Raise.raise(InvalidUri(raw, e.getMessage))

  extension (uri: Uri)
    def toJavaURI: java.net.URI = uri
    def value: String           = uri.toString
    def host: Option[String]    = Option(uri.getHost)
    def port: Int               = if uri.getPort == -1 then 80 else uri.getPort
