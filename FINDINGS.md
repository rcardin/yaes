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
