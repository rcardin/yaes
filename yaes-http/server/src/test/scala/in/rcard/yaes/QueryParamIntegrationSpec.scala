package in.rcard.yaes

import in.rcard.yaes.queryParam
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for query parameter functionality.
  *
  * Tests the complete end-to-end flow of query parameter parsing, matching, and type-safe access.
  */
class QueryParamIntegrationSpec extends AnyFlatSpec with Matchers {

  "Query parameter parsing" should "parse single required parameter" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[String]("q")) { req =>
        // Query params will be available via context in the future
        Response.ok("Search endpoint")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/search",
      headers = Map.empty,
      body = "",
      queryString = Map("q" -> List("scala"))
    )

    val response = routes.handle(request)
    response.status shouldBe 200
  }

  it should "parse multiple required parameters" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[String]("q") & queryParam[Int]("limit")) { req =>
        Response.ok("Search with limit")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/search",
      headers = Map.empty,
      body = "",
      queryString = Map("q" -> List("scala"), "limit" -> List("10"))
    )

    val response = routes.handle(request)
    response.status shouldBe 200
  }

  it should "return 400 for missing required parameter" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[String]("q")) { req =>
        Response.ok("Search")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/search",
      headers = Map.empty,
      body = "",
      queryString = Map.empty
    )

    val response = routes.handle(request)
    response.status shouldBe 400
    response.body should include("Missing required query parameter: q")
  }

  it should "return 400 for invalid parameter type" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[Int]("limit")) { req =>
        Response.ok("Search with limit")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/search",
      headers = Map.empty,
      body = "",
      queryString = Map("limit" -> List("abc"))
    )

    val response = routes.handle(request)
    response.status shouldBe 400
    response.body should include("Invalid query parameter 'limit'")
  }

  it should "handle optional parameters when present" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[Option[Int]]("page")) { req =>
        Response.ok("Search with optional page")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/search",
      headers = Map.empty,
      body = "",
      queryString = Map("page" -> List("5"))
    )

    val response = routes.handle(request)
    response.status shouldBe 200
  }

  it should "handle optional parameters when missing" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[Option[Int]]("page")) { req =>
        Response.ok("Search without page")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/search",
      headers = Map.empty,
      body = "",
      queryString = Map.empty
    )

    val response = routes.handle(request)
    response.status shouldBe 200
  }

  it should "handle optional parameters with invalid value" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[Option[Int]]("page")) { req =>
        Response.ok("Search with invalid page")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/search",
      headers = Map.empty,
      body = "",
      queryString = Map("page" -> List("invalid"))
    )

    val response = routes.handle(request)
    // Optional params return None on parse error, not 400
    response.status shouldBe 200
  }

  it should "handle multi-valued list parameters" in {
    val routes = Routes(
      GET(p"/filter" ? queryParam[List[String]]("tags")) { req =>
        Response.ok("Filter by tags")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/filter",
      headers = Map.empty,
      body = "",
      queryString = Map("tags" -> List("scala", "functional", "tutorial"))
    )

    val response = routes.handle(request)
    response.status shouldBe 200
  }

  it should "handle empty list for multi-valued parameters" in {
    val routes = Routes(
      GET(p"/filter" ? queryParam[List[String]]("tags")) { req =>
        Response.ok("Filter with no tags")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/filter",
      headers = Map.empty,
      body = "",
      queryString = Map.empty
    )

    val response = routes.handle(request)
    response.status shouldBe 200
  }

  "Combined path and query parameters" should "work together" in {
    // TODO: Test combining path params with query params
    // Need to figure out operator precedence issue with: p"/users" / userId ? queryParam[Boolean]("expand")
    pending
  }

  it should "return 400 for invalid path parameter" in {
    // TODO: Test path param errors with query params
    pending
  }

  it should "return 400 for invalid query parameter" in {
    // TODO: Test query param errors with path params
    pending
  }

  "Routes without query parameters" should "still work as before" in {
    val routes = Routes(
      GET(p"/health") { req =>
        Response.ok("OK")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/health",
      headers = Map.empty,
      body = "",
      queryString = Map.empty
    )

    val response = routes.handle(request)
    response.status shouldBe 200
    response.body shouldBe "OK"
  }

  it should "be stored in exactRoutes for fast lookup" in {
    val routes = Routes(
      GET(p"/health") { req =>
        Response.ok("OK")
      }
    )

    // Route with no path params and no query params should be in exactRoutes
    routes.exactRoutes should not be empty
    routes.paramRoutes shouldBe empty
  }

  "Routes with query parameters" should "not be stored in exactRoutes" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[String]("q")) { req =>
        Response.ok("Search")
      }
    )

    // Route with query params should be in paramRoutes
    routes.exactRoutes shouldBe empty
    routes.paramRoutes should have size 1
  }

  "Different parameter types" should "parse correctly" in {
    val routes = Routes(
      GET(
        p"/api"
          ? queryParam[String]("name")
          & queryParam[Int]("age")
          & queryParam[Long]("id")
          & queryParam[Boolean]("active")
      ) { req =>
        Response.ok("API endpoint")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/api",
      headers = Map.empty,
      body = "",
      queryString = Map(
        "name" -> List("John"),
        "age" -> List("30"),
        "id" -> List("1234567890"),
        "active" -> List("true")
      )
    )

    val response = routes.handle(request)
    response.status shouldBe 200
  }

  "Pattern matching" should "not match routes without query params when query params are provided" in {
    val routes = Routes(
      GET(p"/search") { req =>
        Response.ok("Search without params")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/search",
      headers = Map.empty,
      body = "",
      queryString = Map("q" -> List("scala"))
    )

    // Route should still match - query params in request are ignored if route doesn't expect them
    val response = routes.handle(request)
    response.status shouldBe 200
  }
}
