# FINDINGS

## TASK-4: Integration Tests

### Union Type Raise Issue

`Raise.either[E, A]` requires **two** type parameters. The task spec showed `Raise.either[A | B | C] { ... }` with one type arg — this does not compile.

More importantly, when inside `Raise.either[ConnectionError | HttpError | DecodingError, T]`, Scala implicit search does NOT automatically provide `Raise[ConnectionError]` or `Raise[HttpError | DecodingError]` from the union `Raise[ConnectionError | HttpError | DecodingError]` — even though by variance the subtype relationship holds.

### Solution Pattern

Use nested `Raise.either` calls and combine results manually:

```scala
Raise.either[ConnectionError, HttpResponse] { client.send(request) }
  .left.map(e => e: ConnectionError | HttpError | DecodingError)
  .flatMap { resp =>
    Raise.either[HttpError | DecodingError, A] { resp.as[A] }
      .left.map(e => e: ConnectionError | HttpError | DecodingError)
  }
```

This produces `Either[ConnectionError | HttpError | DecodingError, A]` as intended.

### ExecutionContext Required

`Sync.runBlocking` has `(implicit ec: ExecutionContext)`. Tests must import:
```scala
import scala.concurrent.ExecutionContext.Implicits.global
```
