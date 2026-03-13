# HTTP Client

An effect-based HTTP client built on YAES effects and Java's `java.net.http.HttpClient`. The `yaes-http-client` module provides a lightweight, composable HTTP client that integrates seamlessly with the YAES effect system for structured error handling, resource lifecycle management, and typed body encoding/decoding.

**Key Features:**
- **Java HttpClient backend** - Built on `java.net.http.HttpClient` with virtual thread support
- **Effect integration** - Uses `Sync`, `Raise`, and `Resource` effects for structured error handling and lifecycle management
- **Typed error hierarchy** - Separate `ConnectionError` (transport) and `HttpError` (HTTP status) error types
- **Fluent builder API** - Immutable request construction with `header`, `queryParam`, and `timeout` extension methods
- **Body codecs** - Automatic request/response body encoding and decoding via the `BodyCodec` typeclass
- **URI validation** - Opaque `Uri` type with construction-time validation via the `Raise` effect

**Requirements:**
- Java 25+ (for Virtual Threads and Structured Concurrency)
- Scala 3.8.1+
- yaes-core (included transitively)

---

## Installation

Add `yaes-http-client` to your project dependencies:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-http-client" % "0.15.0"
```

> Check [Maven Central](https://central.sonatype.com/artifact/in.rcard.yaes/yaes-http-client_3) for the latest version.

---

## Quick Start

Here's a minimal HTTP client that sends a GET request and prints the response:

```scala
import in.rcard.yaes.*
import in.rcard.yaes.http.client.*
import scala.concurrent.duration.*

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
```

**Required Effects:**
- **`Resource`** - Manages the lifecycle of the underlying Java `HttpClient` (auto-closed on block exit)
- **`Raise[ConnectionError]`** - Handles transport-level errors (connection refused, timeouts)
- **`Sync`** - Provided implicitly within `Resource.run`

---

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

  // Use clients here...
}
```

### Client Configuration

The `YaesClientConfig` case class controls client-level settings:

```scala
case class YaesClientConfig(
  connectTimeout: Option[Duration] = None,
  followRedirects: RedirectPolicy = RedirectPolicy.Normal,
  httpVersion: HttpVersion = HttpVersion.Http11
)
```

**Configuration options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `connectTimeout` | `Option[Duration]` | `None` | Maximum time to establish a TCP connection |
| `followRedirects` | `RedirectPolicy` | `Normal` | Redirect-following policy |
| `httpVersion` | `HttpVersion` | `Http11` | HTTP protocol version |

**Redirect policies:**

| Policy | Description |
|--------|-------------|
| `RedirectPolicy.Never` | Never follow redirects |
| `RedirectPolicy.Normal` | Follow redirects except cross-protocol downgrades (HTTPS → HTTP) |
| `RedirectPolicy.Always` | Always follow redirects, including cross-protocol |

**HTTP versions:**

| Version | Description |
|---------|-------------|
| `HttpVersion.Http11` | HTTP/1.1 |
| `HttpVersion.Http2` | HTTP/2 |

> **Note:** Infinite or undefined connect timeouts are silently ignored (treated as "no timeout").

---

## Building Requests

### Factory Methods

Create requests using the `HttpRequest` companion object:

```scala
Raise.run[Uri.InvalidUri] {
  val uri = Uri("https://api.example.com/users")

  // Requests without a body
  val get     = HttpRequest.get(uri)
  val head    = HttpRequest.head(uri)
  val delete  = HttpRequest.delete(uri)
  val options = HttpRequest.options(uri)

  // Requests with a body (requires a BodyCodec in scope)
  val post  = HttpRequest.post(uri, """{"name": "Alice"}""")
  val put   = HttpRequest.put(uri, """{"name": "Bob"}""")
  val patch = HttpRequest.patch(uri, """{"name": "Charlie"}""")
}
```

Methods with a body (`post`, `put`, `patch`) require a `BodyCodec[A]` in scope. The codec determines the `Content-Type` header and encodes the value to a string.

### Fluent Builder API

Use extension methods to customize requests after creation:

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

**Available extension methods:**

| Method | Description |
|--------|-------------|
| `header(name, value)` | Adds or replaces a header (keys are lowercased for consistency) |
| `queryParam(name, value)` | Appends a query parameter (duplicate keys are allowed) |
| `timeout(duration)` | Sets the per-request timeout (infinite durations are ignored) |

All builder methods return a new `HttpRequest` — the original is not modified.

> **Header behavior:** The `header` method can override headers set by `BodyCodec` (e.g., `Content-Type`). Header keys are always stored in lowercase.

> **Query parameter encoding:** Query parameters are URL-encoded when appended to the URI at send time.

---

## Sending Requests

Use `client.send(request)` to execute a request. The method requires `Sync` and `Raise[ConnectionError]` effects:

```scala
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
```

**Important:** `send` returns the response regardless of status code — it never raises `HttpError`. Non-2xx responses are only raised when you call `response.as[A]` to decode the body.

---

## Reading Responses

### Raw Response

The `HttpResponse` contains the raw status code, headers, and body:

```scala
case class HttpResponse(
  status: Int,
  headers: Map[String, String],
  body: String
)
```

**Accessing response data:**

```scala
val response = client.send(request)

response.status                    // Int (e.g. 200, 404)
response.body                      // String (raw body)
response.header("content-type")    // Option[String] (case-insensitive lookup)
```

**Header handling:** All response header keys are stored in lowercase. The `header` method performs a case-insensitive lookup, so `response.header("Content-Type")` and `response.header("content-type")` return the same value.

### Typed Decoding

Use `response.as[A]` to decode the body into a typed value. This method:
1. Checks the status code — raises `HttpError` for non-2xx
2. Decodes the body — raises `DecodingError` if decoding fails

```scala
Raise.run[HttpError | DecodingError] {
  val body: String = response.as[String]
}
```

The union type `HttpError | DecodingError` makes both error types explicit in the effect signature.

---

## Error Handling

The client separates errors into two layers, keeping transport concerns separate from HTTP semantics.

### Transport Errors (`ConnectionError`)

Raised by `client.send` when the request cannot be delivered at the network level:

| Error | Description |
|-------|-------------|
| `ConnectionRefused(host, port)` | TCP connection refused by the target host |
| `ConnectTimeout(host)` | Connection could not be established within the configured timeout |
| `RequestTimeout(url)` | Server accepted the connection but did not respond within the per-request timeout |
| `Unexpected(cause)` | Any other exception during the HTTP exchange |

**Handling transport errors:**

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

Raised by `response.as[A]` when the status code is outside the 2xx range. The error hierarchy distinguishes client errors (4xx) from server errors (5xx) via marker traits.

**Client errors (4xx) — `ClientHttpError`:**

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

**Server errors (5xx) — `ServerHttpError`:**

| Error | Status |
|-------|--------|
| `InternalServerError` | 500 |
| `BadGateway` | 502 |
| `ServiceUnavailable` | 503 |
| `GatewayTimeout` | 504 |
| `OtherServerError(status, body)` | Other 5xx |

**Other status codes:**

| Error | Description |
|-------|-------------|
| `UnexpectedStatus(status, body)` | Status codes outside 4xx and 5xx (e.g., 1xx, 3xx) |

**Matching by error category:**

```scala
val result = Raise.either[HttpError | DecodingError, String] {
  response.as[String]
}
result match
  case Left(e: ClientHttpError) => println(s"Client error ${e.status}: ${e.body}")
  case Left(e: ServerHttpError) => println(s"Server error ${e.status}: ${e.body}")
  case Left(e: DecodingError)   => println(s"Decoding failed: ${e.message}")
  case Right(value)             => println(s"Success: $value")
```

---

## URI Validation

The `Uri` opaque type wraps `java.net.URI` and validates syntax at construction time via the `Raise` effect:

```scala
Raise.run[Uri.InvalidUri] {
  val valid = Uri("https://example.com/api")     // succeeds
  val invalid = Uri("not a valid uri :::")        // raises InvalidUri
}
```

**Handling invalid URIs:**

```scala
val result = Raise.either[Uri.InvalidUri, Uri] {
  Uri("https://example.com")
}
result match
  case Left(Uri.InvalidUri(input, reason)) =>
    println(s"Invalid URI '$input': $reason")
  case Right(uri) =>
    println(s"Host: ${uri.host}, Port: ${uri.port}")
```

**URI extension methods:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `value` | `String` | The URI as a string |
| `host` | `Option[String]` | The host component |
| `port` | `Int` | The port (defaults to 80 if unspecified) |
| `toJavaURI` | `java.net.URI` | The underlying Java URI |

---

## Body Codecs

The client uses the `BodyCodec[A]` typeclass for encoding request bodies (in `post`, `put`, `patch`) and decoding response bodies (in `as[A]`). Built-in codecs exist for `String`, `Int`, `Long`, `Double`, and `Boolean`.

### JSON with Circe

For JSON support, add the `yaes-http-circe` module which provides automatic `BodyCodec` instances for types with Circe `Encoder` and `Decoder`:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-http-circe" % "0.15.0"
```

```scala
import in.rcard.yaes.http.circe.given
import io.circe.{Encoder, Decoder}

case class User(id: Int, name: String) derives Encoder.AsObject, Decoder

Raise.run[Uri.InvalidUri] {
  val uri = Uri("https://api.example.com/users")

  // Content-Type: application/json set automatically by the circe codec
  val request = HttpRequest.post(uri, User(1, "Alice"))
}
```

> See [Circe Integration](http/circe.md) for full documentation on JSON body codecs.

---

## Complete Example

A full client example demonstrating configuration, request building, sending, and error handling:

```scala
import in.rcard.yaes.*
import in.rcard.yaes.http.client.*
import scala.concurrent.duration.*

Raise.run[ConnectionError] {
  Resource.run {
    // Create a configured client
    val client = YaesClient.make(YaesClientConfig(
      connectTimeout = Some(10.seconds),
      followRedirects = RedirectPolicy.Normal
    ))

    Raise.run[Uri.InvalidUri] {
      // Build and send a request
      val uri = Uri("https://httpbin.org/get")
      val request = HttpRequest.get(uri)
        .header("Accept", "application/json")
        .queryParam("name", "Alice")
        .timeout(30.seconds)

      val response = client.send(request)

      // Decode the response with error handling
      val result = Raise.either[HttpError | DecodingError, String] {
        response.as[String]
      }
      result match
        case Left(e: HttpError)     => println(s"HTTP error ${e.status}")
        case Left(e: DecodingError) => println(s"Decoding failed")
        case Right(body)            => println(s"Response: $body")
    }
  }
}
```

---

## Testing

```bash
# Run all client tests
sbt "client/test"

# Run a specific test suite
sbt "client/testOnly in.rcard.yaes.http.client.HttpRequestSpec"

# Run a specific test
sbt "client/testOnly in.rcard.yaes.http.client.YaesClientSendSpec -- -z \"send GET\""
```
