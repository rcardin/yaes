## yaes-http server

HTTP server implementation for λÆS.

### Route DSL

```scala
import io.yaes.http.server.*

// Simple route (no params) — handler receives just the request
val route1 = GET(p"/users") { req => Response.ok("Users") }

// Path parameters — declared with param[Type]("name"), read by name from the path named tuple
val userId = param[Int]("userId")
val route2 = GET(p"/users" / userId) { (req, path, _) =>
  Response.ok(s"User ${path.userId}")
}

// Query parameters — declared with queryParam[Type]("name"), read by name from the query named tuple
val route3 = GET(p"/search" ? queryParam[String]("q")) { (req, _, query) =>
  Response.ok(s"Searching for: ${query.q}")
}

// Combine routes and run the server (run requires an effect context, e.g. Log.run/Async.run)
val server = YaesServer.route(route1, route2, route3)
server.run(port = 8080)
```

Path/query parameters are encoded as `scala.NamedTuple`s. Handlers receive `(request, path, query)`
and access params by name (`path.userId`, `query.q`); ignore an unused tuple with `_`. A route with
no path and no query parameters uses the ergonomic single-argument form `{ req => ... }`.

**Route Matching Order:**
- Exact routes (no parameters) are checked first via map lookup (O(1))
- Parameterized routes are checked sequentially in definition order
- First matching route wins
- Returns 404 if no route matches

### Shutdown Behavior

- `server.shutdown()` is idempotent — safe to call multiple times
- Shutdown waits for all in-flight requests to complete before cleanup
- New requests during shutdown receive 503 Service Unavailable
- Shutdown hooks run during `Resource` cleanup, before server stops
- Structured concurrency ensures all request handler fibers complete via `Async.run`'s `StructuredTaskScope.join()`

### URL Decoding and Path Traversal Security

URL encoding can bypass naive security checks. Always **decode first, then validate**:

```scala
// ✅ CORRECT — Decode first, then validate
val decoded = try {
  URLDecoder.decode(segment, StandardCharsets.UTF_8)
} catch {
  case _: IllegalArgumentException =>
    Raise.raise(HttpParseError.MalformedPath)
}
if (decoded == ".." || decoded.contains("..")) {
  Raise.raise(HttpParseError.MalformedPath)
}

// ❌ INCORRECT — Validate before decoding (can be bypassed with %2e%2e)
if (rawPath.contains("..")) { ... }
val decoded = URLDecoder.decode(rawPath, StandardCharsets.UTF_8)
```

**Key principles:**
1. Always decode at the boundary
2. Validate after decoding
3. Never trust encoded input
4. Decode once — don't decode multiple times or at multiple layers

**Always test both literal and encoded versions** of security-sensitive patterns (e.g., `..` and `%2e%2e`).
