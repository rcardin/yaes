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
