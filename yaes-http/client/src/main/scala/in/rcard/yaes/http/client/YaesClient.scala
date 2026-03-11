package in.rcard.yaes.http.client

import in.rcard.yaes.*
import java.net.http.{HttpClient => JHttpClient}
import java.time.{Duration => JDuration}
import scala.concurrent.duration.Duration

class YaesClient private (val underlying: JHttpClient):
  def send(request: HttpRequest): HttpResponse raises ConnectionError = ???

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
