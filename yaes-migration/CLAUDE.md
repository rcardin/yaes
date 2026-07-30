## yaes-migration

Scalafix syntactic migration rules, published as `io.yaes:yaes-migration_2.13`. Consumers add a single line to their build (`scalafixDependencies += "io.yaes" %% "yaes-migration"`) and run the rules against their own YAES sources. Sources live under `yaes-migration/rules/`.

### Why This Module Is Scala 2.13 (not `_3`)

Every other YAES module targets Scala 3, but this one is compiled with Scala 2.13 on purpose. External Scalafix rules (those pulled in via `scalafixDependencies`) are loaded by the `scalafix-reflect_2.13` layer, so `scalafixDependencies += "io.yaes" %% "yaes-migration"` resolves `_2.13` regardless of the consumer's own Scala version. A `_3` build does compile (Scalafix's own `scalafix-rules_3` does the same via `for3Use2_13`), but it rides inside the CLI classpath and is not reachable as an external rule, so publishing `_3` would break that one-line setup. The `scalaVersion` override and the rationale live in the `yaes-migration` section of the root `build.sbt`.

### How a Rule Rewrites Code

`MigrateV021ToV022` migrates the 0.21.0 package layout (`in.rcard.yaes`) to the 0.22.0 layout (`io.yaes`). It works in two passes because comments are invisible to a syntax tree:

1. **Tree pass.** Package declarations, imports of every style, and fully-qualified type references all contain the same three-segment `Term.Select` node whose syntax is exactly `in.rcard.yaes`. Matching that innermost node and replacing it with `io.yaes` rewrites every reference case uniformly.
2. **String pass.** The prefix also travels inside string data (`Class.forName("in.rcard.yaes.Foo")`, logger names), where no `Term.Select` exists. Every `Lit.String` whose source text contains the old prefix is rewritten, covering plain, interpolated (one `Lit.String` per `s"..."` part), triple-quoted, and escape-carrying literals. Both the guard and the replacement use `lit.syntax` (raw source text), never `lit.value` (decoded), so an escape such as `\t` is not burned into the output as a real tab.
3. **Comment pass.** A separate iteration over the token stream string-replaces the old prefix inside every `Token.Comment`, covering inline `//` comments, `/* ... */` blocks, and `/** ... */` Scaladoc (including `{{{ }}}` code examples).

All three passes are idempotent: a migrated source has no matching tree node, string, or comment left.

Not covered: anything outside the Scala source text handed to the rule (resource files, build definitions, service files) and prefixes assembled at runtime from separate fragments, where no single literal holds the whole prefix.

### Adding a Future Migration Rule

1. Add a new rule class under `rules/src/main/scala/io/yaes/migration/` extending `SyntacticRule` (or `SemanticRule` if it needs symbol information).
2. Register it in `rules/src/main/resources/META-INF/services/scalafix.v1.Rule` (one fully-qualified class name per line). Scalafix discovers rules through this service file, so an unregistered rule will not load.

### Tests

Tests use Scalafix testkit's `AbstractSyntacticRuleSuite`. Each case is a pair of resource files under `rules/src/test/resources/migration/<name>/`: `input.scala` is fed to the rule and the result is compared against `output.scala`. `RuleSuite` also re-runs every case as `output` against `output`, so each pair doubles as an idempotency check. To add a case, drop a new `input.scala`/`output.scala` pair and a `checkPair("<name>")` call in `RuleSuite`.
