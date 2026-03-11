package in.rcard.yaes.http.client

import in.rcard.yaes.*
import in.rcard.yaes.http.core.{BodyCodec, DecodingError}

case class HttpResponse(
  status: Int,
  headers: Map[String, String],
  body: String
)

extension (resp: HttpResponse)
  def header(name: String): Option[String] =
    resp.headers.get(name.toLowerCase)

  def as[A](using codec: BodyCodec[A]): A raises (HttpError | DecodingError) =
    if resp.status >= 400 && resp.status < 600 then
      Raise.raise(HttpError.fromStatus(resp.status, resp.body))
    else
      codec.decode(resp.body)
