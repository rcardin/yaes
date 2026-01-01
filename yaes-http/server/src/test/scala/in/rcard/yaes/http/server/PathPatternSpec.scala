package in.rcard.yaes.http.server


import in.rcard.yaes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PathPatternSpec extends AnyFlatSpec with Matchers {

  private def req(path: String) = Request(Method.GET, path, Map.empty, "", Map.empty)

  "PathPattern (literal only)" should "match exact paths" in {
    val pattern = (p"/users" / "admin").build

    val result = Raise.either {
      pattern.extract(req("/users/admin"))
    }

    result.map(_.map(_._1)) shouldBe Right(Some(NoParamValues))
  }

  it should "not match different paths" in {
    val pattern = (p"/users" / "admin").build

    val result = Raise.either {
      pattern.extract(req("/users/other"))
    }

    result shouldBe Right(None)
  }

  it should "not match paths with different segment count" in {
    val pattern = (p"/users" / "admin").build

    val result1 = Raise.either {
      pattern.extract(req("/users/admin/extra"))
    }
    val result2 = Raise.either {
      pattern.extract(req("/users"))
    }

    result1 shouldBe Right(None)
    result2 shouldBe Right(None)
  }

  it should "handle root path" in {
    val pattern = p"/".build

    val result = Raise.either {
      pattern.extract(req("/"))
    }

    result.map(_.map(_._1)) shouldBe Right(Some(NoParamValues))
  }

  "PathPattern (single parameter)" should "extract Int parameter" in {
    val userId = param[Int]("userId")
    val pattern = (p"/users" / userId).build

    val result = Raise.either {
      pattern.extract(req("/users/123"))
    }

    result match {
      case Right(Some((ParamValueCons(id: Int, NoParamValues), _))) =>
        id shouldBe 123
      case other =>
        fail(s"Expected Right(Some(ParamValueCons(123, NoParamValues))), got $other")
    }
  }

  it should "extract Long parameter" in {
    val itemId = param[Long]("itemId")
    val pattern = (p"/items" / itemId).build

    val result = Raise.either {
      pattern.extract(req("/items/987654321"))
    }

    result match {
      case Right(Some((ParamValueCons(id: Long, NoParamValues), _))) =>
        id shouldBe 987654321L
      case other =>
        fail(s"Expected Long parameter, got $other")
    }
  }

  it should "extract String parameter" in {
    val username = param[String]("username")
    val pattern = (p"/users" / username).build

    val result = Raise.either {
      pattern.extract(req("/users/alice"))
    }

    result match {
      case Right(Some((ParamValueCons(name: String, NoParamValues), _))) =>
        name shouldBe "alice"
      case other =>
        fail(s"Expected String parameter, got $other")
    }
  }

  it should "raise PathParamError for invalid Int" in {
    val userId = param[Int]("userId")
    val pattern = (p"/users" / userId).build

    val result = Raise.either {
      pattern.extract(req("/users/abc"))
    }

    result match {
      case Left(PathParamError.InvalidType("userId", "abc", "Int")) => succeed
      case other => fail(s"Expected InvalidType error, got $other")
    }
  }

  it should "raise PathParamError for invalid Long" in {
    val itemId = param[Long]("itemId")
    val pattern = (p"/items" / itemId).build

    val result = Raise.either {
      pattern.extract(req("/items/not-a-number"))
    }

    result match {
      case Left(PathParamError.InvalidType("itemId", "not-a-number", "Long")) => succeed
      case other => fail(s"Expected InvalidType error, got $other")
    }
  }

  it should "return None for mismatched literal segments" in {
    val userId = param[Int]("userId")
    val pattern = (p"/users" / userId).build

    val result = Raise.either {
      pattern.extract(req("/posts/123"))
    }

    result shouldBe Right(None)
  }

  "PathPattern (multiple parameters)" should "extract two Int parameters" in {
    val userId = param[Int]("userId")
    val postId = param[Int]("postId")
    val pattern = (p"/users" / userId / "posts" / postId).build

    val result = Raise.either {
      pattern.extract(req("/users/42/posts/99"))
    }

    result match {
      case Right(Some((ParamValueCons(uid: Int, ParamValueCons(pid: Int, NoParamValues)), _))) =>
        uid shouldBe 42
        pid shouldBe 99
      case other =>
        fail(s"Expected two Int parameters, got $other")
    }
  }

  it should "extract mixed type parameters (Int and Long)" in {
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")
    val pattern = (p"/users" / userId / "posts" / postId).build

    val result = Raise.either {
      pattern.extract(req("/users/42/posts/999999999"))
    }

    result match {
      case Right(Some((ParamValueCons(uid: Int, ParamValueCons(pid: Long, NoParamValues)), _))) =>
        uid shouldBe 42
        pid shouldBe 999999999L
      case other =>
        fail(s"Expected Int and Long parameters, got $other")
    }
  }

  it should "extract three parameters" in {
    val orgId = param[Int]("orgId")
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")
    val pattern = (p"/orgs" / orgId / "users" / userId / "posts" / postId).build

    val result = Raise.either {
      pattern.extract(req("/orgs/1/users/42/posts/123"))
    }

    result match {
      case Right(Some((ParamValueCons(oid: Int, ParamValueCons(uid: Int, ParamValueCons(pid: Long, NoParamValues))), _))) =>
        oid shouldBe 1
        uid shouldBe 42
        pid shouldBe 123L
      case other =>
        fail(s"Expected three parameters, got $other")
    }
  }

  it should "raise error if first parameter is invalid" in {
    val userId = param[Int]("userId")
    val postId = param[Int]("postId")
    val pattern = (p"/users" / userId / "posts" / postId).build

    val result = Raise.either {
      pattern.extract(req("/users/abc/posts/99"))
    }

    result match {
      case Left(PathParamError.InvalidType("userId", "abc", "Int")) => succeed
      case other => fail(s"Expected InvalidType error for userId, got $other")
    }
  }

  it should "raise error if second parameter is invalid" in {
    val userId = param[Int]("userId")
    val postId = param[Int]("postId")
    val pattern = (p"/users" / userId / "posts" / postId).build

    val result = Raise.either {
      pattern.extract(req("/users/42/posts/xyz"))
    }

    result match {
      case Left(PathParamError.InvalidType("postId", "xyz", "Int")) => succeed
      case other => fail(s"Expected InvalidType error for postId, got $other")
    }
  }

  "PathPattern.toPattern" should "generate correct pattern string for literals" in {
    val pattern = (p"/users" / "admin").build
    pattern.toPattern shouldBe "/users/admin"
  }

  it should "generate correct pattern string with single parameter" in {
    val userId = param[Int]("userId")
    val pattern = (p"/users" / userId).build
    pattern.toPattern shouldBe "/users/:userId"
  }

  it should "generate correct pattern string with multiple parameters" in {
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")
    val pattern = (p"/users" / userId / "posts" / postId).build
    pattern.toPattern shouldBe "/users/:userId/posts/:postId"
  }

  "PathBuilder" should "build patterns with only literals using / operator" in {
    val pattern = (p"/api" / "v1" / "users").build

    val result = Raise.either {
      pattern.extract(req("/api/v1/users"))
    }

    result.map(_.map(_._1)) shouldBe Right(Some(NoParamValues))
  }

  it should "build mixed literal and parameter patterns" in {
    val id = param[Int]("id")
    val pattern = (p"/api" / "v1" / "users" / id).build

    val result = Raise.either {
      pattern.extract(req("/api/v1/users/123"))
    }

    result match {
      case Right(Some((ParamValueCons(userId: Int, NoParamValues), _))) =>
        userId shouldBe 123
      case other =>
        fail(s"Expected Int parameter, got $other")
    }
  }

  "PathPattern edge cases" should "not match paths with extra trailing segments" in {
    val userId = param[Int]("userId")
    val pattern = (p"/users" / userId).build

    val result = Raise.either {
      pattern.extract(req("/users/123/extra"))
    }

    result shouldBe Right(None)
  }

  it should "not match paths missing required segments" in {
    val userId = param[Int]("userId")
    val postId = param[Int]("postId")
    val pattern = (p"/users" / userId / "posts" / postId).build

    val result = Raise.either {
      pattern.extract(req("/users/123/posts"))
    }

    result shouldBe Right(None)
  }
}
