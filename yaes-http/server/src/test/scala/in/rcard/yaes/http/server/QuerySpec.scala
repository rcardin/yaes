package in.rcard.yaes.http.server


import in.rcard.yaes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class QuerySpec extends AnyFlatSpec with Matchers {

  "queryParam" should "retrieve a String query parameter value by name" in {
    type TestParams = QueryParam["name", String, NoQueryParams]
    given query: Query[TestParams] = Query[TestParams](Map("name" -> "John"))

    val result = Query.queryParam("name")

    result shouldBe "John"
  }

  it should "retrieve an Int query parameter value by name" in {
    type TestParams = QueryParam["age", Int, NoQueryParams]
    given query: Query[TestParams] = Query[TestParams](Map("age" -> 42))

    val result = Query.queryParam("age")

    result shouldBe 42
  }

  it should "retrieve a Boolean query parameter value by name" in {
    type TestParams = QueryParam["active", Boolean, NoQueryParams]
    given query: Query[TestParams] = Query[TestParams](Map("active" -> true))

    val result = Query.queryParam("active")

    result shouldBe true
  }

  it should "retrieve query parameters from a multi-parameter query" in {
    type TestParams = QueryParam["name", String, QueryParam["age", Int, NoQueryParams]]
    given query: Query[TestParams] = Query[TestParams](Map("name" -> "Jane", "age" -> 30))

    val name = Query.queryParam("name")
    val age = Query.queryParam("age")

    name shouldBe "Jane"
    age shouldBe 30
  }
}
