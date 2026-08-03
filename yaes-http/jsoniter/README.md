![Made for Scala 3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/yaes-io/yaes/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/io.yaes/yaes-http-jsoniter_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/yaes-io/yaes)
[![javadoc](https://javadoc.io/badge2/io.yaes/yaes-http-jsoniter_3/javadoc.svg)](https://javadoc.io/doc/io.yaes/yaes-http-jsoniter_3)
<br/>

# λÆS HTTP jsoniter-scala

JSON body encoder/decoder integration for the λÆS HTTP server using [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala). This module provides automatic `BodyEncoder[A]` and `BodyDecoder[A]` instances for any type that has a `JsonValueCodec[A]` in scope, enabling seamless JSON request/response handling.

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "io.yaes" %% "yaes-http-jsoniter" % "0.23.0"
```

This module depends on `yaes-http-core` and `jsoniter-scala-core` (included transitively). To derive codecs via `JsonCodecMaker.make`, also include `jsoniter-scala-macros` as a provided dependency:

```scala
libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.9" % Provided
```

## Quick Start

Derive a `JsonValueCodec` for your types and import the jsoniter encoder/decoder instances:

```scala
import io.yaes.*
import io.yaes.Log.given
import io.yaes.http.server.*
import io.yaes.http.core.DecodingError
import io.yaes.http.jsoniter.given
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

case class User(name: String, age: Int)
given JsonValueCodec[User] = JsonCodecMaker.make

Shutdown.run {
  Log.run() {
    val server = YaesServer.route(
      // Response body automatically encoded to JSON
      GET(p"/users" / param[Int]("id")) { (req, _, _) =>
        Response.ok(User("Alice", 30))
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
```

## How It Works

The module provides two separate `given` instances, each gated on a single `JsonValueCodec[A]` constraint:

```scala
given jsoniterBodyEncoder[A](using JsonValueCodec[A]): BodyEncoder[A]
given jsoniterBodyDecoder[A](using JsonValueCodec[A]): BodyDecoder[A]
```

Importing `io.yaes.http.jsoniter.given` brings both into scope at once.

- **`jsoniterBodyEncoder`**: encodes values using `writeToString` (compact JSON) and sets the `Content-Type` header to `application/json`
- **`jsoniterBodyDecoder`**: decodes JSON bodies using `readFromString`; any `JsonReaderException` (whether invalid syntax or missing/wrong fields) is mapped to `DecodingError.ParseError` and raised as a `List[DecodingError]`

Note: unlike Circe, jsoniter-scala does not distinguish between syntax errors and structural errors — both surface as `JsonReaderException` and are mapped to `DecodingError.ParseError`. There is no `DecodingError.ValidationError` mapping.

## Codec Derivation

Use `JsonCodecMaker.make` (from `jsoniter-scala-macros`) to derive codecs at compile time:

```scala
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

case class User(name: String, age: Int)
given JsonValueCodec[User] = JsonCodecMaker.make

case class Address(street: String, city: String)
case class Person(name: String, address: Address)
given JsonValueCodec[Address] = JsonCodecMaker.make
given JsonValueCodec[Person]  = JsonCodecMaker.make
```

Nested case classes work automatically as long as a codec is in scope for each type in the hierarchy.

## Error Handling

When JSON decoding fails, the codec raises a `List[DecodingError]` containing a single `DecodingError.ParseError`. Use `Raise.fold` to handle decoding errors in routes:

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
- **Malformed JSON** (e.g., `"not json at all"`) — returns a parse error
- **Missing required fields** (e.g., `{"name":"Alice"}` for a type requiring `name` and `age`) — returns a parse error

## Requirements

- **Java 25+**: Required by λÆS for virtual threads and structured concurrency
- **Scala 3.8.1+**: Uses Scala 3 features (context functions, given instances, etc.)
- **yaes-http-core**: Provides `BodyEncoder`, `BodyDecoder`, and `DecodingError`

## Contributing

Contributions to the λÆS project are welcome! Please feel free to submit pull requests or open issues if you find bugs or have feature requests.
