## yaes-http server

HTTP server implementation for λÆS.

### Route DSL

```scala
import in.rcard.yaes.http.server.*

// Simple route
val route1 = GET / "users" -> { req => Response.ok("Users") }

// Route with path parameters (use *: for type-safe extraction)
val route2 = GET / "users" / *:[Int] -> { (req, userId) =>
  Response.ok(s"User $userId")
}

// Route with query parameters
val route3 = GET / "search" ? "q" *: StringParam -> { (req, query) =>
  Response.ok(s"Searching for: $query")
}

// Combine routes and run server
val routes = Routes(route1, route2, route3)
val server = YaesServer(routes)
  .onShutdown(() => println("Cleanup"))
  .run(port = 8080)
```

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
