package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import PathBuilder.given

class RoutesSpec extends AnyFlatSpec with Matchers {

  "Routes (exact routes)" should "match GET requests with exact path" in {
    val routes = Routes(
      GET(p"/health") { req =>
        Response.ok("OK")
      }
    )

    val request = Request(Method.GET, "/health", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "OK"
  }

  it should "match POST requests with exact path" in {
    val routes = Routes(
      POST(p"/users") { req =>
        Response.created("User created")
      }
    )

    val request = Request(Method.POST, "/users", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 201
    response.body shouldBe "User created"
  }

  it should "return 404 for non-existent exact routes" in {
    val routes = Routes(
      GET(p"/health") { req =>
        Response.ok("OK")
      }
    )

    val request = Request(Method.GET, "/notfound", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 404
    response.body should include("No route found")
  }

  it should "distinguish between different HTTP methods" in {
    val routes = Routes(
      GET(p"/users") { req =>
        Response.ok("GET users")
      },
      POST(p"/users") { req =>
        Response.created("POST users")
      }
    )

    val getRequest = Request(Method.GET, "/users", Map.empty, "")
    val postRequest = Request(Method.POST, "/users", Map.empty, "")

    routes.handle(getRequest).body shouldBe "GET users"
    routes.handle(postRequest).body shouldBe "POST users"
  }

  it should "handle multiple exact routes" in {
    val routes = Routes(
      GET(p"/health") { req => Response.ok("healthy") },
      GET(p"/status") { req => Response.ok("status") },
      GET(p"/version") { req => Response.ok("v1.0") }
    )

    routes.handle(Request(Method.GET, "/health", Map.empty, "")).body shouldBe "healthy"
    routes.handle(Request(Method.GET, "/status", Map.empty, "")).body shouldBe "status"
    routes.handle(Request(Method.GET, "/version", Map.empty, "")).body shouldBe "v1.0"
  }

  "Routes (parameterized routes)" should "extract and pass Int parameter" in {
    val userId = param[Int]("userId")
    val routes = Routes(
      GET(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"User $id")
      }
    )

    val request = Request(Method.GET, "/users/123", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "User 123"
  }

  it should "extract and pass Long parameter" in {
    val itemId = param[Long]("itemId")
    val routes = Routes(
      GET(p"/items" / itemId) { (req, id: Long) =>
        Response.ok(s"Item $id")
      }
    )

    val request = Request(Method.GET, "/items/987654321", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "Item 987654321"
  }

  it should "extract and pass String parameter" in {
    val username = param[String]("username")
    val routes = Routes(
      GET(p"/users" / username) { (req, name: String) =>
        Response.ok(s"Hello $name")
      }
    )

    val request = Request(Method.GET, "/users/alice", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "Hello alice"
  }

  it should "extract and pass two parameters" in {
    val userId = param[Int]("userId")
    val postId = param[Int]("postId")
    val routes = Routes(
      GET(p"/users" / userId / "posts" / postId) { (req, uid: Int, pid: Int) =>
        Response.ok(s"User $uid, Post $pid")
      }
    )

    val request = Request(Method.GET, "/users/42/posts/99", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "User 42, Post 99"
  }

  it should "extract and pass three parameters" in {
    val orgId = param[Int]("orgId")
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")
    val routes = Routes(
      GET(p"/orgs" / orgId / "users" / userId / "posts" / postId) { (req, oid: Int, uid: Int, pid: Long) =>
        Response.ok(s"Org $oid, User $uid, Post $pid")
      }
    )

    val request = Request(Method.GET, "/orgs/1/users/42/posts/123", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "Org 1, User 42, Post 123"
  }

  it should "return 400 for invalid Int parameter" in {
    val userId = param[Int]("userId")
    val routes = Routes(
      GET(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"User $id")
      }
    )

    val request = Request(Method.GET, "/users/abc", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 400
    response.body should include("Invalid path parameter")
    response.body should include("userId")
  }

  it should "return 400 for invalid Long parameter" in {
    val itemId = param[Long]("itemId")
    val routes = Routes(
      GET(p"/items" / itemId) { (req, id: Long) =>
        Response.ok(s"Item $id")
      }
    )

    val request = Request(Method.GET, "/items/not-a-number", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 400
    response.body should include("Invalid path parameter")
    response.body should include("itemId")
  }

  it should "return 404 for mismatched path structure" in {
    val userId = param[Int]("userId")
    val routes = Routes(
      GET(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"User $id")
      }
    )

    val request = Request(Method.GET, "/posts/123", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 404
    response.body should include("No route found")
  }

  "Routes (mixed exact and parameterized)" should "try exact routes first" in {
    val userId = param[Int]("userId")
    val routes = Routes(
      GET(p"/users/admin") { req =>
        Response.ok("Admin user")
      },
      GET(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"User $id")
      }
    )

    // Should match exact route
    routes.handle(Request(Method.GET, "/users/admin", Map.empty, "")).body shouldBe "Admin user"

    // Should match parameterized route
    routes.handle(Request(Method.GET, "/users/123", Map.empty, "")).body shouldBe "User 123"
  }

  it should "handle complex routing scenarios" in {
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")
    val routes = Routes(
      GET(p"/health") { req =>
        Response.ok("OK")
      },
      GET(p"/users") { req =>
        Response.ok("All users")
      },
      GET(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"User $id")
      },
      GET(p"/users" / userId / "posts") { (req, id: Int) =>
        Response.ok(s"Posts for user $id")
      },
      GET(p"/users" / userId / "posts" / postId) { (req, uid: Int, pid: Long) =>
        Response.ok(s"User $uid, Post $pid")
      },
      POST(p"/users") { req =>
        Response.created("User created")
      }
    )

    routes.handle(Request(Method.GET, "/health", Map.empty, "")).body shouldBe "OK"
    routes.handle(Request(Method.GET, "/users", Map.empty, "")).body shouldBe "All users"
    routes.handle(Request(Method.GET, "/users/42", Map.empty, "")).body shouldBe "User 42"
    routes.handle(Request(Method.GET, "/users/42/posts", Map.empty, "")).body shouldBe "Posts for user 42"
    routes.handle(Request(Method.GET, "/users/42/posts/99", Map.empty, "")).body shouldBe "User 42, Post 99"
    routes.handle(Request(Method.POST, "/users", Map.empty, "")).body shouldBe "User created"
  }

  "Routes (method-based routing)" should "support all HTTP methods" in {
    val userId = param[Int]("userId")
    val routes = Routes(
      GET(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"GET User $id")
      },
      POST(p"/users" / userId) { (req, id: Int) =>
        Response.created(s"POST User $id")
      },
      PUT(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"PUT User $id")
      },
      DELETE(p"/users" / userId) { (req, id: Int) =>
        Response.noContent()
      },
      PATCH(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"PATCH User $id")
      }
    )

    routes.handle(Request(Method.GET, "/users/1", Map.empty, "")).body shouldBe "GET User 1"
    routes.handle(Request(Method.POST, "/users/1", Map.empty, "")).body shouldBe "POST User 1"
    routes.handle(Request(Method.PUT, "/users/1", Map.empty, "")).body shouldBe "PUT User 1"
    routes.handle(Request(Method.DELETE, "/users/1", Map.empty, "")).status shouldBe 204
    routes.handle(Request(Method.PATCH, "/users/1", Map.empty, "")).body shouldBe "PATCH User 1"
  }

  "Routes (first match wins)" should "use first matching parameterized route" in {
    val userId = param[Int]("userId")
    val routes = Routes(
      GET(p"/users" / userId) { (req, id: Int) =>
        Response.ok("First route")
      },
      GET(p"/users" / userId) { (req, id: Int) =>
        Response.ok("Second route")
      }
    )

    val request = Request(Method.GET, "/users/123", Map.empty, "")
    val response = routes.handle(request)

    response.body shouldBe "First route"
  }

  "Routes (request access)" should "allow handlers to access request properties" in {
    val routes = Routes(
      GET(p"/echo") { req =>
        Response.ok(req.body)
      },
      GET(p"/headers") { req =>
        val value = req.headers.getOrElse("X-Custom", "not found")
        Response.ok(value)
      }
    )

    val bodyRequest = Request(Method.GET, "/echo", Map.empty, "test body")
    routes.handle(bodyRequest).body shouldBe "test body"

    val headerRequest = Request(Method.GET, "/headers", Map("X-Custom" -> "custom value"), "")
    routes.handle(headerRequest).body shouldBe "custom value"
  }

  it should "allow parameterized handlers to access request" in {
    val userId = param[Int]("userId")
    val routes = Routes(
      GET(p"/users" / userId) { (req, id: Int) =>
        val contentType = req.headers.getOrElse("Content-Type", "none")
        Response.ok(s"User $id with content-type: $contentType")
      }
    )

    val request = Request(Method.GET, "/users/42", Map("Content-Type" -> "application/json"), "")
    val response = routes.handle(request)

    response.body shouldBe "User 42 with content-type: application/json"
  }

  "Routes (edge cases)" should "handle root path" in {
    val routes = Routes(
      GET(p"/") { req =>
        Response.ok("Root")
      }
    )

    val request = Request(Method.GET, "/", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "Root"
  }

  it should "return 404 for empty routes" in {
    val routes = Routes()

    val request = Request(Method.GET, "/anything", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 404
  }

  it should "handle paths with special characters in parameters" in {
    val name = param[String]("name")
    val routes = Routes(
      GET(p"/users" / name) { (req, n: String) =>
        Response.ok(s"User $n")
      }
    )

    val request = Request(Method.GET, "/users/alice-bob", Map.empty, "")
    val response = routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "User alice-bob"
  }

  "Route.toPattern" should "generate correct pattern string" in {
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")

    val route1 = GET(p"/health") { req => Response.ok("") }
    route1.toPattern shouldBe "GET /health"

    val route2 = GET(p"/users" / userId) { (req, id: Int) => Response.ok("") }
    route2.toPattern shouldBe "GET /users/:userId"

    val route3 = GET(p"/users" / userId / "posts" / postId) { (req, uid: Int, pid: Long) =>
      Response.ok("")
    }
    route3.toPattern shouldBe "GET /users/:userId/posts/:postId"
  }
}
