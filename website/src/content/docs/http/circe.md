---
title: JSON with Circe
description: JSON body codec integration for λÆS HTTP using Circe.
sidebar:
  label: JSON with Circe
  order: 3
---

JSON body encoder/decoder integration for the λÆS HTTP server using [Circe](https://circe.github.io/circe/). The `yaes-http-circe` module provides automatic `BodyEncoder[A]` and `BodyDecoder[A]` instances for any type that has the corresponding Circe `Encoder` or `Decoder` in scope, enabling seamless JSON request/response handling without manual implementation.

**Key Features:**
- **Automatic BodyEncoder derivation** - Any type with a Circe `Encoder` gets a `BodyEncoder` for free
- **Automatic BodyDecoder derivation** - Any type with a Circe `Decoder` gets a `BodyDecoder` for free
- **Compact JSON encoding** - Values are serialized using `asJson.noSpaces`
- **Content-Type handling** - Automatically sets `Content-Type: application/json`
- **Accumulating error mapping** - Decoding raises a non-empty `List[DecodingError]` accumulating all failures; Circe `ParsingFailure` maps to `DecodingError.ParseError`, each `DecodingFailure` maps to `DecodingError.ValidationError`

**Requirements:**
- Java 25+ (for Virtual Threads and Structured Concurrency)
- Scala 3.8.1+
- yaes-http-server (included transitively)

---

## Installation

Add `yaes-http-circe` to your project dependencies:

```scala
libraryDependencies += "io.yaes" %% "yaes-http-circe" % "0.23.0"
```

If you need Circe's automatic derivation features, also include `circe-generic`:

```scala
libraryDependencies += "io.circe" %% "circe-generic" % "0.14.15"
```

> Check [Maven Central](https://central.sonatype.com/artifact/io.yaes/yaes-http-circe_3) for the latest version.

---

## Quick Start

Import the circe codecs with `import io.yaes.http.circe.given` and use typed request/response bodies in your routes:

```scala
import io.yaes.*
import io.yaes.Log.given
import io.yaes.http.server.*
import io.yaes.http.circe.given
import io.circe.{Encoder, Decoder}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

case class User(name: String, age: Int) derives Encoder.AsObject, Decoder

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

The key import is `io.yaes.http.circe.given` — this brings both `circeBodyEncoder` and `circeBodyDecoder` into scope, which automatically provide a `BodyEncoder[A]` for any type `A` with a Circe `Encoder[A]` and a `BodyDecoder[A]` for any type `A` with a Circe `Decoder[A]`.

---

## How It Works

The module provides two separate `given` instances:

```scala
given circeBodyEncoder[A](using Encoder[A]): BodyEncoder[A]
given circeBodyDecoder[A](using Decoder[A]): BodyDecoder[A]
```

Each is gated on a single Circe constraint, so you only need the relevant typeclass in scope.

| Instance | Method | Behavior |
|---|---|---|
| `circeBodyEncoder` | `contentType` | Returns `"application/json"` |
| `circeBodyEncoder` | `encode(value: A)` | Serializes using `value.asJson.noSpaces` (compact JSON) |
| `circeBodyDecoder` | `decode(body: String)` | Parses using Circe's `decodeAccumulating[A]`, raising a non-empty `List[DecodingError]`: `DecodingError.ParseError` for invalid JSON syntax, or one `DecodingError.ValidationError` per accumulated schema mismatch |

Because the instances are parameterized over `A`, they work for **any** type with the required Circe typeclasses — no per-type boilerplate is needed.

---

## Derivation Strategies

Circe offers multiple ways to derive `Encoder` and `Decoder` instances for your types.

### Automatic Derivation

The simplest approach uses Scala 3 `derives` clauses:

```scala
case class User(name: String, age: Int) derives Encoder.AsObject, Decoder
```

This automatically generates both the `Encoder` and `Decoder` at compile time.

### Semi-Automatic Derivation

For more control over which types get codecs, derive instances explicitly:

```scala
case class Product(id: Long, label: String)

given Encoder[Product] = Encoder.AsObject.derived
given Decoder[Product] = Decoder.derived
```

This requires `circe-generic` on the classpath.

### Nested Case Classes

Both strategies work with nested structures:

```scala
case class Address(street: String, city: String) derives Encoder.AsObject, Decoder
case class Person(name: String, address: Address) derives Encoder.AsObject, Decoder

val encoder = summon[BodyEncoder[Person]]
encoder.encode(Person("Alice", Address("123 Main St", "Springfield")))
// {"name":"Alice","address":{"street":"123 Main St","city":"Springfield"}}

val decoder = summon[BodyDecoder[Person]]
// decoder.decode(body) raises List[DecodingError] on failure
```

---

## Error Handling

When JSON decoding fails, the codec raises a non-empty `List[DecodingError]` accumulating all errors found in the body. A `ParsingFailure` (invalid JSON syntax) becomes `DecodingError.ParseError` with the original exception attached, while each `DecodingFailure` (valid JSON but wrong shape) becomes `DecodingError.ValidationError`. Use `Raise.fold` to handle decoding errors in your routes:

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
| Missing required fields | `{"name":"Alice"}` (missing `age`) | `DecodingError.ValidationError` with missing field message |
| Wrong field types | `{"name":"Alice","age":"thirty"}` | `DecodingError.ValidationError` with type mismatch message |

---

## Complete Example

A full server with JSON endpoints using Circe:

```scala
import io.yaes.*
import io.yaes.Log.given
import io.yaes.http.server.*
import io.yaes.http.circe.given
import io.circe.{Encoder, Decoder}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

case class User(id: Int, name: String, email: String) derives Encoder.AsObject, Decoder
case class CreateUser(name: String, email: String) derives Encoder.AsObject, Decoder

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
  "io.yaes" %% "yaes-http-circe" % "0.23.0",
  "io.circe"      %% "circe-generic"   % "0.14.15"  // For derivation
)
```
