![Made for Scala 3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/yaes-io/yaes/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/io.yaes/yaes-http-server_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/yaes-io/yaes)
[![javadoc](https://javadoc.io/badge2/io.yaes/yaes-http-server_3/javadoc.svg)](https://javadoc.io/doc/io.yaes/yaes-http-server_3)
<br/>

# λÆS HTTP Server

Type-safe HTTP/1.1 server built on YAES effects and virtual threads.

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "io.yaes" %% "yaes-http-server" % "0.23.0"
```

## Overview

`yaes-http-server` provides an effect-based HTTP/1.1 server built on `java.net.ServerSocket` with virtual threads for request handling. Each incoming request is automatically handled in its own fiber (virtual thread) via `Async.fork`, integrating seamlessly with YAES effects for structured concurrency, graceful shutdown, and error handling.

## Features

- **Socket-Based HTTP/1.1**: Built on java.net.ServerSocket with virtual threads
- **Virtual Thread Per Request**: Each request runs in its own fiber under structured concurrency
- **Type-Safe Routing DSL**: Compile-time verified routes with path and query parameters
- **Effect Integration**: Seamless integration with YAES effects (Async, Resource, Shutdown, Raise, Sync)
- **Graceful Shutdown**: Coordinated shutdown with configurable deadlines and 503 responses
- **URL Decoding**: Automatic URL decoding for paths and query parameters
- **Case-Insensitive Headers**: HTTP/1.1 compliant header handling

## Quick Start

```scala
import io.yaes.*
import io.yaes.Log.given
import io.yaes.http.server.*
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

// Server requires Sync, Log, and Shutdown effects
Sync.runBlocking(Duration.Inf) {
  Shutdown.run {
    Log.run() {
      val server = YaesServer.route(
        GET(p"/hello") { req =>
          Response.ok("Hello, World!")
        },
        POST(p"/echo") { req =>
          Response.ok(req.body)
        }
      )

      server.run(port = 8080)
      // Server runs until Shutdown.initiateShutdown() is called
    }
  }
}.get
```

## Routing DSL

### Path Literals

Use the `p` string interpolator for literal paths:

```scala
val routes = Routes(
  GET(p"/") { req => Response.ok("Home") },
  GET(p"/health") { req => Response.ok("OK") },
  GET(p"/api/v1/status") { req => Response.ok("Running") }
)
```

### Path Parameters

Define typed path parameters using `param[Type]("name")`:

```scala
val userId = param[Int]("userId")
val postId = param[Long]("postId")
val username = param[String]("username")

val routes = Routes(
  // Single parameter — read it by name from the `path` named tuple
  GET(p"/users" / userId) { (req, path, _) =>
    Response.ok(s"User ${path.userId}")
  },

  // Multiple parameters
  GET(p"/users" / userId / "posts" / postId) { (req, path, _) =>
    Response.ok(s"User ${path.userId}, Post ${path.postId}")
  },

  // String parameters
  GET(p"/hello" / username) { (req, path, _) =>
    Response.ok(s"Hello, ${path.username}!")
  }
)
```

Handlers receive `(request, path, query)`, where `path` and `query` are named tuples. Parameters
are read by name (`path.userId`), so access is order-independent and self-documenting. The unused
tuple can be ignored with `_`.

**Supported types:** `String`, `Int`, `Long` (provide a `given PathParamParser[T]` for other types)

Path parameters are automatically URL-decoded (e.g., `/users/john%20doe` → `"john doe"`).

### Query Parameters

Define typed query parameters using `queryParam[Type]("name")`:

```scala
val routes = Routes(
  // Single query parameter — read it by name from the `query` named tuple
  GET(p"/search" ? queryParam[String]("q")) { (req, _, query) =>
    Response.ok(s"Searching for: ${query.q}")
  },

  // Multiple query parameters
  GET(p"/search" ? queryParam[String]("q") & queryParam[Int]("limit")) { (req, _, query) =>
    Response.ok(s"Searching for: ${query.q} (limit: ${query.limit})")
  },

  // Optional query parameters — Option/List ride in the element type
  GET(p"/search" ? queryParam[Option[Int]]("page")) { (req, _, query) =>
    val page = query.page.getOrElse(1)
    Response.ok(s"Page $page")
  }
)
```

Query parameters are automatically URL-decoded (e.g., `?q=hello%20world` → `"hello world"`).

### Combined Path and Query Parameters

Combine path and query parameters in a single route:

```scala
val userId = param[Int]("userId")

val routes = Routes(
  GET(p"/users" / userId ? queryParam[String]("include")) { (req, path, query) =>
    Response.ok(s"User ${path.userId} with ${query.include} data")
  }
)
```

### HTTP Methods

Supported methods: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`

```scala
val routes = Routes(
  GET(p"/users") { req => Response.ok("List users") },
  POST(p"/users") { req => Response.created("User created") },
  PUT(p"/users" / userId) { (req, path, _) => Response.ok(s"Updated user ${path.userId}") },
  DELETE(p"/users" / userId) { (req, path, _) => Response.ok(s"Deleted user ${path.userId}") },
  PATCH(p"/users" / userId) { (req, path, _) => Response.ok(s"Patched user ${path.userId}") }
)
```

### Route Matching

Routes are matched in the following order:

1. **Exact routes** (no parameters) - O(1) lookup via map
2. **Parameterized routes** - Sequential matching in definition order

The first matching route handles the request. Unmatched requests return 404.

## Request and Response

### Request

```scala
case class Request(
  method: Method,
  path: String,           // URL-decoded
  headers: Map[String, String],  // Lowercase header names
  body: String,
  queryString: Map[String, List[String]]  // URL-decoded
)

// Access request data
req.method          // Method.GET, Method.POST, etc.
req.path            // "/users/123"
req.header("content-type")  // Case-insensitive: Option[String]
req.queryString.get("q")    // Raw query values: Option[List[String]]
req.body            // Request body as String
```

**Header Handling:** All header names are stored in lowercase for HTTP/1.1 compliance. `req.header("Content-Type")` and `req.header("content-type")` return the same value.

### Response

```scala
// All factory methods accept optional extraHeaders: Map[String, String] = Map.empty
Response.ok(body)                                                      // 200
Response.created(body)                                                 // 201
Response.accepted(body)                                                // 202
Response.noContent()                                                   // 204
Response.badRequest(message)                                           // 400
Response.notFound(message)                                             // 404
Response.internalServerError(message)                                  // 500
Response.serviceUnavailable(message)                                   // 503
Response.withStatus(status, value)                                     // any status code

// Custom status code with extra headers
Response.withStatus(
  201,
  """{"id": 123, "name": "Alice"}""",
  extraHeaders = Map(
    "location" -> "/users/123",
    Headers.ContentType -> "application/json"
  )
)
```

**Adding extra headers to factory methods:**

All factory methods accept an optional `extraHeaders: Map[String, String] = Map.empty` parameter. Header names in `extraHeaders` are normalized to lowercase automatically. Methods that encode a body set `content-type` via the encoder; `extraHeaders` wins on collision. `noContent` carries only the headers you provide.

```scala
// 201 with Location header
Response.created(user, extraHeaders = Map("location" -> s"/users/${user.id}"))

// 301 redirect using withStatus (status codes not covered by convenience methods)
Response.withStatus(301, "", extraHeaders = Map("location" -> "/new-path"))

// 204 with ETag
Response.noContent(extraHeaders = Map("etag" -> "\"abc123\""))

// Override Content-Type explicitly (caller wins)
Response.ok(rawJson, extraHeaders = Map(Headers.ContentType -> "application/json"))
```

### HTTP Methods

```scala
enum Method:
  case GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
```

## Server Configuration

### Basic Configuration

```scala
// Simple port configuration
server.run(port = 8080)

// With custom deadline for graceful shutdown
server.run(port = 8080, deadline = Deadline.after(10.seconds))
```

### Advanced Configuration

```scala
val config = ServerConfig(
  port = 8080,
  deadline = Deadline.after(30.seconds),  // Shutdown deadline
  maxHeaderSize = 16384,                  // Max header size (16 KB)
  maxBodySize = 1048576                   // Max body size (1 MB)
)

server.run(config)
```

## Graceful Shutdown

The server integrates with YAES's `Shutdown` effect for coordinated graceful shutdown:

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

Sync.runBlocking(Duration.Inf) {
  Shutdown.run {
    Log.run() {
      val server = YaesServer.route(
        GET(p"/work") { req =>
          Async.delay(5.seconds)  // Simulate long-running request
          Response.ok("Done")
        }
      )

      // Start server in background fiber
      val serverFiber = Async.forkNamed("server") {
        server.run(port = 8080)
      }

      // Do other work...
      Async.delay(10.seconds)

      // Initiate shutdown
      Shutdown.initiateShutdown()

      // Wait for server to finish
      serverFiber.join()
    }
  }
}.get
```

### Shutdown Behavior

When `Shutdown.initiateShutdown()` is called:

1. **Server stops accepting new connections**
2. **In-flight requests continue processing** (up to the deadline)
3. **New requests receive 503 Service Unavailable**
4. **Server waits for active requests** to complete before stopping
5. **After deadline expires**, remaining requests are interrupted
6. **Shutdown hooks execute** before final cleanup

### Shutdown Hooks

Register callbacks to run when shutdown begins:

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

Sync.runBlocking(Duration.Inf) {
  Shutdown.run {
    Log.run() {
      // Register cleanup hooks
      Shutdown.onShutdown {
        println("Cleaning up resources...")
      }

      val server = YaesServer.route(
        GET(p"/health") { req =>
          Response.ok("OK")
        }
      )

      server.run(port = 8080)
    }
  }
}.get
```

### Shutdown on JVM Termination

The `Shutdown` effect automatically registers JVM shutdown hooks to handle:
- SIGTERM signals
- SIGINT (Ctrl+C)
- JVM shutdown

This ensures graceful shutdown even when the process is killed.

## Body Codecs (JSON, etc.)

The server uses two typeclasses for automatic body handling:
- **`BodyEncoder[A]`** — used by `Response.ok`, `Response.created`, etc., to encode values into a body string and set the `Content-Type` header
- **`BodyDecoder[A]`** — used by `Request.as[A]` to decode the request body; decoding failures are raised via `Raise[List[DecodingError]]` so all failures are surfaced together

Built-in instances for both exist for `String`, `Int`, `Long`, `Double`, and `Boolean`.

```scala
// Built-in codecs work automatically
GET(p"/number") { req =>
  Response.ok(42)  // Automatically encoded to "42" with text/plain
}

POST(p"/echo") { req =>
  val text = req.as[String]  // Decode body as String
  Response.ok(text)
}
```

### Custom JSON Codec Example

To use JSON, implement `BodyEncoder[A]` and/or `BodyDecoder[A]` for your types:

```scala
// Example with circe (not included)
import io.yaes.raises
import io.yaes.Raise
import io.circe.{Encoder, Decoder}
import io.circe.syntax.*
import io.circe.parser.decode

case class User(id: Int, name: String)

given BodyEncoder[User] with {
  def contentType: String = "application/json"
  def encode(user: User): String = user.asJson.noSpaces
}

given BodyDecoder[User] with {
  def decode(body: String): User raises List[DecodingError] =
    decode[User](body).fold(
      error => Raise.raise(List(DecodingError.ParseError(error.getMessage))),
      user => user
    )
}

// Use in routes
GET(p"/users" / userId) { (req, path, _) =>
  val user = User(path.userId, "Alice")
  Response.ok(user)  // Requires BodyEncoder[User]; sets Content-Type: application/json
}

POST(p"/users") { req =>
  Raise.fold {
    val user = req.as[User]  // Requires BodyDecoder[User]; decoded from JSON
    Response.created(user)
  } { case errors: List[DecodingError] =>
    Response.badRequest(errors.map(_.message).mkString(", "))
  }
}
```

Note: JSON libraries (circe, upickle, zio-json, etc.) are not included. Choose your preferred library and implement `BodyEncoder` and/or `BodyDecoder` as needed. For Circe, the `yaes-http-circe` module provides both automatically.

## Examples

### Complete Server Example

```scala
import io.yaes.http.server.*
import io.yaes.*
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

@main
def main(): Unit = {
  val userId = param[Int]("userId")

  Sync.runBlocking(Duration.Inf) {
    Shutdown.run {
      Log.run() {
        val server = YaesServer.route(
          // Health check
          GET(p"/health") { req =>
            Response.ok("OK")
          },

          // List users
          GET(p"/users") { req =>
            Response.ok("""[{"id": 1, "name": "Alice"}]""",
              extraHeaders = Map("content-type" -> "application/json")
            )
          },

          // Get user by ID
          GET(p"/users" / userId) { (req, path, _) =>
            Response.ok(s"""{"id": ${path.userId}, "name": "User ${path.userId}"}""",
              extraHeaders = Map("content-type" -> "application/json")
            )
          },

          // Search with query parameter
          GET(p"/search" ? queryParam[String]("q")) { (req, _, query) =>
            Response.ok(s"Searching for: ${query.q}")
          },

          // Create user
          POST(p"/users") { req =>
            // Parse req.body and create user...
            Response.withStatus(201, """{"id": 123, "name": "New User"}""",
              extraHeaders = Map(
                "content-type" -> "application/json",
                "location"     -> "/users/123"
              )
            )
          }
        )

        server.run(port = 8080)
      }
    }
  }
}
```

## Testing

```bash
# Run all tests
sbt "server/test"

# Run specific test suite
sbt "server/testOnly io.yaes.http.server.parsing.HttpParserSpec"

# Run specific test
sbt "server/testOnly io.yaes.http.server.integration.YaesServerSpec -- -z \"start and accept\""
```

## Known Limitations

- **No HTTP/1.1 Keep-Alive**: Each connection handles one request then closes
- **No Chunked Transfer Encoding**: Request and response bodies must be fully buffered
- **No TLS/HTTPS Support**: Only plain HTTP (use a reverse proxy for HTTPS)
- **No WebSocket Support**: HTTP/1.1 upgrade requests are not supported
- **No Request Streaming**: Bodies are fully read into memory

## Performance Characteristics

- **Virtual Threads**: Each request runs in a virtual thread (Project Loom)
- **O(1) Exact Route Matching**: Routes without parameters use hash map lookup
- **Sequential Parameterized Routes**: Routes with parameters are checked in order
- **Blocking I/O**: Socket operations use blocking I/O (suitable for virtual threads)

## Requirements

- **Java 24+**: Required for virtual threads and structured concurrency
- **Scala 3.7.4+**: Uses Scala 3 features (context functions, inline methods, etc.)
- **YAES Core**: Depends on yaes-core for effect system

## License

Apache 2.0
