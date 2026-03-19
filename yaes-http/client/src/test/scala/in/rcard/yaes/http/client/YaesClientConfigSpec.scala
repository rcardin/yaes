package in.rcard.yaes.http.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class YaesClientConfigSpec extends AnyFlatSpec with Matchers:

  "YaesClientConfig" should "use default values" in {
    val config = YaesClientConfig()
    config.connectTimeout shouldBe None
    config.followRedirects shouldBe RedirectPolicy.Normal
    config.httpVersion shouldBe HttpVersion.Http11
  }
