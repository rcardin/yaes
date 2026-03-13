package in.rcard.yaes.http.client

import in.rcard.yaes.*
import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient => JHttpClient, HttpRequest => JHttpRequest, HttpResponse => JHttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Duration => JDuration}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*

class YaesClient private (private[client] val underlying: JHttpClient):
  def send(request: HttpRequest)(using Sync, Raise[ConnectionError]): HttpResponse =
    val bodyPublisher =
      if request.body.isEmpty then JHttpRequest.BodyPublishers.noBody()
      else JHttpRequest.BodyPublishers.ofByteArray(request.body.getBytes(UTF_8))
    try
      val javaUri = buildUri(request.uri, request.queryParams)
      val jReqBuilder = JHttpRequest.newBuilder()
        .uri(javaUri)
        .method(request.method.toString, bodyPublisher)
      request.headers.foreach((k, v) => jReqBuilder.header(k, v))
      request.timeout.foreach(d =>
        jReqBuilder.timeout(JDuration.ofMillis(d.toMillis))
      )
      val jReq = jReqBuilder.build()
      val jResp = underlying.send(jReq, JHttpResponse.BodyHandlers.ofString())
      val headers = jResp.headers().map().asScala.map { (k, vs) =>
        k.toLowerCase -> vs.asScala.headOption.getOrElse("")
      }.toMap
      HttpResponse(jResp.statusCode(), headers, jResp.body())
    catch
      case e: java.net.ConnectException =>
        Raise.raise(ConnectionError.ConnectionRefused(
          request.uri.host.getOrElse("unknown"),
          request.uri.port
        ))
      case e: java.net.http.HttpConnectTimeoutException =>
        Raise.raise(ConnectionError.ConnectTimeout(
          request.uri.host.getOrElse("unknown")
        ))
      case e: java.net.http.HttpTimeoutException =>
        Raise.raise(ConnectionError.RequestTimeout(request.uri.value))
      case e: Exception =>
        Raise.raise(ConnectionError.Unexpected(e))

  private def buildUri(uri: Uri, queryParams: List[(String, String)]): URI =
    if queryParams.isEmpty then uri.toJavaURI
    else
      val encoded = queryParams.map { (k, v) =>
        URLEncoder.encode(k, UTF_8) + "=" + URLEncoder.encode(v, UTF_8)
      }.mkString("&")
      val base = uri.value
      val separator = if base.contains("?") then "&" else "?"
      URI(base + separator + encoded)

object YaesClient:
  def make(config: YaesClientConfig = YaesClientConfig())(using Resource): YaesClient =
    val builder = JHttpClient.newBuilder()
    config.connectTimeout.foreach(d =>
      builder.connectTimeout(JDuration.ofMillis(d.toMillis))
    )
    builder.followRedirects(toJavaRedirect(config.followRedirects))
    builder.version(toJavaVersion(config.httpVersion))
    val javaClient = builder.build()
    Resource.install(javaClient)(_.close())
    new YaesClient(javaClient)

  private def toJavaRedirect(policy: RedirectPolicy): JHttpClient.Redirect = policy match
    case RedirectPolicy.Never  => JHttpClient.Redirect.NEVER
    case RedirectPolicy.Always => JHttpClient.Redirect.ALWAYS
    case RedirectPolicy.Normal => JHttpClient.Redirect.NORMAL

  private def toJavaVersion(version: HttpVersion): JHttpClient.Version = version match
    case HttpVersion.Http11 => JHttpClient.Version.HTTP_1_1
    case HttpVersion.Http2  => JHttpClient.Version.HTTP_2
