package in.rcard.yaes.http.client

import in.rcard.yaes.http.core.{BodyCodec, Headers, Method}
import scala.concurrent.duration.Duration

case class HttpRequest(
  method: Method,
  uri: Uri,
  headers: Map[String, String] = Map.empty,
  body: String = "", // FIXME Isn't it better to use a byte array here?
  queryParams: List[(String, String)] = List.empty,
  timeout: Option[Duration] = None
)

object HttpRequest:
  def get(uri: Uri): HttpRequest     = HttpRequest(Method.GET, uri)
  def head(uri: Uri): HttpRequest    = HttpRequest(Method.HEAD, uri)
  def delete(uri: Uri): HttpRequest  = HttpRequest(Method.DELETE, uri)
  def options(uri: Uri): HttpRequest = HttpRequest(Method.OPTIONS, uri)

  def post[A](uri: Uri, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.POST, uri, Map(Headers.ContentType -> codec.contentType), codec.encode(body))
  def put[A](uri: Uri, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.PUT, uri, Map(Headers.ContentType -> codec.contentType), codec.encode(body))
  def patch[A](uri: Uri, body: A)(using codec: BodyCodec[A]): HttpRequest =
    HttpRequest(Method.PATCH, uri, Map(Headers.ContentType -> codec.contentType), codec.encode(body))

  extension (req: HttpRequest)
    def header(name: String, value: String): HttpRequest =
      req.copy(headers = req.headers + (name.toLowerCase -> value))
    def queryParam(name: String, value: String): HttpRequest =
      req.copy(queryParams = req.queryParams :+ (name, value))
    def timeout(duration: Duration): HttpRequest =
      req.copy(timeout = Some(duration))
