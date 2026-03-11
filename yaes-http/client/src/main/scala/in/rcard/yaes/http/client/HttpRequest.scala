package in.rcard.yaes.http.client

import in.rcard.yaes.http.core.{BodyCodec, Headers, Method}
import scala.concurrent.duration.Duration

case class HttpRequest(
  method: Method,
  url: String, // FIXME I don't like to use a String, since we need to parse it later. Maybe we can use java.net.URI or something else?
  headers: Map[String, String] = Map.empty,
  body: String = "",
  queryParams: List[(String, String)] = List.empty,
  timeout: Option[Duration] = None
)

object HttpRequest:
  def get(url: String): HttpRequest     = HttpRequest(Method.GET, url)
  def head(url: String): HttpRequest    = HttpRequest(Method.HEAD, url)
  def delete(url: String): HttpRequest  = HttpRequest(Method.DELETE, url)
  def options(url: String): HttpRequest = HttpRequest(Method.OPTIONS, url)

  def post[A](url: String, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.POST, url, Map(Headers.ContentType -> codec.contentType), codec.encode(body))
  def put[A](url: String, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.PUT, url, Map(Headers.ContentType -> codec.contentType), codec.encode(body))
  def patch[A](url: String, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.PATCH, url, Map(Headers.ContentType -> codec.contentType), codec.encode(body))

  extension (req: HttpRequest)
    def header(name: String, value: String): HttpRequest =
      req.copy(headers = req.headers + (name.toLowerCase -> value))
    def queryParam(name: String, value: String): HttpRequest =
      req.copy(queryParams = req.queryParams :+ (name, value))
    def timeout(duration: Duration): HttpRequest =
      req.copy(timeout = Some(duration))
