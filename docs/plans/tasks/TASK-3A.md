# TASK-3A | TestServer + YaesClient.make

status: [x]
requires: 2A, 2B, 2C, 2D
verify: `sbt client/testOnly *YaesClientSpec`

Package: `in.rcard.yaes.http.client`
Main dir: `yaes-http/client/src/main/scala/in/rcard/yaes/http/client/`
Test dir: `yaes-http/client/src/test/scala/in/rcard/yaes/http/client/`

## `TestServer.scala` (test dir)

```scala
package in.rcard.yaes.http.client

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import java.net.InetSocketAddress

object TestServer:
  def findFreePort(): Int =
    val socket = new java.net.ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port

  def start(handler: HttpExchange => Unit): (HttpServer, String) =
    val port = findFreePort()
    val server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/", exchange => {
      handler(exchange)
    })
    server.setExecutor(null)
    server.start()
    (server, s"http://localhost:$port")
```

## `YaesClient.scala` (main dir)

```scala
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
```

## `YaesClientSpec.scala` (test dir)

```scala
package in.rcard.yaes.http.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import in.rcard.yaes.*
import java.net.http.{HttpClient => JHttpClient}
import java.time.{Duration => JDuration}
import scala.concurrent.duration.*

class YaesClientSpec extends AnyFlatSpec with Matchers:

  "YaesClient.make" should "create client with default config" in {
    Resource.run {
      val client = YaesClient.make()
      client.underlying should not be null
    }
  }

  it should "apply connect timeout from config" in {
    Resource.run {
      val client = YaesClient.make(YaesClientConfig(connectTimeout = Some(5.seconds)))
      client.underlying.connectTimeout().isPresent shouldBe true
      client.underlying.connectTimeout().get() shouldBe JDuration.ofSeconds(5)
    }
  }

  it should "apply redirect policy from config" in {
    Resource.run {
      val client = YaesClient.make(YaesClientConfig(followRedirects = RedirectPolicy.Never))
      client.underlying.followRedirects() shouldBe JHttpClient.Redirect.NEVER
    }
  }

  it should "apply HTTP version from config" in {
    Resource.run {
      val client = YaesClient.make(YaesClientConfig(httpVersion = HttpVersion.Http2))
      client.underlying.version() shouldBe JHttpClient.Version.HTTP_2
    }
  }
```
