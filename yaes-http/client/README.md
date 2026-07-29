![Made for Scala 3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/yaes-io/yaes/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/io.yaes/yaes-http-client_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/yaes-io/yaes)
[![javadoc](https://javadoc.io/badge2/io.yaes/yaes-http-client_3/javadoc.svg)](https://javadoc.io/doc/io.yaes/yaes-http-client_3)
<br/>

# λÆS HTTP Client

Effect-based HTTP client built on YAES effects and Java's `java.net.http.HttpClient`.

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "io.yaes" %% "yaes-http-client" % "0.23.0"
```

## Overview

`yaes-http-client` provides an effect-based HTTP client wrapping Java's `java.net.http.HttpClient`. Transport errors are raised as `ConnectionError` via the `Raise` effect, and HTTP-level errors (non-2xx) are raised when decoding the response via `HttpResponse.as`. Client lifecycle is managed through the `Resource` effect.

## Features

- **Java HttpClient Backend**: Built on `java.net.http.HttpClient` with virtual thread support
- **Effect Integration**: Uses `Sync`, `Raise`, and `Resource` effects for structured error handling and lifecycle management
- **Typed Error Hierarchy**: Separate `ConnectionError` (transport) and `HttpError` (HTTP status) types
- **Fluent Builder API**: Immutable request building with `header`, `queryParam`, and `timeout` extension methods
- **Body Codecs**: Request body encoding via `BodyEncoder` and response body decoding via `BodyDecoder`
- **URI Validation**: Opaque `Uri` type with compile-time-safe construction via the `Raise` effect
- **Path Segment Operator**: `uri / segment` to append URL-encoded segments dynamically, preserving query strings and fragments
- **Path Parameter Interpolation**: `uri"..."` string interpolator for ergonomic, type-safe path param encoding via `PathParamStringifier`
- **Configurable**: Connect timeout, redirect policy, and HTTP version selection

## Quick Start

```scala
import io.yaes.*
import io.yaes.http.client.*
import scala.concurrent.duration.*

Sync.runBlocking(30.seconds) {
  Raise.run[ConnectionError] {
    Resource.run {
      val client = YaesClient.make()

      Raise.run[Uri.InvalidUri] {
        val uri = Uri("https://httpbin.org/get")
        val response = client.send(HttpRequest.get(uri))
        println(s"Status: ${response.status}")
        println(s"Body: ${response.body}")
      }
    }
  }
}
```

## Creating a Client

Use `YaesClient.make` inside a `Resource.run` block. The underlying Java `HttpClient` is automatically closed when the `Resource` block completes:

```scala
Resource.run {
  // Default configuration
  val client = YaesClient.make()

  // Custom configuration
  val customClient = YaesClient.make(YaesClientConfig(
    connectTimeout = Some(5.seconds),
    followRedirects = RedirectPolicy.Never,
    httpVersion = HttpVersion.Http2
  ))

  // Use clients...
}
```

### Client Configuration

```scala
case class YaesClientConfig(
  connectTimeout: Option[Duration] = None,
  followRedirects: RedirectPolicy = RedirectPolicy.Normal,
  httpVersion: HttpVersion = HttpVersion.Http11
)
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `connectTimeout` | `Option[Duration]` | `None` | Maximum time to establish a TCP connection |
| `followRedirects` | `RedirectPolicy` | `Normal` | Redirect-following policy |
| `httpVersion` | `HttpVersion` | `Http11` | HTTP protocol version |

**Redirect policies:**

| Policy | Description |
|--------|-------------|
| `RedirectPolicy.Never` | Never follow redirects |
| `RedirectPolicy.Normal` | Follow redirects except cross-protocol downgrades |
| `RedirectPolicy.Always` | Always follow redirects, including cross-protocol |

**HTTP versions:**

| Version | Description |
|---------|-------------|
| `HttpVersion.Http11` | HTTP/1.1 |
| `HttpVersion.Http2` | HTTP/2 |

## Building Requests

### Factory Methods

Create requests using the companion object factory methods:

```scala
Raise.run[Uri.InvalidUri] {
  val uri = Uri("https://api.example.com/users")

  // Requests without a body
  val get     = HttpRequest.get(uri)
  val head    = HttpRequest.head(uri)
  val delete  = HttpRequest.delete(uri)
  val options = HttpRequest.options(uri)

  // Requests with a body (requires a BodyEncoder in scope)
  val post  = HttpRequest.post(uri, """{"name": "Alice"}""")
  val put   = HttpRequest.put(uri, """{"name": "Bob"}""")
  val patch = HttpRequest.patch(uri, """{"name": "Charlie"}""")
}
```

### Fluent Builder API

Use extension methods to customize requests:

```scala
Raise.run[Uri.InvalidUri] {
  val uri = Uri("https://api.example.com/users")

  val request = HttpRequest.get(uri)
    .header("Authorization", "Bearer my-token")
    .header("Accept", "application/json")
    .queryParam("page", "1")
    .queryParam("limit", "10")
    .timeout(30.seconds)
}
```

- `header(name, value)` — adds or replaces a header (keys are lowercased)
- `queryParam(name, value)` — appends a query parameter (duplicate keys allowed)
- `timeout(duration)` — sets the per-request timeout (non-finite or non-positive durations clear any existing timeout)

## Sending Requests

Use `client.send(request)` to execute a request. The method requires `Sync` and `Raise[ConnectionError]` effects:

```scala
Sync.runBlocking(30.seconds) {
  Raise.run[ConnectionError] {
    Resource.run {
      val client = YaesClient.make()

      Raise.run[Uri.InvalidUri] {
        val uri = Uri("https://httpbin.org/post")
        val request = HttpRequest.post(uri, "hello")
          .header("Accept", "application/json")

        val response: HttpResponse = client.send(request)
        println(s"Status: ${response.status}")
        println(s"Content-Type: ${response.header("content-type")}")
        println(s"Body: ${response.body}")
      }
    }
  }
}
```

**Important:** `send` returns the response regardless of status code. It never raises `HttpError` — use `response.as[A]` to decode and check status.

## Reading Responses

### Raw Response

The `HttpResponse` contains raw status, headers, and body:

```scala
case class HttpResponse(
  status: Int,
  headers: Map[String, String],
  body: String
)
```

```scala
val response = client.send(request)

response.status                    // Int (e.g. 200, 404)
response.body                      // String
response.header("content-type")    // Option[String] (case-insensitive)
```

### Typed Decoding

Use `response.as[A]` to decode the body into a typed value. This raises `HttpError` for non-2xx status codes and a `DecodingError` if decoding fails:

```scala
import io.yaes.http.core.DecodingError

Raise.run[HttpError | DecodingError] {
  val user: String = response.as[String]
}
```

## Error Handling

The client separates errors into two layers:

### Transport Errors (`ConnectionError`)

Raised by `client.send` when the request cannot be delivered:

| Error | Description |
|-------|-------------|
| `ConnectionRefused(host, port)` | TCP connection refused |
| `ConnectTimeout(host)` | Connection timeout (from client config) |
| `RequestTimeout(url)` | Per-request timeout exceeded |
| `Unexpected(cause)` | Other exceptions during the exchange |

```scala
val result = Raise.either[ConnectionError, HttpResponse] {
  client.send(request)
}
result match
  case Left(ConnectionError.ConnectionRefused(host, port)) =>
    println(s"Cannot connect to $host:$port")
  case Left(ConnectionError.ConnectTimeout(host)) =>
    println(s"Connection to $host timed out")
  case Left(ConnectionError.RequestTimeout(url)) =>
    println(s"Request to $url timed out")
  case Left(ConnectionError.Unexpected(cause)) =>
    println(s"Unexpected error: ${cause.getMessage}")
  case Right(response) =>
    println(s"Got response: ${response.status}")
```

### HTTP Errors (`HttpError`)

Raised by `response.as[A]` when the status code is outside 2xx:

**Client errors (4xx):**

| Error | Status |
|-------|--------|
| `BadRequest` | 400 |
| `Unauthorized` | 401 |
| `Forbidden` | 403 |
| `NotFound` | 404 |
| `MethodNotAllowed` | 405 |
| `Conflict` | 409 |
| `Gone` | 410 |
| `UnprocessableEntity` | 422 |
| `TooManyRequests` | 429 |
| `OtherClientError(status, body)` | Other 4xx |

**Server errors (5xx):**

| Error | Status |
|-------|--------|
| `InternalServerError` | 500 |
| `BadGateway` | 502 |
| `ServiceUnavailable` | 503 |
| `GatewayTimeout` | 504 |
| `OtherServerError(status, body)` | Other 5xx |

Use the `ClientHttpError` and `ServerHttpError` marker traits to match error categories:

```scala
import io.yaes.http.core.DecodingError

val result = Raise.either[HttpError | DecodingError, String] {
  response.as[String]
}
result match
  case Left(e: ClientHttpError)  => println(s"Client error ${e.status}: ${e.body}")
  case Left(e: ServerHttpError)  => println(s"Server error ${e.status}: ${e.body}")
  case Left(error: DecodingError) => println(s"Decoding failed: ${error.message}")
  case Right(value)               => println(s"Success: $value")
```

### Typed Error Body Decoding

Many REST APIs return structured error payloads alongside non-2xx responses (e.g. a `422` with a JSON `ApiError`). Use `err.as[E]` on any `HttpError` to decode the raw error body into a typed value using the same `BodyDecoder` infrastructure as the success path:

```scala
import io.circe.Decoder
import io.yaes.http.circe.given
import io.yaes.http.core.DecodingError

case class ApiError(field: String, message: String)
given Decoder[ApiError] =
  Decoder.forProduct2("field", "message")(ApiError.apply)

val result: Either[DecodingError, ApiError | User] =
  Raise.fold {
    response.as[User]
  } {
    case err: HttpError =>
      Raise.either[DecodingError, ApiError | User] {
        err.as[ApiError]
      }
    case error: DecodingError =>
      Left(error)
  } {
    user => Right(user)
  }
```

`err.as[E]` raises `DecodingError` if decoding fails — the same semantics as `response.as[A]`.

## Path Parameters

Use the `uri"..."` string interpolator to construct URIs with path parameters. Each interpolated argument is automatically URL-encoded (spaces become `%20`, slashes become `%2F`, etc.) via the `PathParamStringifier[A]` typeclass. The literal parts of the URI template are validated at **compile time**. Since interpolations are URL-encoded and intended for path segments, the assembled URI is always well-formed when placeholders appear only in path positions — no `Raise` effect is needed at runtime.

```scala
import io.yaes.*
import io.yaes.http.client.*

val userId: Int     = 42
val orderId: String = "ord-99"

val request = HttpRequest.get(uri"https://api.example.com/users/$userId/orders/$orderId")
```

Built-in `PathParamStringifier` instances exist for `String`, `Int`, `Long`, `Boolean`, `Double`, and `UUID`.

### Custom Encoders

Provide a `given PathParamStringifier[A]` for your own types:

```scala
import io.yaes.*
import io.yaes.http.client.*

case class ItemId(value: Int)

given PathParamStringifier[ItemId] with {
  def encode(v: ItemId): String = s"item-${v.value}"
}

val id = ItemId(5)
val request = HttpRequest.get(uri"https://api.example.com/items/$id")
// => GET https://api.example.com/items/item-5
```

A missing `PathParamStringifier` instance is a **compile error** — the interpolator will not fall back to `.toString`.

---

## URI Validation

The `Uri` opaque type validates URI syntax at construction time via the `Raise` effect:

```scala
Raise.run {
  val valid = Uri("https://example.com/api")     // succeeds
  val invalid = Uri("not a valid uri :::")        // raises InvalidUri
}
```

```scala
// Handle invalid URIs
val result = Raise.either[Uri.InvalidUri, Uri] {
  Uri("https://example.com")
}
result match
  case Left(Uri.InvalidUri(input, reason)) =>
    println(s"Invalid URI '$input': $reason")
  case Right(uri) =>
    println(s"Host: ${uri.host}, Port: ${uri.port}")
```

### Path Segment Operator (`/`)

Use `uri / segment` to append a single path segment to an existing `Uri`. The segment is URL-encoded via the same `PathParamStringifier` typeclass used by the `uri"..."` interpolator. Query strings and fragments are preserved.

```scala
Raise.run {
  val base   = Uri("https://api.example.com/users")
  val userId = 42

  val uri = base / userId
  // => https://api.example.com/users/42
}
```

Operators chain naturally:

```scala
Raise.run {
  val uri = Uri("https://api.example.com") / "users" / 42 / "orders"
  // => https://api.example.com/users/42/orders
}
```

Query strings and fragments survive the append:

```scala
Raise.run {
  val base = Uri("https://api.example.com/users?active=true")
  val uri  = base / 42
  // => https://api.example.com/users/42?active=true
}
```

Any type with a `PathParamStringifier` instance is accepted. A trailing slash on the base URI is normalised before appending.

## Body Codecs

The client uses `BodyEncoder[A]` for encoding request bodies (in `post`, `put`, `patch`) and `BodyDecoder[A]` for decoding response bodies (in `response.as[A]`). Built-in instances for both exist for `String`, `Int`, `Long`, `Double`, and `Boolean`.

For JSON support, use the `yaes-http-circe` module which provides automatic `BodyEncoder` and `BodyDecoder` instances for types with Circe `Encoder` and `Decoder` respectively:

```scala
libraryDependencies += "io.yaes" %% "yaes-http-circe" % "0.23.0"
```

```scala
import io.yaes.http.circe.given
import io.circe.{Encoder, Decoder}

case class User(id: Int, name: String) derives Encoder.AsObject, Decoder

Raise.run {
  val uri = Uri("https://api.example.com/users")
  val request = HttpRequest.post(uri, User(1, "Alice"))
  // Content-Type: application/json set automatically
}
```

## Complete Example

```scala
import io.yaes.*
import io.yaes.http.client.*
import io.yaes.http.core.DecodingError
import scala.concurrent.duration.*

Raise.run {
  Resource.run {
    val client = YaesClient.make(YaesClientConfig(
      connectTimeout = Some(10.seconds),
      followRedirects = RedirectPolicy.Normal
    ))

    Raise.run {
      val uri = Uri("https://httpbin.org/get")
      val request = HttpRequest.get(uri)
        .header("Accept", "application/json")
        .queryParam("name", "Alice")
        .timeout(30.seconds)

      val response = client.send(request)

      val result = Raise.either[HttpError | DecodingError, String] {
        response.as[String]
      }
      result match
        case Left(e: HttpError)        => println(s"HTTP error ${e.status}")
        case Left(error: DecodingError) => println(s"Decoding failed")
        case Right(body)               => println(s"Response: $body")
    }
  }
}
```

## Testing

```bash
# Run all tests
sbt "client/test"

# Run specific test suite
sbt "client/testOnly io.yaes.http.client.HttpRequestSpec"
```

## Requirements

- **Java 25+**: Required for virtual threads and structured concurrency
- **Scala 3.8.1+**: Uses Scala 3 features (context functions, opaque types, etc.)
- **YAES Core**: Depends on yaes-core for effect system

## License

Apache 2.0
