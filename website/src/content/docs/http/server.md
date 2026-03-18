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
libraryDependencies += "in.rcard.yaes" %% "yaes-http-server" % "0.15.0"
```

> Check [Maven Central](https://central.sonatype.com/artifact/in.rcard.yaes/yaes-http-server_3) for the latest version.

---

## Quick Start

Here's a minimal HTTP server with a single route:

```scala
import in.rcard.yaes.*
import in.rcard.yaes.Log.given
import in.rcard.yaes.http.server.*
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
  // Single parameter
  GET(p"/users" / userId) { (req, id: Int) =>
    Response.ok(s"User $id")
  },

  // Multiple parameters
  GET(p"/users" / userId / "posts" / postId) { (req, uid: Int, pid: Long) =>
    Response.ok(s"Post $pid for user $uid")
  },

  // String parameters
  GET(p"/hello" / username) { (req, name: String) =>
    Response.ok(s"Hello, $name!")
  }
)
```

**Supported parameter types:**

| Type | Example | Description |
|------|---------|-------------|
| `String` | `param[String]("name")` | Text values |
| `Int` | `param[Int]("id")` | 32-bit integers |
| `Long` | `param[Long]("id")` | 64-bit integers |
| `Boolean` | `param[Boolean]("enabled")` | true/false |
| `Double` | `param[Double]("price")` | Floating-point numbers |

**Path parameters are automatically URL-decoded:**
- `/users/john%20doe` → `"john doe"`
- `/files/my%2Ffile.txt` → `"my/file.txt"`

> **Limitation:** Maximum 4 path parameters per route. For more complex scenarios, use query parameters.

### Query Parameters

Define typed query parameters for optional or required URL query strings:

```scala
val routes = Routes(
  // Single required query parameter
  GET(p"/search" ? queryParam[String]("q")) { req =>
    val query = req.queryParam("q").get
    Response.ok(s"Searching for: $query")
  },

  // Multiple query parameters
  GET(p"/search" ? queryParam[String]("q") & queryParam[Int]("limit")) { req =>
    val query = req.queryParam("q").get
    val limit = req.queryParam("limit").map(_.toInt).getOrElse(10)
    Response.ok(s"Results for '$query' (limit: $limit)")
  },

  // Optional query parameter
  GET(p"/users" ? queryParam[Option[Int]]("page")) { req =>
    val page = req.queryParam("page").flatMap(_.toIntOption).getOrElse(1)
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
  GET(p"/users" / userId ? queryParam[String]("include")) { (req, id: Int) =>
    val include = req.queryParam("include").getOrElse("basic")
    Response.ok(s"User $id with $include data")
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

  PUT(p"/users" / userId) { (req, id: Int) =>
    Response.ok(s"Updated user $id")
  },

  DELETE(p"/users" / userId) { (req, id: Int) =>
    Response.ok(s"Deleted user $id")
  },

  PATCH(p"/users" / userId) { (req, id: Int) =>
    Response.ok(s"Patched user $id")
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
  val queryValue = req.queryParam("search")     // Option[String]
  val body = req.body              // Full request body as String

  Response.ok(s"Method: $method, Path: $path")
}
```

**Header handling:** All header names are stored in lowercase for HTTP/1.1 compliance. Both `req.header("Content-Type")` and `req.header("content-type")` return the same value.

> **Important:** Request bodies are fully buffered in memory before processing. There is no streaming support. Configure `maxBodySize` in `ServerConfig` to limit memory usage.

### Response

Build HTTP responses using the `Response` case class:

```scala
case class Response(
  status: Int,
  headers: Map[String, String] = Map.empty,
  body: String = ""
)
```

**Helper constructors for common status codes:**

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

**Building custom responses:**

```scala
POST(p"/users") { req =>
  // Custom response with headers
  Response(
    status = 201,
    headers = Map(
      "Location" -> "/users/123",
      "Content-Type" -> "application/json"
    ),
    body = """{"id": 123, "name": "Alice"}"""
  )
}

// Adding custom headers
GET(p"/download") { req =>
  Response(
    status = 200,
    headers = Map(
      "Content-Type" -> "application/octet-stream",
      "Content-Disposition" -> "attachment; filename=data.txt"
    ),
    body = "file content"
  )
}
```

---

## Body Codecs

Body codecs enable automatic encoding and decoding of request and response bodies.

### Built-in Codecs

The following types have built-in codecs:

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
  } { case error: DecodingError =>
    Response.badRequest(error.message)
  }
}
```

### Custom Codecs

Implement the `BodyCodec[A]` trait for custom types:

```scala
trait BodyCodec[A] {
  def contentType: String  // Content-Type header value
  def encode(value: A): String
  def decode(body: String): A raises DecodingError
}
```

**Example - JSON codec using an external library:**

```scala
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax.*

// Define your domain type
case class User(id: Int, name: String)

// Implement BodyCodec
given userCodec: BodyCodec[User] with {
  def contentType: String = "application/json"

  def encode(user: User): String =
    user.asJson.noSpaces  // Using circe encoder

  def decode(body: String): User raises DecodingError =
    decode[User](body).fold(
      error => Raise.raise(DecodingError(error.getMessage)),
      user => user
    )
}

// Use in routes - Content-Type is automatically set from codec
POST(p"/users") { req =>
  Raise.fold {
    val user = req.as[User]
    Response.created(user)  // Content-Type: application/json set automatically
  } { case error: DecodingError =>
    Response.badRequest(error.message)
  }
}
```

> **Note:** JSON codec libraries (circe, upickle, zio-json, etc.) are not included. Choose your preferred library and implement the `BodyCodec` trait. See [JSON with Circe](/yaes/http/circe/) for a ready-made integration.

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

> See [Step 5: Concurrency](/yaes/learn/5-concurrency/) for more details on shutdown coordination.

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

> See [SLF4J Logging](/yaes/integrations/slf4j-logging/) for details on log levels, formatting, and custom loggers.

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
import in.rcard.yaes.*
import in.rcard.yaes.http.server.*
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
              Response(
                status = 200,
                headers = Map("Content-Type" -> "application/json"),
                body = users
              )
            },

            // Get user by ID
            GET(p"/users" / userId) { (req, id: Int) =>
              Response(
                status = 200,
                headers = Map("Content-Type" -> "application/json"),
                body = s"""{"id": $id, "name": "User $id"}"""
              )
            },

            // Search users with query parameter
            GET(p"/users/search" ? queryParam[String]("q")) { req =>
              val query = req.queryParam("q").getOrElse("")
              Response(
                status = 200,
                headers = Map("Content-Type" -> "application/json"),
                body = s"""{"query": "$query", "results": []}"""
              )
            },

            // Create new user
            POST(p"/users") { req =>
              // In real app, parse req.body and save to database
              val newUserId = 123
              Response(
                status = 201,
                headers = Map(
                  "Content-Type" -> "application/json",
                  "Location" -> s"/users/$newUserId"
                ),
                body = s"""{"id": $newUserId, "name": "New User"}"""
              )
            },

            // Update user
            PUT(p"/users" / userId) { (req, id: Int) =>
              Response(
                status = 200,
                headers = Map("Content-Type" -> "application/json"),
                body = s"""{"id": $id, "name": "Updated User"}"""
              )
            },

            // Delete user
            DELETE(p"/users" / userId) { (req, id: Int) =>
              Response.noContent()  // 204 No Content
            },

            // Get user posts with pagination
            GET(p"/users" / userId / "posts" ? queryParam[Option[Int]]("page")) { (req, uid: Int) =>
              val page = req.queryParam("page").flatMap(_.toIntOption).getOrElse(1)
              Response(
                status = 200,
                headers = Map("Content-Type" -> "application/json"),
                body = s"""{"userId": $uid, "page": $page, "posts": []}"""
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
