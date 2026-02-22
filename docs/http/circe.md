# HTTP Circe Integration

JSON body codec integration for the λÆS HTTP server using [Circe](https://circe.github.io/circe/). The `yaes-http-circe` module provides an automatic `BodyCodec[A]` instance for any type that has Circe `Encoder` and `Decoder` in scope, enabling seamless JSON request/response handling without manual codec implementation.

**Key Features:**
- **Automatic BodyCodec derivation** - Any type with Circe `Encoder` and `Decoder` gets a `BodyCodec` for free
- **Compact JSON encoding** - Values are serialized using `asJson.noSpaces`
- **Content-Type handling** - Automatically sets `Content-Type: application/json`
- **Error mapping** - Circe decoding failures are mapped to `DecodingError.ParseError`

**Requirements:**
- Java 25+ (for Virtual Threads and Structured Concurrency)
- Scala 3.8.1+
- yaes-http-server (included transitively)

---

## Installation

Add `yaes-http-circe` to your project dependencies:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-http-circe" % "0.14.0"
```

> Check [Maven Central](https://central.sonatype.com/artifact/in.rcard.yaes/yaes-http-circe_3) for the latest version.

---

## Quick Start

Import the circe codecs with `import in.rcard.yaes.http.circe.given` and use typed request/response bodies in your routes:

```scala
import in.rcard.yaes.*
import in.rcard.yaes.Log.given
import in.rcard.yaes.http.server.*
import in.rcard.yaes.http.circe.given
import io.circe.{Encoder, Decoder}

case class User(name: String, age: Int) derives Encoder.AsObject, Decoder

Shutdown.run {
  Log.run() {
    val server = YaesServer.route(
      // Response body automatically encoded to JSON
      GET(p"/users" / param[Int]("id")) { (req, id: Int) =>
        Response.ok(User("Alice", 30))
        // Response body: {"name":"Alice","age":30}
        // Content-Type: application/json
      },

      // Request body automatically decoded from JSON
      POST(p"/users") { req =>
        Raise.fold {
          val user = req.as[User]
          Response.created(user)
        } { case error: DecodingError =>
          Response.badRequest(error.message)
        }
      }
    )

    server.run(port = 8080)
  }
}
```

The key import is `in.rcard.yaes.http.circe.given` — this brings the `circeBodyCodec` instance into scope, which automatically provides a `BodyCodec[A]` for any type `A` that has both a Circe `Encoder[A]` and `Decoder[A]` available.

---

## How It Works

The module provides a single `given` instance:

```scala
given circeBodyCodec[A](using Encoder[A], Decoder[A]): BodyCodec[A]
```

This instance implements the three methods of the `BodyCodec` trait:

| Method | Behavior |
|---|---|
| `contentType` | Returns `"application/json"` |
| `encode(value: A)` | Serializes using `value.asJson.noSpaces` (compact JSON) |
| `decode(body: String)` | Parses using Circe's `decode[A]`, raising `DecodingError.ParseError` on failure |

Because the instance is parameterized over `A`, it works for **any** type with the required Circe typeclasses — no per-type boilerplate is needed.

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

val codec = summon[BodyCodec[Person]]
codec.encode(Person("Alice", Address("123 Main St", "Springfield")))
// {"name":"Alice","address":{"street":"123 Main St","city":"Springfield"}}
```

---

## Error Handling

When JSON decoding fails, the codec raises a `DecodingError.ParseError` with the original Circe error message and exception attached. Use `Raise.fold` to handle decoding errors in your routes:

```scala
POST(p"/users") { req =>
  Raise.fold {
    val user = req.as[User]
    Response.created(user)
  } { case error: DecodingError =>
    Response.badRequest(error.message)
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

A full server with JSON endpoints using Circe:

```scala
import in.rcard.yaes.*
import in.rcard.yaes.Log.given
import in.rcard.yaes.http.server.*
import in.rcard.yaes.http.circe.given
import io.circe.{Encoder, Decoder}

case class User(id: Int, name: String, email: String) derives Encoder.AsObject, Decoder
case class CreateUser(name: String, email: String) derives Encoder.AsObject, Decoder

object JsonServer extends App {
  val userId = param[Int]("userId")

  Shutdown.run {
    Log.run() {
      val server = YaesServer.route(
        // Return a user as JSON
        GET(p"/users" / userId) { (req, id: Int) =>
          Response.ok(User(id, "Alice", "alice@example.com"))
        },

        // Parse JSON body and create a user
        POST(p"/users") { req =>
          Raise.fold {
            val newUser = req.as[CreateUser]
            val created = User(1, newUser.name, newUser.email)
            Response.created(created)
          } { case error: DecodingError =>
            Response.badRequest(error.message)
          }
        }
      )

      server.run(port = 8080)
    }
  }
}
```

---

## Dependency

Add the following to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "in.rcard.yaes" %% "yaes-http-circe" % "0.14.0",
  "io.circe"      %% "circe-generic"   % "0.14.15"  // For derivation
)
```
