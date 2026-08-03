package io.yaes.migration

import scalafix.v1._
import scala.meta._

/** Scalafix syntactic rule that migrates YAES sources from the 0.21.0 package layout
  * (`in.rcard.yaes`) to the 0.22.0 layout (`io.yaes`).
  *
  * Exactly three kinds of occurrence are rewritten, each by its own pass.
  *
  * '''1. Reference positions''' (tree pass):
  *   - `package` declarations, including sub-packages (`package in.rcard.yaes.cats`)
  *   - import statements of every style (`import in.rcard.yaes.Raise`, `import in.rcard.yaes.*`,
  *     `import in.rcard.yaes.cats.accumulate`)
  *   - fully-qualified type references in signatures and type positions (`in.rcard.yaes.Sync`)
  *
  * All of these forms contain the same three-segment `Term.Select` node whose syntax is exactly
  * `in.rcard.yaes` (as a package ref, an import ref, or the qualifier of a `Type.Select`). Matching
  * that innermost node and replacing it with `io.yaes` rewrites every case uniformly.
  *
  * '''2. String literals''' (tree pass): the prefix also travels inside string data such as
  * `Class.forName("in.rcard.yaes.Foo")` or a logger name, where no `Term.Select` exists. Every
  * `Lit.String` whose ''source text'' contains the old prefix is rewritten, covering all four
  * literal forms: plain (`"in.rcard.yaes.Foo"`), interpolated (each `s"..."` part is its own
  * `Lit.String`, so a prefix on either side of a `${...}` splice is caught), triple-quoted
  * (`"""in.rcard.yaes.Baz"""`), and literals carrying escape sequences. The match and the
  * replacement both run over `lit.syntax` (the raw source text) rather than `lit.value` (the
  * decoded value), so an escape such as `\t` survives verbatim instead of being burned into the
  * rewritten source as a real tab.
  *
  * '''3. Comments and Scaladoc''' (token pass): comments are invisible to tree visitors, so they
  * are handled by iterating the token stream and string-replacing `in.rcard.yaes` inside every
  * `Token.Comment` (inline `//` comments, `/* ... */` blocks, and `/** ... */` Scaladoc, including
  * `{{{ }}}` code examples).
  *
  * All three passes are idempotent: a migrated source contains no `in.rcard.yaes` node, string, or
  * comment left to match.
  *
  * '''Not covered.''' The rule only sees the file it is given, so occurrences outside Scala source
  * text are left alone: resource files, build definitions, `META-INF/services` entries, and any
  * prefix assembled at runtime from separate fragments (for example `"in.rcard" + ".yaes.Foo"`,
  * where no single literal holds the whole prefix).
  */
class MigrateV021ToV022 extends SyntacticRule("MigrateV021ToV022") {

  private val OldPrefix = "in.rcard.yaes"
  private val NewPrefix = "io.yaes"

  /** Applies the package rename to a single source file, covering reference nodes, string literals,
    * and comments.
    *
    * @param doc
    *   the syntactic document to rewrite, supplying its parsed tree and token stream
    * @return
    *   a [[scalafix.v1.Patch]] that replaces every `in.rcard.yaes` occurrence (in references,
    *   string literals, and comments) with `io.yaes`, or an empty patch when the source has no such
    *   occurrence
    */
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val treePatch =
      doc.tree.collect {
        case ref: Term.Select if ref.syntax == OldPrefix =>
          Patch.replaceTree(ref, NewPrefix)
      }.asPatch

    val stringPatch =
      doc.tree.collect {
        case lit @ Lit.String(_) if lit.syntax.contains(OldPrefix) =>
          Patch.replaceTree(lit, lit.syntax.replace(OldPrefix, NewPrefix))
      }.asPatch

    val commentPatch =
      doc.tokens.collect {
        case comment: Token.Comment if comment.value.contains(OldPrefix) =>
          val updated = comment.text.replace(OldPrefix, NewPrefix)
          Patch.replaceToken(comment, updated)
      }.asPatch

    treePatch + stringPatch + commentPatch
  }
}
