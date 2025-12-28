package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PathPatternSpec extends AnyFlatSpec with Matchers {

  "PathPattern" should "parse a path with only literal segments" in {
    val pattern = PathPattern.parse("/users/admin")
    pattern.segments shouldBe List(Literal("users"), Literal("admin"))
    pattern.isExact shouldBe true
  }

  it should "parse a path with single parameter" in {
    val pattern = PathPattern.parse("/users/:id")
    pattern.segments shouldBe List(Literal("users"), Parameter("id"))
    pattern.isExact shouldBe false
  }

  it should "parse a path with multiple parameters" in {
    val pattern = PathPattern.parse("/users/:userId/posts/:postId")
    pattern.segments shouldBe List(
      Literal("users"),
      Parameter("userId"),
      Literal("posts"),
      Parameter("postId")
    )
  }

  it should "match an exact literal path" in {
    val pattern = PathPattern.parse("/users/admin")
    pattern.matches("/users/admin") shouldBe Some(Map.empty)
    pattern.matches("/users/other") shouldBe None
  }

  it should "match and extract single parameter" in {
    val pattern = PathPattern.parse("/users/:id")
    pattern.matches("/users/123") shouldBe Some(Map("id" -> "123"))
    pattern.matches("/users/abc") shouldBe Some(Map("id" -> "abc"))
  }

  it should "match and extract multiple parameters" in {
    val pattern = PathPattern.parse("/users/:userId/posts/:postId")
    pattern.matches("/users/42/posts/99") shouldBe Some(
      Map("userId" -> "42", "postId" -> "99")
    )
  }

  it should "return None for mismatched literal segments" in {
    val pattern = PathPattern.parse("/users/:id")
    pattern.matches("/posts/123") shouldBe None
  }

  it should "return None for different segment counts" in {
    val pattern = PathPattern.parse("/users/:id")
    pattern.matches("/users/123/extra") shouldBe None
    pattern.matches("/users") shouldBe None
  }

  it should "handle paths with leading/trailing slashes consistently" in {
    val pattern = PathPattern.parse("/users/:id")
    pattern.matches("/users/123") shouldBe Some(Map("id" -> "123"))
  }

  it should "handle root paths" in {
    val pattern = PathPattern.parse("/")
    pattern.segments shouldBe List.empty
    pattern.matches("/") shouldBe Some(Map.empty)
  }

  it should "handle paths with only parameters" in {
    val pattern = PathPattern.parse("/:category/:id")
    pattern.matches("/books/123") shouldBe Some(Map("category" -> "books", "id" -> "123"))
  }

  // Validation tests

  it should "reject patterns with empty parameter names" in {
    val exception = intercept[IllegalArgumentException] {
      PathPattern.parse("/users/:")
    }
    exception.getMessage should include("parameter name cannot be empty")
  }

  it should "reject patterns with duplicate parameter names" in {
    val exception = intercept[IllegalArgumentException] {
      PathPattern.parse("/users/:id/posts/:id")
    }
    exception.getMessage should include("duplicate parameter names")
    exception.getMessage should include("id")
  }

  it should "accept patterns with different parameter names" in {
    val pattern = PathPattern.parse("/users/:userId/posts/:postId")
    pattern.segments.size shouldBe 4
  }
}
