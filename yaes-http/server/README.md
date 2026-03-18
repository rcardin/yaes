![Made for Scala 3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/rcardin/yaes/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/in.rcard.yaes/yaes-http-server_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/rcardin/yaes)
[![javadoc](https://javadoc.io/badge2/in.rcard.yaes/yaes-http-server_3/javadoc.svg)](https://javadoc.io/doc/in.rcard.yaes/yaes-http-server_3)
<br/>

# λÆS HTTP Server

Type-safe HTTP/1.1 server built on YAES effects and virtual threads.

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "in.rcard.yaes" %% "yaes-http-server" % "0.15.0"
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
import in.rcard.yaes.*
import in.rcard.yaes.Log.given
import in.rcard.yaes.http.server.*
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
  // Single parameter
  GET(p"/users" / userId) { (req, id: Int) =>
    Response.ok(s"User $id")
  },

  // Multiple parameters
  GET(p"/users" / userId / "posts" / postId) { (req, uid: Int, pid: Long) =>
    Response.ok(s"User $uid, Post $pid")
  },

  // String parameters
  GET(p"/hello" / username) { (req, name: String) =>
    Response.ok(s"Hello, $name!")
  }
)
```

**Supported types:** `String`, `Int`, `Long`, `Boolean`, `Double`

Path parameters are automatically URL-decoded (e.g., `/users/john%20doe` → `"john doe"`).

### Query Parameters

Define typed query parameters using `queryParam[Type]("name")`:

```scala
val routes = Routes(
  // Single query parameter
  GET(p"/search" ? queryParam[String]("q")) { req =>
    val query = req.queryParam("q").get
    Response.ok(s"Searching for: $query")
  },

  // Multiple query parameters
  GET(p"/search" ? queryParam[String]("q") & queryParam[Int]("limit")) { req =>
    val query = req.queryParam("q").get
    val limit = req.queryParam("limit").map(_.toInt).getOrElse(10)
    Response.ok(s"Searching for: $query (limit: $limit)")
  },

  // Optional query parameters
  GET(p"/search" ? queryParam[Option[Int]]("page")) { req =>
    val page = req.queryParam("page").flatMap(_.toIntOption).getOrElse(1)
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
  GET(p"/users" / userId ? queryParam[String]("include")) { (req, id: Int) =>
    val include = req.queryParam("include").getOrElse("basic")
    Response.ok(s"User $id with $include data")
  }
)
```

### HTTP Methods

Supported methods: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`

```scala
val routes = Routes(
  GET(p"/users") { req => Response.ok("List users") },
  POST(p"/users") { req => Response.created("User created") },
  PUT(p"/users" / userId) { (req, id: Int) => Response.ok(s"Updated user $id") },
  DELETE(p"/users" / userId) { (req, id: Int) => Response.ok(s"Deleted user $id") },
  PATCH(p"/users" / userId) { (req, id: Int) => Response.ok(s"Patched user $id") }
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
req.queryParam("q") // Option[String]
req.body            // Request body as String
```

**Header Handling:** All header names are stored in lowercase for HTTP/1.1 compliance. `req.header("Content-Type")` and `req.header("content-type")` return the same value.

### Response

```scala
case class Response(
  status: Int,
  headers: Map[String, String] = Map.empty,
  body: String = ""
)

// Helper constructors
Response.ok(body)                      // 200
Response.created(body)                 // 201
Response.noContent()                   // 204
Response.badRequest(message)           // 400
Response.notFound(message)             // 404
Response.internalServerError(message)  // 500

// Custom response
Response(
  status = 201,
  headers = Map("Location" -> "/users/123"),
  body = """{"id": 123, "name": "Alice"}"""
)
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
      val serverFiber = Async.fork("server") {
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
}
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
}
```

### Shutdown on JVM Termination

The `Shutdown` effect automatically registers JVM shutdown hooks to handle:
- SIGTERM signals
- SIGINT (Ctrl+C)
- JVM shutdown

This ensures graceful shutdown even when the process is killed.

## Body Codecs (JSON, etc.)

The server uses the `BodyCodec[A]` typeclass for automatic body encoding/decoding. Built-in codecs exist for `String`, `Int`, `Long`, `Double`, and `Boolean`.

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

To use JSON, implement `BodyCodec[A]` for your types:

```scala
// Example with circe (not included)
import io.circe.{Encoder, Decoder}
import io.circe.syntax.*
import io.circe.parser.decode

case class User(id: Int, name: String)

given BodyCodec[User] with {
  def contentType: String = "application/json"
  def encode(user: User): String = user.asJson.noSpaces
  def decode(body: String): User raises DecodingError =
    decode[User](body).fold(
      error => Raise.raise(DecodingError(error.getMessage)),
      user => user
    )
}

// Use in routes
GET(p"/users" / userId) { (req, id: Int) =>
  val user = User(id, "Alice")
  Response.ok(user)  // Automatically encoded to JSON with application/json Content-Type
}

POST(p"/users") { req =>
  Raise.fold {
    val user = req.as[User]  // Automatically decoded from JSON
    Response.created(user)
  } { case error: DecodingError =>
    Response.badRequest(error.message)
  }
}
```

Note: JSON libraries (circe, upickle, zio-json, etc.) are not included. Choose your preferred library and implement the `BodyCodec` trait.

## Examples

### Complete Server Example

```scala
import in.rcard.yaes.http.server.*
import in.rcard.yaes.*
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

object MyServer extends App {
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
            Response(
              status = 200,
              headers = Map("Content-Type" -> "application/json"),
              body = """[{"id": 1, "name": "Alice"}]"""
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

          // Search with query parameter
          GET(p"/search" ? queryParam[String]("q")) { req =>
            val query = req.queryParam("q").get
            Response.ok(s"Searching for: $query")
          },

          // Create user
          POST(p"/users") { req =>
            // Parse req.body and create user...
            Response(
              status = 201,
              headers = Map(
                "Content-Type" -> "application/json",
                "Location" -> "/users/123"
              ),
              body = """{"id": 123, "name": "New User"}"""
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
sbt "server/testOnly in.rcard.yaes.http.server.HttpParserSpec"

# Run specific test
sbt "server/testOnly in.rcard.yaes.http.server.YaesServerSpec -- -z \"start and accept\""
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
