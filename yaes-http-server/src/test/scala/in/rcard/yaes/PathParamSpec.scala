package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PathParamSpec extends AnyFlatSpec with Matchers {

  "Request.pathParam" should "extract String parameters" in {
    val req = Request(Method.GET, "/users/123", Map.empty, "", Map("id" -> "123"))

    val result = Raise.either {
      req.pathParam[String]("id")
    }

    result shouldBe Right("123")
  }

  it should "extract Int parameters" in {
    val req = Request(Method.GET, "/users/123", Map.empty, "", Map("id" -> "123"))

    val result = Raise.either {
      req.pathParam[Int]("id")
    }

    result shouldBe Right(123)
  }

  it should "extract Long parameters" in {
    val req =
      Request(Method.GET, "/items/9999999999", Map.empty, "", Map("id" -> "9999999999"))

    val result = Raise.either {
      req.pathParam[Long]("id")
    }

    result shouldBe Right(9999999999L)
  }

  it should "raise InvalidType for invalid Int conversion" in {
    val req = Request(Method.GET, "/users/abc", Map.empty, "", Map("id" -> "abc"))

    val result = Raise.either {
      req.pathParam[Int]("id")
    }

    result match {
      case Left(PathParamError.InvalidType("id", "abc", "Int")) => succeed
      case _                                                     => fail("Expected InvalidType error")
    }
  }

  it should "raise InvalidType for invalid Long conversion" in {
    val req = Request(Method.GET, "/items/xyz", Map.empty, "", Map("id" -> "xyz"))

    val result = Raise.either {
      req.pathParam[Long]("id")
    }

    result.isLeft shouldBe true
  }

  it should "raise MissingParam for non-existent parameter" in {
    val req = Request(Method.GET, "/users/123", Map.empty, "", Map.empty)

    val result = Raise.either {
      req.pathParam[String]("id")
    }

    result match {
      case Left(PathParamError.MissingParam("id")) => succeed
      case _                                       => fail("Expected MissingParam error")
    }
  }

  it should "extract multiple parameters from the same request" in {
    val req = Request(
      Method.GET,
      "/users/42/posts/99",
      Map.empty,
      "",
      Map("userId" -> "42", "postId" -> "99")
    )

    val result = Raise.either {
      val userId = req.pathParam[Int]("userId")
      val postId = req.pathParam[Int]("postId")
      (userId, postId)
    }

    result shouldBe Right((42, 99))
  }

  it should "handle negative integers" in {
    val req = Request(Method.GET, "/values/-123", Map.empty, "", Map("value" -> "-123"))

    val result = Raise.either {
      req.pathParam[Int]("value")
    }

    result shouldBe Right(-123)
  }

  it should "handle zero" in {
    val req = Request(Method.GET, "/values/0", Map.empty, "", Map("value" -> "0"))

    val result = Raise.either {
      req.pathParam[Int]("value")
    }

    result shouldBe Right(0)
  }
}
