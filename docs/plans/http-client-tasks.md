# HTTP Client — Task Index

Spec: `docs/plans/http-client-specs.md`
Tasks: `docs/plans/tasks/TASK-*.md`

## Protocol

1. Read this file
2. Find next task: first `[ ]` in Status whose `requires` are all `[x]`
3. Read `docs/plans/tasks/TASK-{id}.md` — it has everything needed to implement
4. Implement, then run the `verify` command
5. If task is a gate, also run the gate command
6. On success: update Status below (`[ ]` → `[x]`) and `status:` in the task file
7. On failure: do NOT update status — fix the issue and re-verify

## Status

```
1A:[x] 1B:[x] 1C:[x] 2A:[x] 2B:[x] 2C:[x] 2D:[x] 3A:[x] 3B:[x] 4:[x]
```

## Tasks

| id | title | requires | verify |
|----|-------|----------|--------|
| 1A | build.sbt + dirs | — | `sbt compile` |
| 1B | Move types to core | 1A | `sbt core/compile` |
| 1C | Update imports | 1B | `sbt server/test circe/test` |
| 2A | Enums + config | 1A | `sbt client/testOnly *YaesClientConfigSpec` |
| 2B | Error ADTs | 1A | `sbt client/testOnly *HttpErrorSpec` |
| 2C | HttpRequest | 1B | `sbt client/testOnly *HttpRequestSpec` |
| 2D | HttpResponse | 1B, 2B | `sbt client/testOnly *HttpResponseSpec` |
| 3A | TestServer + make | 2A, 2B, 2C, 2D | `sbt client/testOnly *YaesClientSpec` |
| 3B | YaesClient.send | 3A | `sbt client/testOnly *YaesClientSendSpec` |
| 4 | Integration tests | 3B | `sbt client/testOnly *YaesClientIntegrationSpec` |

## Gates

- PHASE 1 (after 1C): `sbt server/test circe/test`
- PHASE 2 (after 2D): `sbt client/test`
- PHASE 3 (after 3B): `sbt client/test`
- FINAL (after 4): `sbt server/test circe/test client/test`

## Parallel groups

- After 1A: {1B, 2A, 2B}
- After 1B: {1C, 2C} — 2D also needs 2B
