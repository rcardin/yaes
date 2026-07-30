package io.yaes.http.server

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import PathBuilder.given

class PathInterpolatorSpec extends AnyFlatSpec with Matchers {

  "the p interpolator" should "build a multi-segment literal path" in {
    (p"/api/v1/users").build.toPattern shouldBe "/api/v1/users"
  }

  it should "build the root path" in {
    (p"/").build.toPattern shouldBe ""
  }

  it should "reject an interpolated value at compile time" in {
    assertDoesNotCompile(
      """
        |val id = 42
        |p"/users/$id"
        |""".stripMargin
    )
  }

  it should "reject an interpolated String at compile time" in {
    assertDoesNotCompile(
      """
        |val prefix = "api"
        |p"/$prefix/v1"
        |""".stripMargin
    )
  }

  it should "still allow literal segments appended with /" in {
    (p"/api" / "v1").build.toPattern shouldBe "/api/v1"
  }
}
