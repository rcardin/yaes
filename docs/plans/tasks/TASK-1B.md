# TASK-1B | Move shared types to core

status: [x]
requires: 1A
verify: `sbt core/compile`

Target package: `in.rcard.yaes.http.core`
Target dir: `yaes-http/core/src/main/scala/in/rcard/yaes/http/core/`

## Move `BodyCodec.scala`

Copy from `yaes-http/server/src/main/scala/in/rcard/yaes/http/server/BodyCodec.scala` to target dir. Change package to `in.rcard.yaes.http.core`. Delete original. Signatures unchanged:

```scala
package in.rcard.yaes.http.core

trait BodyCodec[A]:
  def contentType: String
  def encode(value: A): String
  def decode(body: String): A raises DecodingError
// given instances: String, Int, Long, Double, Boolean — keep as-is
```

## Move `DecodingError.scala`

Copy from `yaes-http/server/src/main/scala/in/rcard/yaes/http/server/DecodingError.scala` to target dir. Change package. Delete original. Signatures unchanged:

```scala
package in.rcard.yaes.http.core

sealed trait DecodingError
case class ParseError(message: String, cause: Option[Throwable]) extends DecodingError
case class ValidationError(message: String) extends DecodingError
```

## Extract `Method` from `Request.scala`

Read `yaes-http/server/src/main/scala/in/rcard/yaes/http/server/Request.scala`, find the Method enum. Create new file:

```scala
package in.rcard.yaes.http.core

enum Method:
  case GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
```

Then in `Request.scala`: remove the Method enum definition, add `import in.rcard.yaes.http.core.Method`.

## Create `Headers.scala`

```scala
package in.rcard.yaes.http.core

object Headers:
  val ContentType: String    = "content-type"
  val Authorization: String  = "authorization"
  val Accept: String         = "accept"
  val ContentLength: String  = "content-length"
  val UserAgent: String      = "user-agent"
  val Host: String           = "host"
```
