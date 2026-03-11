# FINDINGS

## TASK-1B (completed)

- After deleting `BodyCodec.scala` and `DecodingError.scala` from `server`, the server module no longer compiles — this is intentional, TASK-1C will fix imports.
- `Request.scala` still references `BodyCodec` and `DecodingError` (via the `as[A]` extension method) — these need to be imported from `in.rcard.yaes.http.core` in TASK-1C.
- All other server files that used `BodyCodec` or `DecodingError` (e.g., `Response.scala`, `Routes.scala`, circe integration) will also need import updates in TASK-1C.
- The `Method` type is now in `in.rcard.yaes.http.core.Method`. `MethodDSL.scala` and any other server file using `Method` will need `import in.rcard.yaes.http.core.Method` added in TASK-1C.
- SBT project names: use `core`, `server`, `client` (not path-based names). See yaes-http/CLAUDE.md.

## Next tasks available after 1B

- **1C**: Update imports in server and circe modules to use `in.rcard.yaes.http.core` types.
- **2A**: Enums + config (requires only 1A — already done)
- **2B**: Error ADTs (requires only 1A — already done)
- **2C**: HttpRequest (requires 1B)
- **2D**: HttpResponse (requires 1B and 2B)

## TASK-1C (completed)

- Main source files in `server/src/main/` also needed import updates (not just test files).
  Files changed: Response.scala, Routes.scala, MethodDSL.scala, Request.scala, routing/TypedRoute.scala, parsing/HttpParser.scala
- Test files in the `in.rcard.yaes.http.server` package (no wildcard import) also needed core imports:
  BodyCodecSpec.scala, RoutesSpec.scala
- Test files with `import in.rcard.yaes.http.server.*` needed `import in.rcard.yaes.http.core.Method` added.

## Next tasks available after 1C (PHASE 1 gate cleared)

- **2A**: Enums + config for http client (requires 1A)
- **2B**: Error ADTs (requires 1A)
- **2C**: HttpRequest (requires 1B)
- **2D**: HttpResponse (requires 1B and 2B)

## TASK-2C (HttpRequest) — completed

- `BodyCodec` built-in instances return `"text/plain; charset=UTF-8"` (confirmed by server BodyCodecSpec/ResponseSpec tests). The TASK-2C spec test had incorrect expected values (`"text/plain"`); fixed to `"text/plain; charset=UTF-8"`.
- `HttpRequest` extension methods (`header`, `queryParam`, `timeout`) live in the companion object using Scala 3 `extension` syntax — consistent with project patterns.

## TASK-2D (HttpResponse) — completed

- Task spec shows `Raise.either[HttpError | DecodingError] { ... }` with one type arg, but the actual signature is `Raise.either[E, A]` — two type params required. Correct form: `Raise.either[HttpError | DecodingError, Int] { ... }`.
- Union error type `Raise[HttpError | DecodingError]` works without special handling via contravariance of `Raise[-E]`.
- PHASE 2 gate (`sbt client/test`) passed after 2D.

## TASK-3B (YaesClient.send) — completed

- **`ofByteArray` instead of `ofString`**: `JHttpRequest.BodyPublishers.ofString(body)` may cause charset issues; `ofByteArray(body.getBytes(UTF_8))` avoids this.
- **URISyntaxException in `buildUri`**: Must be inside the `try/catch` block. If `buildUri` is called before `try`, `URISyntaxException` propagates unhandled instead of being mapped to `ConnectionError.MalformedUrl`.
- **Test spec adjustments needed** (the task spec had several issues):
  - `import HttpRequest.*` conflicts with `HttpResponse.header(name)` extension — remove the wildcard import (companion extensions are in implicit scope anyway).
  - `Sync.runBlocking` requires `implicit ec: ExecutionContext` — add global EC import.
  - `Sync.runBlocking` returns `Try[A]` — call `.get` to propagate `TestFailedException`.
  - `Raise.either[E]` needs two type args: `Raise.either[E, A]`.
  - `BodyCodec[String].contentType` is `"text/plain; charset=UTF-8"` — test should expect that, not `"text/plain"`.
- PHASE 3 gate (`sbt client/test`) passed after 3B.
