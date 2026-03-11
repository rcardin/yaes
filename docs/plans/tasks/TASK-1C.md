# TASK-1C | Update server + circe imports

status: [x]
requires: 1B
verify: `sbt server/test circe/test`
gate: PHASE 1

## Action

Grep and replace in all `.scala` files under `yaes-http/server/src/` and `yaes-http/circe/src/`:

| old | new |
|-----|-----|
| `in.rcard.yaes.http.server.BodyCodec` | `in.rcard.yaes.http.core.BodyCodec` |
| `in.rcard.yaes.http.server.DecodingError` | `in.rcard.yaes.http.core.DecodingError` |
| `in.rcard.yaes.http.server.Method` | `in.rcard.yaes.http.core.Method` |

Also check for wildcard imports like `import in.rcard.yaes.http.server.*` or `server._` that pulled these types in — those may need an additional `import in.rcard.yaes.http.core.*`.

Test logic must NOT change — only imports.
