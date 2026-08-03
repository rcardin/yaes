---
title: JSON with jsoniter-scala
description: JSON body codec integration for λÆS HTTP using jsoniter-scala.
sidebar:
  label: JSON with jsoniter-scala
  order: 4
---

JSON body encoder/decoder integration for the λÆS HTTP server using [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala). The `yaes-http-jsoniter` module provides automatic `BodyEncoder[A]` and `BodyDecoder[A]` instances for any type that has a `JsonValueCodec[A]` in scope, enabling seamless JSON request/response handling without manual implementation.

**Key Features:**
- **Automatic BodyEncoder derivation** - Any type with a `JsonValueCodec[A]` gets a `BodyEncoder` for free
- **Automatic BodyDecoder derivation** - Any type with a `JsonValueCodec[A]` gets a `BodyDecoder` for free
- **Compact JSON encoding** - Values are serialized using `writeToString` (jsoniter's default compact format)
- **Content-Type handling** - Automatically sets `Content-Type: application/json`
- **Unified error mapping** - All decoding failures (syntax or structural) raise a `List[DecodingError.ParseError]` — jsoniter does not distinguish between the two

**Requirements:**
- Java 25+ (for Virtual Threads and Structured Concurrency)
- Scala 3.8.1+
- yaes-http-core (included transitively)

---

## Installation

Add `yaes-http-jsoniter` to your project dependencies:

```scala
libraryDependencies += "io.yaes" %% "yaes-http-jsoniter" % "0.23.0"
```

To derive codecs via `JsonCodecMaker.make`, also add `jsoniter-scala-macros` as a provided dependency:

```scala
libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.9" % Provided
```

> Check [Maven Central](https://central.sonatype.com/artifact/io.yaes/yaes-http-jsoniter_3) for the latest version.

---

## Quick Start

Derive a `JsonValueCodec` for your types, import the jsoniter codecs with `import io.yaes.http.jsoniter.given`, and use typed request/response bodies in your routes:

```scala
import io.yaes.*
import io.yaes.Log.given
import io.yaes.http.server.*
import io.yaes.http.core.DecodingError
import io.yaes.http.jsoniter.given
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

case class User(name: String, age: Int)
given JsonValueCodec[User] = JsonCodecMaker.make

Sync.runBlocking(Duration.Inf) {
  Shutdown.run {
    Log.run() {
      val server = YaesServer.route(
        // Response body automatically encoded to JSON
        GET(p"/users" / param[Int]("id")) { (req, _, _) =>
          Response.ok(User("Alice", 30))
          // Response body: {"name":"Alice","age":30}
          // Content-Type: application/json
        },

        // Request body automatically decoded from JSON
        POST(p"/users") { req =>
          Raise.fold {
            val user = req.as[User]
            Response.created(user)
          } { case errors: List[DecodingError] =>
            Response.badRequest(errors.map(_.message).mkString(", "))
          }
        }
      )

      server.run(port = 8080)
    }
  }
}.get
```

The key import is `io.yaes.http.jsoniter.given` — this brings both `jsoniterBodyEncoder` and `jsoniterBodyDecoder` into scope, which automatically provide a `BodyEncoder[A]` and `BodyDecoder[A]` for any type `A` with a `JsonValueCodec[A]` in scope.

---

## How It Works

The module provides two separate `given` instances:

```scala
given jsoniterBodyEncoder[A](using JsonValueCodec[A]): BodyEncoder[A]
given jsoniterBodyDecoder[A](using JsonValueCodec[A]): BodyDecoder[A]
```

Each is gated on a single `JsonValueCodec[A]` constraint. Unlike Circe's separate `Encoder`/`Decoder`, jsoniter-scala uses a unified codec for both directions.

| Instance | Method | Behavior |
|---|---|---|
| `jsoniterBodyEncoder` | `contentType` | Returns `"application/json"` |
| `jsoniterBodyEncoder` | `encode(value: A)` | Serializes using `writeToString(value)` (compact JSON) |
| `jsoniterBodyDecoder` | `decode(body: String)` | Parses using `readFromString[A]`; any `JsonReaderException` maps to `DecodingError.ParseError` raised as `List[DecodingError]` |

---

## Codec Derivation

Use `JsonCodecMaker.make` (from `jsoniter-scala-macros`) to derive codecs at compile time. This macro generates highly optimized codec implementations without reflection at runtime.

### Basic Derivation

```scala
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

case class User(name: String, age: Int)
given JsonValueCodec[User] = JsonCodecMaker.make
```

### Nested Case Classes

Derive a codec for each type in the hierarchy. The macro handles nested structures automatically:

```scala
case class Address(street: String, city: String)
case class Person(name: String, address: Address)

given JsonValueCodec[Address] = JsonCodecMaker.make
given JsonValueCodec[Person]  = JsonCodecMaker.make

val encoder = summon[BodyEncoder[Person]]
encoder.encode(Person("Alice", Address("123 Main St", "Springfield")))
// {"name":"Alice","address":{"street":"123 Main St","city":"Springfield"}}

val decoder = summon[BodyDecoder[Person]]
// decoder.decode(body) raises List[DecodingError] on failure
```

---

## Error Handling

When JSON decoding fails, the codec raises a `List[DecodingError]` containing a single `DecodingError.ParseError`. Unlike Circe, jsoniter-scala does not distinguish between invalid JSON syntax and structural mismatches (e.g., missing required fields or wrong field types) — both surface as `JsonReaderException` and are mapped to `DecodingError.ParseError`.

Use `Raise.fold` to handle decoding errors in your routes:

```scala
POST(p"/users") { req =>
  Raise.fold {
    val user = req.as[User]
    Response.created(user)
  } { case errors: List[DecodingError] =>
    Response.badRequest(errors.map(_.message).mkString(", "))
  }
}
```

Common failure scenarios:

| Scenario | Example Input | Result |
|---|---|---|
| Malformed JSON | `"not json at all"` | `DecodingError.ParseError` with parse error message |
| Missing required fields | `{"name":"Alice"}` (missing `age`) | `DecodingError.ParseError` with missing field message |
| Wrong field types | `{"name":"Alice","age":"thirty"}` | `DecodingError.ParseError` with type mismatch message |

---

## Complete Example

A full server with JSON endpoints using jsoniter-scala:

```scala
import io.yaes.*
import io.yaes.Log.given
import io.yaes.http.server.*
import io.yaes.http.core.DecodingError
import io.yaes.http.jsoniter.given
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

case class User(id: Int, name: String, email: String)
case class CreateUser(name: String, email: String)

given JsonValueCodec[User]       = JsonCodecMaker.make
given JsonValueCodec[CreateUser] = JsonCodecMaker.make

object JsonServer extends App {
  val userId = param[Int]("userId")

  Sync.runBlocking(Duration.Inf) {
    Shutdown.run {
      Log.run() {
        val server = YaesServer.route(
          // Return a user as JSON
          GET(p"/users" / userId) { (req, path, _) =>
            Response.ok(User(path.userId, "Alice", "alice@example.com"))
          },

          // Parse JSON body and create a user
          POST(p"/users") { req =>
            Raise.fold {
              val newUser = req.as[CreateUser]
              val created = User(1, newUser.name, newUser.email)
              Response.created(created)
            } { case errors: List[DecodingError] =>
              Response.badRequest(errors.map(_.message).mkString(", "))
            }
          }
        )

        server.run(port = 8080)
      }
    }
  }.get
}
```

---

## Dependency

Add the following to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "io.yaes"                              %% "yaes-http-jsoniter"     % "0.23.0",
  "com.github.plokhotnyuk.jsoniter-scala"      %% "jsoniter-scala-macros"  % "2.38.9" % Provided
)
```
