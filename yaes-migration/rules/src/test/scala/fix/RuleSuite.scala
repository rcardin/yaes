package fix

import io.yaes.migration.MigrateV021ToV022
import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.AbstractSyntacticRuleSuite

import scala.io.Source

/** Testkit suite for [[io.yaes.migration.MigrateV021ToV022]]. Each case is a pair of `.scala`
  * resource files under `migration/<name>/`: `input.scala` is fed to the rule and the result is
  * compared against `output.scala`.
  */
class RuleSuite extends AbstractSyntacticRuleSuite with AnyFunSuiteLike {

  private val rule = new MigrateV021ToV022()

  private def load(path: String): String = {
    val source = Source.fromResource(path)(scala.io.Codec.UTF8)
    try source.mkString
    finally source.close()
  }

  private def checkPair(name: String): Unit = {
    val input  = load(s"migration/$name/input.scala")
    val output = load(s"migration/$name/output.scala")
    check(rule, name, input, output)
    // Idempotency: applying the rule to already-migrated source is a no-op.
    check(rule, s"$name (idempotent)", output, output)
  }

  checkPair("package-rename")
  checkPair("sub-package-rename")
  checkPair("named-import")
  checkPair("braced-import")
  checkPair("wildcard-import")
  checkPair("sub-package-import")
  checkPair("fqn-reference")
  checkPair("scaladoc-code-example")
  checkPair("scaladoc-prose")
  checkPair("inline-comment")
  checkPair("block-comment")
  checkPair("string-literal")
  checkPair("interpolated-string")
  checkPair("triple-quoted-string")
  checkPair("escaped-string")
  checkPair("no-op")
}
