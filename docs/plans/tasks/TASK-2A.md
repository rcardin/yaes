# TASK-2A | Enums + config

status: [x]
requires: 1A
verify: `sbt client/testOnly *YaesClientConfigSpec`

Package: `in.rcard.yaes.http.client`
Dir: `yaes-http/client/src/main/scala/in/rcard/yaes/http/client/`
Test dir: `yaes-http/client/src/test/scala/in/rcard/yaes/http/client/`

## `RedirectPolicy.scala`

```scala
package in.rcard.yaes.http.client

enum RedirectPolicy:
  case Never, Always, Normal
```

## `HttpVersion.scala`

```scala
package in.rcard.yaes.http.client

enum HttpVersion:
  case Http11, Http2
```

## `YaesClientConfig.scala`

```scala
package in.rcard.yaes.http.client

import scala.concurrent.duration.Duration

case class YaesClientConfig(
  connectTimeout: Option[Duration] = None,
  followRedirects: RedirectPolicy = RedirectPolicy.Normal,
  httpVersion: HttpVersion = HttpVersion.Http11
)
```

## `YaesClientConfigSpec.scala`

```scala
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
```
