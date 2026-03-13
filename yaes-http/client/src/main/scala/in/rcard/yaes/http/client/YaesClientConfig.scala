package in.rcard.yaes.http.client

import scala.concurrent.duration.Duration

/** Configuration for [[YaesClient]].
  *
  * Example:
  * {{{
  * val config = YaesClientConfig(
  *   connectTimeout = Some(5.seconds),
  *   followRedirects = RedirectPolicy.Never,
  *   httpVersion = HttpVersion.Http2
  * )
  * val client = YaesClient.make(config)
  * }}}
  *
  * @param connectTimeout  maximum time to establish a TCP connection
  * @param followRedirects redirect-following policy (default: [[RedirectPolicy.Normal]])
  * @param httpVersion     HTTP protocol version (default: [[HttpVersion.Http11]])
  */
case class YaesClientConfig(
  connectTimeout: Option[Duration] = None,
  followRedirects: RedirectPolicy = RedirectPolicy.Normal,
  httpVersion: HttpVersion = HttpVersion.Http11
)
