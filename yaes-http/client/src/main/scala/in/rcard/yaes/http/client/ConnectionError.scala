package in.rcard.yaes.http.client

sealed trait ConnectionError

object ConnectionError:
  case class MalformedUrl(url: String) extends ConnectionError
  case class ConnectionRefused(host: String, port: Int) extends ConnectionError
  case class ConnectTimeout(host: String) extends ConnectionError
  case class RequestTimeout(url: String) extends ConnectionError
