---
title: HTTP Server
description: Build type-safe, effect-based HTTP/1.1 servers with λÆS and Java virtual threads.
sidebar:
  label: HTTP Server
  order: 1
---

A type-safe, effect-based HTTP/1.1 server built on YAES effects and Java virtual threads. The `yaes-http-server` module provides a lightweight, composable HTTP server that integrates seamlessly with the YAES effect system for structured concurrency, graceful shutdown, and functional error handling.

**Key Features:**
- **Socket-based HTTP/1.1** - Built on `java.net.ServerSocket` with virtual threads for concurrent request handling
- **Type-safe routing DSL** - Compile-time verified routes with typed path and query parameters
- **Virtual threads per request** - Each request runs in its own fiber via `Async.fork` under structured concurrency
- **Effect integration** - Seamless composition with YAES effects (Async, Resource, Shutdown, Raise, Log, Sync)
- **Graceful shutdown** - Coordinated shutdown with configurable deadlines and automatic 503 responses
- **Automatic error handling** - HTTP parse errors and parameter validation automatically converted to proper status codes

**Requirements:**
- Java 25+ (for Virtual Threads and Structured Concurrency)
- Scala 3.8.1+
- yaes-core (included transitively)

---

## Installation

Add `yaes-http-server` to your project dependencies:

```scala
libraryDependencies += "io.yaes" %% "yaes-http-server" % "0.23.0"
```

> Check [Maven Central](https://central.sonatype.com/artifact/io.yaes/yaes-http-server_3) for the latest version.

---

## Quick Start

Here's a minimal HTTP server with a single route:

```scala
import io.yaes.*
import io.yaes.Log.given
import io.yaes.http.server.*
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

// Run server with required effect contexts
Sync.runBlocking(Duration.Inf) {
  Shutdown.run {
    Log.run() {
      val server = YaesServer.route(
        GET(p"/hello") { req =>
          Response.ok("Hello, World!")
        }
      )

      server.run(port = 8080)
      // Server runs until Shutdown.initiateShutdown() is called
    }
  }
}.get
```

**Required Effects:**
- **`Sync`** - Tracks I/O side effects (socket binding, accepting connections, reading/writing)
- **`Shutdown`** - Enables graceful shutdown coordination and JVM signal handling
- **`Log`** - Provides server lifecycle logging (start, ready, shutdown, errors)

When the server starts, it:
1. Binds to the specified port
2. Logs "Starting server on port 8080" and "Server ready, listening on port 8080"
3. Accepts incoming connections in a loop
4. Spawns a new virtual thread (fiber) for each request via `Async.fork`
5. Continues until `Shutdown.initiateShutdown()` is called or the JVM receives a termination signal

---

## Routing DSL

The routing DSL provides compile-time type safety for defining HTTP routes with path and query parameters.

### Path Literals

Define literal paths using the `p` string interpolator:

```scala
val routes = Routes(
  GET(p"/") { req =>
    Response.ok("Home")
  },
  GET(p"/health") { req =>
    Response.ok("OK")
  },
  GET(p"/api/v1/users") { req =>
    Response.ok("Users list")
  }
)
```

Combine path segments using the `/` operator:

```scala
GET(p"/api" / "v1" / "users") { req =>
  Response.ok("Users")
}
```

### Path Parameters

Define typed path parameters for extracting values from URLs:

```scala
// Define typed parameters
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
    Response.ok(s"Post ${path.postId} for user ${path.userId}")
  },

  // String parameters
  GET(p"/hello" / username) { (req, path, _) =>
    Response.ok(s"Hello, ${path.username}!")
  }
)
```

Handlers receive `(request, path, query)`, where `path` and `query` are named tuples. Parameters
are read by name (`path.userId`), so access is order-independent and self-documenting. Ignore an
unused tuple with `_`.

**Supported parameter types:**

| Type | Example | Description |
|------|---------|-------------|
| `String` | `param[String]("name")` | Text values |
| `Int` | `param[Int]("id")` | 32-bit integers |
| `Long` | `param[Long]("id")` | 64-bit integers |

Other types are supported by providing a `given PathParamParser[T]`.

**Path parameters are automatically URL-decoded:**
- `/users/john%20doe` → `"john doe"`
- `/files/my%2Ffile.txt` → `"my/file.txt"`

Path parameters have no arity limit — the route type is a named tuple that grows with each `/ param(...)`.

### Query Parameters

Define typed query parameters for optional or required URL query strings:

```scala
val routes = Routes(
  // Single required query parameter — read it by name from the `query` named tuple
  GET(p"/search" ? queryParam[String]("q")) { (req, _, query) =>
    Response.ok(s"Searching for: ${query.q}")
  },

  // Multiple query parameters
  GET(p"/search" ? queryParam[String]("q") & queryParam[Int]("limit")) { (req, _, query) =>
    Response.ok(s"Results for '${query.q}' (limit: ${query.limit})")
  },

  // Optional query parameter — Option/List ride in the element type
  GET(p"/users" ? queryParam[Option[Int]]("page")) { (req, _, query) =>
    val page = query.page.getOrElse(1)
    Response.ok(s"Page $page")
  }
)
```

**Query parameters are automatically URL-decoded:**
- `?q=hello%20world` → `"hello world"`
- `?name=Alice%20%26%20Bob` → `"Alice & Bob"`

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

Supported HTTP methods:

```scala
val userId = param[Int]("userId")

val routes = Routes(
  GET(p"/users") { req =>
    Response.ok("List users")
  },

  POST(p"/users") { req =>
    Response.created("User created")
  },

  PUT(p"/users" / userId) { (req, path, _) =>
    Response.ok(s"Updated user ${path.userId}")
  },

  DELETE(p"/users" / userId) { (req, path, _) =>
    Response.ok(s"Deleted user ${path.userId}")
  },

  PATCH(p"/users" / userId) { (req, path, _) =>
    Response.ok(s"Patched user ${path.userId}")
  }
)
```

**Available methods:** GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS

### Route Matching Order

Routes are matched in a specific order for efficiency:

1. **Exact routes** (no parameters) - Matched first via O(1) hash map lookup
2. **Parameterized routes** (with path/query parameters) - Matched sequentially in definition order

**First match wins.** If no route matches, the server returns 404 Not Found.

**Example:**

```scala
val routes = Routes(
  GET(p"/users/admin") { req => Response.ok("Admin user") },  // Matched first (exact)
  GET(p"/users" / userId) { (req, id) => Response.ok(s"User $id") }  // Matched second
)

// GET /users/admin → "Admin user" (exact match)
// GET /users/123   → "User 123" (parameterized match)
```

---

## Request and Response

### Request

The `Request` object contains all information about the incoming HTTP request:

```scala
case class Request(
  method: Method,                     // HTTP method (GET, POST, etc.)
  path: String,                       // URL-decoded path
  headers: Map[String, String],       // Lowercase header names
  body: String,                       // Request body
  queryString: Map[String, List[String]]  // URL-decoded query parameters
)
```

**Accessing request data:**

```scala
GET(p"/debug") { req =>
  val method = req.method          // Method.GET
  val path = req.path              // "/debug"
  val contentType = req.header("content-type")  // Option[String]
  val userAgent = req.header("User-Agent")      // Case-insensitive
  val queryValue = req.queryString.get("search") // Raw values: Option[List[String]]
  val body = req.body              // Full request body as String

  Response.ok(s"Method: $method, Path: $path")
}
```

**Header handling:** All header names are stored in lowercase for HTTP/1.1 compliance. Both `req.header("Content-Type")` and `req.header("content-type")` return the same value.

> **Important:** Request bodies are fully buffered in memory before processing. There is no streaming support. Configure `maxBodySize` in `ServerConfig` to limit memory usage.

### Response

Build HTTP responses using factory methods on the `Response` companion object:

**Factory methods for common status codes:**

All factory methods accept an optional `extraHeaders: Map[String, String] = Map.empty` parameter. Header names in `extraHeaders` are normalized to lowercase automatically. Methods that encode a body set `content-type` via the encoder; `extraHeaders` wins on collision. `noContent` carries only the headers you provide.

| Method | Status Code | Use Case |
|--------|-------------|----------|
| `Response.ok(body)` | 200 OK | Successful request |
| `Response.created(body)` | 201 Created | Resource created |
| `Response.accepted(body)` | 202 Accepted | Request accepted for processing |
| `Response.noContent()` | 204 No Content | Success with no body |
| `Response.badRequest(message)` | 400 Bad Request | Client error |
| `Response.notFound(message)` | 404 Not Found | Resource not found |
| `Response.internalServerError(message)` | 500 Internal Server Error | Server error |
| `Response.serviceUnavailable(message)` | 503 Service Unavailable | Server shutting down |
| `Response.withStatus(status, value)` | any | Status codes not covered above |

**Adding extra headers:**

```scala
// 201 with Location header
Response.created(user, extraHeaders = Map("location" -> s"/users/${user.id}"))

// 301 redirect — use withStatus for codes not covered by convenience methods
Response.withStatus(301, "", extraHeaders = Map("location" -> "/new-path"))

// 204 with ETag
Response.noContent(extraHeaders = Map("etag" -> "\"abc123\""))

// Override Content-Type explicitly (caller wins over encoder default)
Response.ok(rawJson, extraHeaders = Map(Headers.ContentType -> "application/json"))
```

**Building custom responses:**

```scala
POST(p"/users") { req =>
  // Custom status code with extra headers via withStatus
  Response.withStatus(201, """{"id": 123, "name": "Alice"}""",
    extraHeaders = Map(
      "location" -> "/users/123",
      Headers.ContentType -> "application/json"
    )
  )
}

// Custom content-type via extraHeaders
GET(p"/download") { req =>
  Response.ok("file content",
    extraHeaders = Map(
      "content-type"        -> "application/octet-stream",
      "content-disposition" -> "attachment; filename=data.txt"
    )
  )
}
```

---

## Body Codecs

Body codecs enable automatic encoding and decoding of request and response bodies. The server uses `BodyEncoder[A]` (for response encoding) and `BodyDecoder[A]` (for request decoding) as separate typeclasses.

### Built-in Codecs

The following types have built-in `BodyEncoder` and `BodyDecoder` instances:

| Type | Encoding | Decoding |
|------|----------|----------|
| `String` | Identity | Identity |
| `Int` | `.toString` | `.toInt` |
| `Long` | `.toString` | `.toLong` |
| `Double` | `.toString` | `.toDouble` |
| `Boolean` | `.toString` | `.toBoolean` |

**Example - encoding response bodies:**

```scala
POST(p"/calculate") { req =>
  val result: Int = 42
  Response.ok(result)  // Automatically encoded to "42"
}
```

**Example - decoding request bodies:**

```scala
POST(p"/update") { req =>
  Raise.fold {
    val value = req.as[Int]  // Decode body to Int
    Response.ok(s"Received: $value")
  } { case errors: List[DecodingError] =>
    Response.badRequest(errors.map(_.message).mkString(", "))
  }
}
```

### Custom Codecs

Implement `BodyEncoder[A]` for encoding (used by `Response.ok` etc.) and `BodyDecoder[A]` for decoding (used by `req.as[A]`):

```scala
trait BodyEncoder[A] {
  def contentType: String  // Content-Type header value
  def encode(value: A): String
}

trait BodyDecoder[A] {
  def decode(body: String): A raises List[DecodingError]
}
```

**Example - JSON encoder/decoder using an external library:**

```scala
import io.yaes.{Raise, raises}
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax.*

// Define your domain type
case class User(id: Int, name: String)

// Implement BodyEncoder (used by Response.ok, Response.created, etc.)
given BodyEncoder[User] with {
  def contentType: String = "application/json"
  def encode(user: User): String = user.asJson.noSpaces
}

// Implement BodyDecoder (used by req.as[User])
given BodyDecoder[User] with {
  def decode(body: String): User raises List[DecodingError] =
    decode[User](body).fold(
      error => Raise.raise(List(DecodingError.ParseError(error.getMessage))),
      user => user
    )
}

// Use in routes - Content-Type is automatically set from the encoder
POST(p"/users") { req =>
  Raise.fold {
    val user = req.as[User]
    Response.created(user)  // Content-Type: application/json set automatically
  } { case errors: List[DecodingError] =>
    Response.badRequest(errors.map(_.message).mkString(", "))
  }
}
```

> **Note:** JSON codec libraries (circe, upickle, zio-json, etc.) are not included. Choose your preferred library and implement `BodyEncoder` and/or `BodyDecoder` as needed. See [JSON with Circe](/http/circe/) for a ready-made integration.

---

## Server Configuration

### Basic Configuration

Configure the server with just a port:

```scala
server.run(port = 8080)
```

Or with a port and custom shutdown deadline:

```scala
import scala.concurrent.duration.*

server.run(port = 8080, deadline = Deadline.after(10.seconds))
```

### ServerConfig Options

For advanced configuration, use `ServerConfig`:

```scala
case class ServerConfig(
  port: Int,                          // Port to bind to
  deadline: Deadline,                 // Shutdown deadline (default: 30 seconds)
  maxBodySize: Int,                   // Max request body size (default: 1 MB)
  maxHeaderSize: Int                  // Max header section size (default: 16 KB)
)
```

**Configuration options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `port` | `Int` | *required* | Port number to bind the server |
| `deadline` | `Deadline` | 30 seconds | Maximum time to wait for in-flight requests during shutdown |
| `maxBodySize` | `Int` | 1 MB | Maximum request body size in bytes |
| `maxHeaderSize` | `Int` | 16 KB | Maximum header section size in bytes |

**Example with custom configuration:**

```scala
import scala.concurrent.duration.*

val config = ServerConfig(
  port = 8080,
  deadline = Deadline.after(60.seconds),
  maxBodySize = 5.megabytes,
  maxHeaderSize = 32.kilobytes
)

server.run(config)
```

**Size DSL helpers:**

```scala
val size1 = 1024.bytes       // 1024 bytes
val size2 = 512.kilobytes    // 524,288 bytes
val size3 = 10.megabytes     // 10,485,760 bytes
```

---

## Graceful Shutdown

The HTTP server integrates with the YAES `Shutdown` effect for coordinated graceful shutdown with configurable deadlines.

### Shutdown Effect Integration

The server requires the `Shutdown` effect context. This enables:
- Manual shutdown via `Shutdown.initiateShutdown()`
- Automatic JVM shutdown hook registration for SIGTERM/SIGINT signals
- Coordinated shutdown across multiple components

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

Sync.runBlocking(Duration.Inf) {
  Shutdown.run {
    Raise.run {
      Log.run() {
        val server = YaesServer.route(
          GET(p"/health") { req =>
            Response.ok("OK")
          }
        )

        server.run(port = 8080)
      }
    }
  }
}
```

**Triggering shutdown manually:**

```scala
import scala.concurrent.ExecutionContext.Implicits.global

Sync.runBlocking(Duration.Inf) {
  Shutdown.run {
    Raise.run {
      Log.run() {
        val server = YaesServer.route(
          GET(p"/shutdown") { req =>
            Shutdown.initiateShutdown()  // Trigger graceful shutdown
            Response.ok("Shutdown initiated")
          }
        )

        server.run(port = 8080)
      }
    }
  }
}.get
```

> See [Step 5: Concurrency](/learn/5-concurrency/) for more details on shutdown coordination.

### Shutdown Behavior

When shutdown is initiated (manually or via JVM signal), the following sequence occurs:

1. **Server stops accepting new connections** - The accept loop exits after checking `Shutdown.isShuttingDown()`
2. **In-flight requests continue processing** - Already accepted requests continue up to the configured deadline
3. **New requests receive 503 Service Unavailable** - Any connection accepted during shutdown immediately returns 503
4. **Deadline enforcement** - After the deadline expires, any remaining in-flight requests are interrupted
5. **Resource cleanup** - The server socket is closed and resources are released

**Logged events during shutdown:**
```
Server shutting down...
Server stopped
```

### JVM Signal Handling

The `Shutdown` effect automatically registers JVM shutdown hooks to handle termination signals gracefully:

- **SIGTERM** - Standard termination signal (e.g., `kill <pid>`)
- **SIGINT** - Interrupt signal (e.g., Ctrl+C in terminal)
- **JVM shutdown** - Normal JVM exit

This ensures the server shuts down gracefully when:
- Deployed in containers (Kubernetes, Docker)
- Run in systemd services
- Terminated via process managers
- Stopped during local development (Ctrl+C)

**Container compatibility:** The shutdown behavior is designed for cloud-native deployments. When a container receives a termination signal, the server completes in-flight requests before exiting, preventing dropped connections.

### Shutdown Timeout

If in-flight requests do not complete within the configured deadline, the server logs a warning and completes shutdown normally.

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

val result = Sync.runBlocking(Duration.Inf) {
  Shutdown.run {
    Log.run() {
      val server = YaesServer.route(
        GET(p"/slow") { req =>
          Async.delay(10.seconds)  // Longer than deadline
          Response.ok("Completed")
        }
      )

      server.run(ServerConfig(port = 8080, deadline = Deadline.after(5.seconds)))
    }
  }
}
result.get
// If shutdown exceeds deadline, server logs:
// "Shutdown deadline (5 seconds) exceeded, some requests may not have completed"
```

**Shutdown Timeout Behavior:**
- The server internally handles timeout errors from `Async.withGracefulShutdown`
- A warning is logged when the deadline is exceeded
- Shutdown completes normally (does not raise an error to the caller)
- This is appropriate since timeout is informational, not recoverable

**Best practices:**
- Set `deadline` based on your longest expected request duration
- Monitor server logs for shutdown timeout warnings to identify slow handlers
- Consider adjusting deadlines if timeouts occur frequently during deployment

---

## Error Handling

The HTTP server automatically converts various error conditions into appropriate HTTP responses.

### HTTP Parse Errors

When the server receives malformed HTTP requests, it responds with the appropriate error status code:

| Error Type | HTTP Status | Description |
|------------|-------------|-------------|
| `MalformedRequestLine` | 400 Bad Request | Invalid request line format |
| `UnsupportedMethod` | 501 Not Implemented | HTTP method not supported (e.g., TRACE) |
| `UnsupportedHttpVersion` | 505 HTTP Version Not Supported | Version other than HTTP/1.0 or HTTP/1.1 |
| `MalformedHeaders` | 400 Bad Request | Invalid header format |
| `InvalidContentLength` | 400 Bad Request | Content-Length header is not a valid number |
| `PayloadTooLarge` | 413 Payload Too Large | Request body exceeds `maxBodySize` |
| `MalformedPath` | 400 Bad Request | Invalid URL encoding or path traversal attempt |
| `MalformedQueryString` | 400 Bad Request | Invalid query string encoding |
| `UnexpectedEndOfStream` | 400 Bad Request | Connection closed before body fully received |

**Example error response:**

```
HTTP/1.1 413 Payload Too Large
Content-Length: 89

Payload size 5242880 bytes exceeds maximum allowed size of 1048576 bytes (1.00 MB)
```

**Security:** The server rejects path traversal attempts (paths containing `..` segments) with 400 Bad Request to prevent directory traversal attacks.

### Parameter Validation Errors

Path and query parameter type mismatches are automatically converted to 400 Bad Request:

**Example - invalid path parameter:**
```
GET /users/abc  (expects Int)
→ 400 Bad Request: "Invalid path parameter 'userId': expected Int, got 'abc'"
```

**Example - missing required query parameter:**
```
GET /search  (expects ?q=...)
→ 400 Bad Request: "Missing required query parameter: q"
```

Parameter errors include:
- **Type mismatch** - Value cannot be parsed as the expected type
- **Missing required parameter** - Required query parameter not provided
- **Invalid format** - Query string format is malformed

### Handler Exceptions

Unhandled exceptions thrown by route handlers are caught and converted to 500 Internal Server Error responses:

```scala
GET(p"/error") { req =>
  throw new RuntimeException("Something went wrong")
}

// Results in:
// HTTP/1.1 500 Internal Server Error
// Content-Length: 21
//
// Something went wrong
```

**Best practice:** Use the `Raise` effect for expected errors and proper error handling:

```scala
POST(p"/users") { req =>
  Raise.fold {
    val user = req.as[User]
    // Validate user...
    if (user.name.isEmpty) {
      Raise.raise(ValidationError("Name is required"))
    }
    Response.created(user)
  } { case ValidationError(msg) =>
    Response.badRequest(msg)
  }
}
```

---

## Logging

The HTTP server integrates with the YAES `Log` effect for structured lifecycle logging.

### Log Effect Integration

The server requires the `Log` effect context for logging server lifecycle events:

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

Sync.runBlocking(Duration.Inf) {
  Shutdown.run {
    Raise.run {
      Log.run() {  // Provides logging context
        val server = YaesServer.route(
          GET(p"/health") { req =>
            Response.ok("OK")
          }
        )

        server.run(port = 8080)
      }
    }
  }
}.get
```

> See [SLF4J Logging](/integrations/slf4j-logging/) for details on log levels, formatting, and custom loggers.

### Logged Events

The server logs the following lifecycle events using the logger named **"YaesServer"**:

| Event | Level | Message |
|-------|-------|---------|
| Server starting | INFO | `Starting server on port 8080` |
| Server ready | INFO | `Server ready, listening on port 8080` |
| Connection error | ERROR | `Error accepting connection: <error message>` |
| Shutdown initiated | INFO | `Server shutting down...` |
| Server stopped | INFO | `Server stopped` |

**Example log output:**

```
2026-02-04T10:30:15.123 - INFO - YaesServer - Starting server on port 8080
2026-02-04T10:30:15.456 - INFO - YaesServer - Server ready, listening on port 8080
2026-02-04T10:35:20.789 - INFO - YaesServer - Server shutting down...
2026-02-04T10:35:21.012 - INFO - YaesServer - Server stopped
```

**Error logging example:**

```
2026-02-04T10:32:10.555 - ERROR - YaesServer - Error accepting connection: Connection reset
```

Note: Connection errors during normal operation are logged at ERROR level, but socket exceptions during shutdown are expected and handled silently.

---

## Current Limitations

The HTTP server is designed for simplicity and integration with YAES effects. It has the following limitations:

- **No HTTP Keep-Alive** - Each connection handles exactly one request and then closes. This increases overhead for clients making multiple requests but simplifies connection management.

- **No Chunked Transfer Encoding** - Request and response bodies must be fully buffered in memory. Use `maxBodySize` to limit memory usage. For large file uploads/downloads, consider a reverse proxy or CDN.

- **No TLS/HTTPS Support** - The server only handles plain HTTP. **Workaround:** Use a reverse proxy (nginx, traefik, caddy) for HTTPS termination in production.

- **No WebSocket Support** - HTTP/1.1 upgrade requests are not supported. For real-time communication, use Server-Sent Events (SSE) or poll via regular HTTP requests.

- **No Request/Response Streaming** - Entire bodies are read into memory before processing. Not suitable for large file uploads or video streaming.

- **No HTTP/2 or HTTP/3** - Only HTTP/1.0 and HTTP/1.1 protocols are supported.

- **Maximum 4 Path Parameters** - Routes can have at most 4 typed path parameters. For more complex patterns, use query parameters or combine path segments.

### Workarounds

**For HTTPS in production:**

```nginx
# nginx reverse proxy configuration
server {
    listen 443 ssl;
    server_name example.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

**For large file uploads:**
Configure `maxBodySize` appropriately or use a dedicated file storage service (S3, MinIO) with presigned URLs.

---

## Complete Example

Here's a production-ready HTTP server demonstrating all key features:

```scala
import io.yaes.*
import io.yaes.http.server.*
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

object MyApiServer {

  // Define path parameters
  val userId = param[Int]("userId")
  val postId = param[Long]("postId")

  def main(args: Array[String]): Unit = {
    // Configure server with custom settings
    val config = ServerConfig(
      port = 8080,
      deadline = Deadline.after(30.seconds),  // 30 second shutdown deadline
      maxBodySize = 5.megabytes,              // Allow up to 5 MB request bodies
      maxHeaderSize = 32.kilobytes            // Allow larger header sections
    )

    // Run server with all required effects
    Sync.runBlocking(Duration.Inf) {
      Shutdown.run {
        Raise.run {
          Log.run() {
            val server = YaesServer.route(
            // Health check endpoint
            GET(p"/health") { req =>
              Response.ok("OK")
            },

            // List all users
            GET(p"/users") { req =>
              val users = """[{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}]"""
              Response.ok(users, extraHeaders = Map("content-type" -> "application/json"))
            },

            // Get user by ID
            GET(p"/users" / userId) { (req, path, _) =>
              Response.ok(s"""{"id": ${path.userId}, "name": "User ${path.userId}"}""",
                extraHeaders = Map("content-type" -> "application/json")
              )
            },

            // Search users with query parameter
            GET(p"/users/search" ? queryParam[String]("q")) { (req, _, query) =>
              Response.ok(s"""{"query": "${query.q}", "results": []}""",
                extraHeaders = Map("content-type" -> "application/json")
              )
            },

            // Create new user
            POST(p"/users") { req =>
              // In real app, parse req.body and save to database
              val newUserId = 123
              Response.withStatus(201, s"""{"id": $newUserId, "name": "New User"}""",
                extraHeaders = Map(
                  "content-type" -> "application/json",
                  "location"     -> s"/users/$newUserId"
                )
              )
            },

            // Update user
            PUT(p"/users" / userId) { (req, path, _) =>
              Response.ok(s"""{"id": ${path.userId}, "name": "Updated User"}""",
                extraHeaders = Map("content-type" -> "application/json")
              )
            },

            // Delete user
            DELETE(p"/users" / userId) { (req, path, _) =>
              Response.noContent()  // 204 No Content
            },

            // Get user posts with pagination
            GET(p"/users" / userId / "posts" ? queryParam[Option[Int]]("page")) { (req, path, query) =>
              val page = query.page.getOrElse(1)
              Response.ok(s"""{"userId": ${path.userId}, "page": $page, "posts": []}""",
                extraHeaders = Map("content-type" -> "application/json")
              )
            }
          )

          // Server runs until shutdown signal received
          server.run(config)
          }
        }
      }
    }.get
  }
}
```

**Testing the server:**

```bash
# Health check
curl http://localhost:8080/health

# List users
curl http://localhost:8080/users

# Get specific user
curl http://localhost:8080/users/42

# Search users
curl http://localhost:8080/users/search?q=alice

# Create user
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Charlie"}'

# Update user
curl -X PUT http://localhost:8080/users/42 \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Updated"}'

# Delete user
curl -X DELETE http://localhost:8080/users/42

# Graceful shutdown (Ctrl+C or kill <pid>)
# Server completes in-flight requests before stopping
```
