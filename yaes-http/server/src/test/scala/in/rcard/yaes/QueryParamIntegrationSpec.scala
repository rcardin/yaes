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

  // ========================================
  // TDD: Query Parameter Context Access Tests
  // ========================================

  "Query parameter context" should "provide query params to handler via context function" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[String]("q")) { query ?=> req =>
        val searchTerm = query.get("q")
        Response.ok(s"Searching for: $searchTerm")
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
    response.body shouldBe "Searching for: scala"
  }

  it should "provide multiple query params via context" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[String]("q") & queryParam[Int]("limit")) { query ?=> req =>
        val searchTerm = query.get("q")
        val limit = query.get("limit")
        Response.ok(s"Searching for '$searchTerm' with limit $limit")
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
    response.body shouldBe "Searching for 'scala' with limit 10"
  }

  it should "work with optional query parameters" in {
    val routes = Routes(
      GET(p"/search" ? queryParam[Option[Int]]("page")) { query ?=> req =>
        val page = query.get("page")
        page match {
          case Some(p) => Response.ok(s"Page $p")
          case None    => Response.ok("First page")
        }
      }
    )

    val requestWithPage = Request(
      method = Method.GET,
      path = "/search",
      headers = Map.empty,
      body = "",
      queryString = Map("page" -> List("5"))
    )
    routes.handle(requestWithPage).body shouldBe "Page 5"

    val requestWithoutPage = Request(
      method = Method.GET,
      path = "/search",
      headers = Map.empty,
      body = "",
      queryString = Map.empty
    )
    routes.handle(requestWithoutPage).body shouldBe "First page"
  }

  it should "work with list-valued query parameters" in {
    val routes = Routes(
      GET(p"/filter" ? queryParam[List[String]]("tags")) { query ?=> req =>
        val tags = query.get("tags")
        Response.ok(s"Tags: ${tags.mkString(", ")}")
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
    response.body shouldBe "Tags: scala, functional, tutorial"
  }

  "Combined path and query parameters" should "provide both via separate mechanisms" in {
    val userId = param[Int]("userId")
    val routes = Routes(
      GET((p"/users" / userId) ? queryParam[Boolean]("expand")) { query ?=> (req, id: Int) =>
        val expand = query.get("expand")
        if (expand) Response.ok(s"User $id (expanded)")
        else Response.ok(s"User $id")
      }
    )

    val expandedRequest = Request(
      method = Method.GET,
      path = "/users/123",
      headers = Map.empty,
      body = "",
      queryString = Map("expand" -> List("true"))
    )
    routes.handle(expandedRequest).body shouldBe "User 123 (expanded)"

    val normalRequest = Request(
      method = Method.GET,
      path = "/users/123",
      headers = Map.empty,
      body = "",
      queryString = Map("expand" -> List("false"))
    )
    routes.handle(normalRequest).body shouldBe "User 123"
  }

  it should "work with two path params and two query params" in {
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")
    val routes = Routes(
      GET(
        (p"/users" / userId / "posts" / postId) ? queryParam[String]("format") & queryParam[Boolean](
          "comments"
        )
      ) { query ?=> (req, uid: Int, pid: Long) =>
        val format = query.get("format")
        val comments = query.get("comments")
        Response.ok(s"User $uid, Post $pid, format=$format, comments=$comments")
      }
    )

    val request = Request(
      method = Method.GET,
      path = "/users/42/posts/999",
      headers = Map.empty,
      body = "",
      queryString = Map("format" -> List("json"), "comments" -> List("true"))
    )

    val response = routes.handle(request)
    response.body shouldBe "User 42, Post 999, format=json, comments=true"
  }

  "Query context with all HTTP methods" should "work with POST" in {
    val routes = Routes(
      POST(p"/search" ? queryParam[Boolean]("async")) { query ?=> req =>
        val async = query.get("async")
        Response.created(s"Created (async=$async)")
      }
    )

    val request = Request(
      method = Method.POST,
      path = "/search",
      headers = Map.empty,
      body = "{}",
      queryString = Map("async" -> List("true"))
    )

    routes.handle(request).body shouldBe "Created (async=true)"
  }

  it should "work with PUT" in {
    val id = param[Int]("id")
    val routes = Routes(
      PUT((p"/items" / id) ? queryParam[Boolean]("merge")) { query ?=> (req, itemId: Int) =>
        val merge = query.get("merge")
        Response.ok(s"PUT $itemId, merge=$merge")
      }
    )

    val request = Request(
      method = Method.PUT,
      path = "/items/1",
      headers = Map.empty,
      body = "{}",
      queryString = Map("merge" -> List("true"))
    )

    routes.handle(request).body shouldBe "PUT 1, merge=true"
  }

  it should "work with DELETE" in {
    val id = param[Int]("id")
    val routes = Routes(
      DELETE((p"/items" / id) ? queryParam[Boolean]("soft")) { query ?=> (req, itemId: Int) =>
        val soft = query.get("soft")
        if (soft) Response.ok(s"Soft delete $itemId")
        else Response.noContent()
      }
    )

    val request = Request(
      method = Method.DELETE,
      path = "/items/1",
      headers = Map.empty,
      body = "",
      queryString = Map("soft" -> List("true"))
    )

    routes.handle(request).body shouldBe "Soft delete 1"
  }

  it should "work with PATCH" in {
    val id = param[Int]("id")
    val routes = Routes(
      PATCH((p"/items" / id) ? queryParam[String]("field")) { query ?=> (req, itemId: Int) =>
        val field = query.get("field")
        Response.ok(s"PATCH $itemId, field=$field")
      }
    )

    val request = Request(
      method = Method.PATCH,
      path = "/items/2",
      headers = Map.empty,
      body = "{}",
      queryString = Map("field" -> List("name"))
    )

    routes.handle(request).body shouldBe "PATCH 2, field=name"
  }
}
