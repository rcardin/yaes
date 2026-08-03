package io.yaes.http.server.params.query

import io.yaes.*
import io.yaes.http.core.Method
import io.yaes.http.server.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Unit tests for query parameter extraction into named tuples.
  *
  * Query parameters are extracted as a named tuple and accessed by name (`query.name`), replacing
  * the former `Query.get` accessor and `Contains` evidence.
  */
class QuerySpec extends AnyFlatSpec with Matchers {

  private def req(query: Map[String, List[String]]) =
    Request(Method.GET, "/test", Map.empty, "", query)

  "query extraction" should "expose a String query parameter by name" in {
    val pattern = (p"/test" ? queryParam[String]("name")).build

    val result = Raise.either {
      pattern.extract(req(Map("name" -> List("John"))))
    }

    result match {
      case Right(Some((_, query))) => query.name shouldBe "John"
      case other                   => fail(s"Expected String query parameter, got $other")
    }
  }

  it should "expose an Int query parameter by name" in {
    val pattern = (p"/test" ? queryParam[Int]("age")).build

    val result = Raise.either {
      pattern.extract(req(Map("age" -> List("42"))))
    }

    result match {
      case Right(Some((_, query))) => query.age shouldBe 42
      case other                   => fail(s"Expected Int query parameter, got $other")
    }
  }

  it should "expose a Boolean query parameter by name" in {
    val pattern = (p"/test" ? queryParam[Boolean]("active")).build

    val result = Raise.either {
      pattern.extract(req(Map("active" -> List("true"))))
    }

    result match {
      case Right(Some((_, query))) => query.active shouldBe true
      case other                   => fail(s"Expected Boolean query parameter, got $other")
    }
  }

  it should "expose multiple query parameters by name in declaration order" in {
    val pattern = (p"/test" ? queryParam[String]("name") & queryParam[Int]("age")).build

    val result = Raise.either {
      pattern.extract(req(Map("name" -> List("Jane"), "age" -> List("30"))))
    }

    result match {
      case Right(Some((_, query))) =>
        query.name shouldBe "Jane"
        query.age shouldBe 30
      case other => fail(s"Expected two query parameters, got $other")
    }
  }
}
