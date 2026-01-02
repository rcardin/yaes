# yaes-http-server

Virtual thread-based HTTP server foundation for YAES (Yet Another Effect System).

## Overview

`yaes-http-server` provides a simple, effect-based HTTP server using JDK's `HttpServer` with virtual threads for request handling. Each incoming request is automatically handled in its own fiber via `Async.fork`, integrating seamlessly with YAES effects.

## Features

- **Virtual Thread Per Request**: Each request runs in a virtual thread (fiber)
- **Effect-Based Handlers**: Handlers use YAES effect contexts (IO, Async, etc.)
- **Simple API**: Define routes with method + path + handler triples
- **Automatic Error Handling**: Exceptions become 500 responses, unmatched routes become 404s
- **Zero Dependencies**: Only depends on yaes-core and JDK HttpServer

## Quick Start

```scala
import in.rcard.yaes.http.server.*
import scala.concurrent.ExecutionContext.Implicits.global

object MyServer extends App {
  val server = YaesServer.route(
    (Method.GET, "/hello", (req: Request) =>
      Response.ok("Hello, World!")
    ),

    (Method.POST, "/echo", (req: Request) =>
      Response.ok(req.body)
    )
  )

  Async.run {
    IO.run {
      YaesServer.run(server, port = 8080)
    }
  }
}
```

## API

### Request

```scala
case class Request(
  method: Method,
  path: String,
  headers: Map[String, String],
  body: String
)
```

### Response

```scala
case class Response(
  status: Int,
  headers: Map[String, String] = Map.empty,
  body: String = ""
)

// Helper methods
Response.ok(body)
Response.created(body)
Response.noContent()
Response.badRequest(message)
Response.notFound(message)
Response.internalServerError(message)
```

### HTTP Methods

```scala
enum Method:
  case GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
```

### Server Definition

```scala
// Define routes
val server = YaesServer.route(
  (Method.GET, "/path", handler),
  (Method.POST, "/other", handler),
  // ...
)

// Run server (requires IO and Async contexts)
YaesServer.run(server, port = 8080)
```

## Graceful Shutdown

The server supports graceful shutdown with automatic request tracking:

```scala
Async.run {
  IO.run {
    Output.run {
      val server = YaesServer.route(
        (Method.GET, "/work", (req: Request) =>
          Async.delay(5.seconds)
          Response.ok("Done")
        )
      ).run(port = 8080)

      // Later, when you want to shut down:
      server.shutdown()
      // Server waits for all in-flight requests to complete
    }
  }
}
```

**How it works:**
- All in-flight requests are tracked automatically
- Calling `shutdown()` signals the server to stop
- The server waits for all active request handlers to complete
- Shutdown progress is logged to console
- Guaranteed by YAES structured concurrency (no race conditions)

**Example output:**
```
[YaesServer] Shutdown initiated. Active requests: 3
[YaesServer] Waiting for requests to complete. Remaining: 2
[YaesServer] Waiting for requests to complete. Remaining: 1
[YaesServer] Shutdown complete. All requests finished.
```

## Examples

See [ExampleServer.scala](src/test/scala/in/rcard/yaes/http/server/ExampleServer.scala) for a runnable example:

```bash
sbt "yaes-http-server/Test/runMain in.rcard.yaes.http.server.ExampleServer"
```

Then visit:
- `http://localhost:8080/hello`
- `http://localhost:8080/api/status`

## Testing

```bash
# Run all tests
sbt yaes-http-server/test

# Run specific test suite
sbt "yaes-http-server/testOnly in.rcard.yaes.http.server.RouterSpec"
```

## Future Enhancements

- Path parameters (`/users/:id`)
- Query string parsing
- Request body parsing (JSON, forms)
- **Streaming with Flow[Byte]** - Use YAES Flow for efficient streaming
- Middleware/interceptors
- SSL/TLS support

## License

Apache 2.0
