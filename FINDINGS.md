# Scalafix Migration Rule Findings (issue #298 chain: #299/#300/#301)

## Completed
- #299: `yaes-migration` module scaffold + package declaration rename. DONE.
  - Module at `yaes-migration/rules`, published `moduleName := "yaes-migration"`.
  - Rule `io.yaes.migration.MigrateV021ToV022` (SyntacticRule), registered in
    `META-INF/services/scalafix.v1.Rule`.
  - `sbt "yaes-migration/test"` = 4 green (package-rename, sub-package-rename,
    each + idempotency).
  - publishLocal round-trip verified: throwaway Scala 3 project with
    `scalafixDependencies += "io.yaes" %% "yaes-migration" % <ver>` +
    `sbt "scalafix MigrateV021ToV022"` renamed `package in.rcard.yaes` →
    `package io.yaes`. WORKS.

- #300: import + FQN reference transformation. DONE.
  - Unified `fix`: `doc.tree.collect` matches every `Term.Select` whose `syntax ==
    "in.rcard.yaes"` and replaces with `io.yaes`. That innermost 3-segment node is
    shared by package refs, import refs, and `Type.Select` qualifiers, so one case
    handles packages/imports/FQN uniformly and stays idempotent (migrated source
    has no such node). Replaced the old `Pkg`-only matcher.
  - New fixtures under `resources/migration/`: named-import, wildcard-import,
    sub-package-import, fqn-reference, no-op. Wired into `RuleSuite`.
  - `sbt "yaes-migration/test"` = 14 green.

- #301: comment + Scaladoc transformation. DONE.
  - `fix` now returns `treePatch + commentPatch`. `commentPatch` iterates
    `doc.tokens`, matches `Token.Comment` whose `.value` contains the old prefix,
    and `Patch.replaceToken(comment, comment.text.replace(old, new))`. Tree
    visitors never see comments, so this is a separate token pass. Idempotent
    (migrated comment has no old prefix).
  - Covers inline `//`, Scaladoc prose, and Scaladoc `{{{ }}}` code blocks in one
    string-replace, with no per-style logic needed.
  - New fixtures: scaladoc-code-example, scaladoc-prose, inline-comment.
  - `sbt "yaes-migration/test"` = 20 green.
  - This was the LAST open sub-issue of #298. All three (#299/#300/#301) DONE.

## CRITICAL: coordinate is `_2.13`, NOT `_3` (contradicts #298/#299/#300/#301 spec)
The issue text demands `io.yaes:yaes-migration_3:0.23.0`. A `_3` build COMPILES
and PUBLISHES fine, but it is NOT consumable as an external Scalafix rule, so
`_2.13` is the correct coordinate. Verified empirically (all proven, not
theory):
- `_2.13` rule + the issue's single documented line
  `scalafixDependencies += "io.yaes" %% "yaes-migration"` on a Scala 3 project →
  renamed `package in.rcard.yaes` → `package io.yaes`. WORKS.
- `_3` rule (Scala 3.8.4 + scalafix 0.14.7, `scalafix-core` via `for3Use2_13`,
  `scalafix-testkit_3.8.4`) + the same single line → sbt-scalafix fetches
  `yaes-migration_2.13` and FAILS ("Failed to fetch ... yaes-migration_2.13").
- `_3` rule + `scalafixScalaBinaryVersion := "3"` → STILL fetches `_2.13`.
Why: external rules (via `scalafixDependencies`) are loaded by the
`scalafix-reflect_2.13` layer: resolution is 2.13 by design. Scalafix DOES
support Scala 3, and it ships `scalafix-rules_3` (its OWN rules, compiled with
Scala 3 via `for3Use2_13` against `scalafix-core_2.13`), but those ride INSIDE
`scalafix-cli_3` on the main classpath, not through the external-rule mechanism.
So "Scalafix supports Scala 3" ≠ "external rules ship as `_3`".
There is no `scalafix-core_3` at all (404 at every version); Scala 3 scalafix
support (testkit_3.8.4, cli_3.8.4) starts at 0.14.7 for Scala 3.8.4 / 3.3.x LTS.
Conclusion: the module MUST publish as `io.yaes:yaes-migration_2.13`, the
ecosystem standard for ALL external scalafix rules, and what satisfies the
issue's user story #7 (single build.sbt line). `_3` should be struck from
#300/#301 and parent #298. Optionally cross-publish `_2.12` for extra reach
(the developer-setup doc: "cross-publish for all Scala binary versions for which
scalafix-core is available" = 2.13 + 2.12). Flagged to user; awaiting ack before
#300/#301.

## Design decisions for #300/#301
- Module Scala version: `2.13.16` (`scalafixRuleScalaVersion` in root build.sbt).
  scalafix `0.14.3`. Both pinned as `lazy val`s near the `yaes-migration` def.
- Framework: `AbstractSyntacticRuleSuite` (as spec requires). Its API is MANUAL:
  `check(rule, name, original, expected)`. It does NOT auto-discover
  input/output dirs. `runAllTests()` / directory discovery lives only on
  `AbstractSemanticRuleSuite` (needs semanticdb). So we did NOT use
  `ScalafixTestkitPlugin` / projectmatrix / input+output sbt projects.
- Test fixtures: pairs of resource files under
  `yaes-migration/rules/src/test/resources/migration/<case>/{input,output}.scala`,
  loaded via `Source.fromResource` and fed to `check`. Add new pairs here for
  #300 (imports, FQN) and #301 (comments/scaladoc). `check` also gives us free
  idempotency tests (apply rule to `output`, expect `output`).
- Rule extension point: `MigrateV021ToV022.fix` currently `doc.tree.collect`s
  `Pkg` nodes whose `ref.syntax` starts with `in.rcard.yaes`. #300 adds
  `Importer`/FQN `Term.Select`/`Type.Select` cases; #301 iterates
  `doc.tokens` for `Token.Comment` and string-replaces inside comment text
  (tree visitors don't see comments).
- Testkit `check` compares EXACT output text; keep fixture whitespace/newlines
  matching what the patch produces.

# Migration Findings (issue #67)

## Completed
- #288: build.sbt + CI slug done. organization=io.yaes, homepage=github.com/yaes-io/yaes, codecov slug=yaes-io/yaes.
- #290: yaes-core migrated. All 39 files moved from in/rcard/yaes → io/yaes. 322 tests pass.
- #292: yaes-data migrated. 3 source + 14 test files moved to io/yaes. 295 tests pass.
- #291: yaes-cats migrated. 13 source + 6 test files moved to io/yaes. Root package object converted to top-level definitions. 70 tests pass.
- #293: yaes-slf4j + yaes-test migrated. 2 slf4j sources + 1 test, 4 core-scalatest sources + 4 tests, 2 http-scalatest sources + 1 test moved to io/yaes. 38 tests pass.
- #294: yaes-http migrated. 68 source + test files across core, server, client, circe, jsoniter sub-modules moved to io/yaes. 256 tests pass.

## Task order (dependency chain)
1. #288 build.sbt + CI: DONE
2. #290 yaes-core: DONE
3. #292 yaes-data: DONE
4. #291 yaes-cats: DONE
5. #293 yaes-slf4j + yaes-test: DONE
6. #294 yaes-http (server, client, jsoniter, circe): DONE
7. #289 README + docs: DONE

## Migration pattern per module
- Move files: `src/main/scala/in/rcard/yaes/` → `src/main/scala/io/yaes/`
- Move files: `src/test/scala/in/rcard/yaes/` → `src/test/scala/io/yaes/`
- Sed replace `package in.rcard.yaes` → `package io.yaes` in all .scala files
- Sed replace `import in.rcard.yaes` → `import io.yaes` in all .scala files
- Delete old directory tree after move

## Cross-module imports
Some modules import from each other, so after moving yaes-core, check that
dependent modules' `import in.rcard.yaes.*` statements also get updated when
their turn comes.

## io.yaes namespace collision pitfalls
- In files under `io.yaes.*`, writing `cats.data.X` resolves to `io.yaes.cats` sub-package, not `_root_.cats`. Use `import _root_.cats.data.X` explicitly.
- In extension methods where the receiver parameter is named `io`, writing `io.yaes.Sync` fails. Use the import alias (e.g., `YaesSync`) or `_root_.io.yaes.Sync`.
