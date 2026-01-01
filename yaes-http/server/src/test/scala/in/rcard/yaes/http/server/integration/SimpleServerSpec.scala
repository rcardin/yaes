package in.rcard.yaes.http.server.integration


import in.rcard.yaes.*
import in.rcard.yaes.http.server.*
import in.rcard.yaes.http.server.PathBuilder.given
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for basic server functionality.
  *
  * These tests verify that ServerDef correctly wraps Routes and that the routes can handle requests.
  * Full HTTP integration tests (actual server startup/networking) should be run manually or in a
  * dedicated end-to-end test suite.
  */
class SimpleServerSpec extends AnyFlatSpec with Matchers {

  private def req(method: Method, path: String, headers: Map[String, String] = Map.empty, body: String = "") =
    Request(method, path, headers, body, Map.empty)

  "ServerDef" should "be created from routes" in {
    val server = YaesServer.route(
      GET(p"/hello") { req =>
        Response.ok("Hello!")
      },
      POST(p"/echo") { req =>
        Response.ok(req.body)
      }
    )

    server.routes shouldBe a[Routes]
  }

  "Server routes (exact paths)" should "handle GET requests" in {
    val server = YaesServer.route(
      GET(p"/test") { req =>
        Response.ok("Test response")
      }
    )

    val request  = req(Method.GET, "/test")
    val response = server.routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "Test response"
  }

  it should "return 404 for unmatched routes" in {
    val server = YaesServer.route(
      GET(p"/exists") { req =>
        Response.ok("Found")
      }
    )

    val request  = req(Method.GET, "/notfound")
    val response = server.routes.handle(request)

    response.status shouldBe 404
    response.body should include("No route found")
  }

  it should "pass request body to handlers" in {
    val server = YaesServer.route(
      POST(p"/echo") { req =>
        Response.ok(s"Received: ${req.body}")
      }
    )

    val request  = req(Method.POST, "/echo", body = "test data")
    val response = server.routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "Received: test data"
  }

  it should "pass request headers to handlers" in {
    val server = YaesServer.route(
      GET(p"/headers") { req =>
        val auth = req.headers.getOrElse("Authorization", "none")
        Response.ok(s"Auth: $auth")
      }
    )

    val request  = req(Method.GET, "/headers", headers = Map("Authorization" -> "Bearer token"))
    val response = server.routes.handle(request)

    response.body shouldBe "Auth: Bearer token"
  }

  it should "support multiple HTTP methods on same path" in {
    val server = YaesServer.route(
      GET(p"/users") { req =>
        Response.ok("List users")
      },
      POST(p"/users") { req =>
        Response.created("User created")
      },
      DELETE(p"/users") { req =>
        Response.noContent()
      }
    )

    server.routes.handle(req(Method.GET, "/users")).status shouldBe 200
    server.routes.handle(req(Method.POST, "/users")).status shouldBe 201
    server.routes.handle(req(Method.DELETE, "/users")).status shouldBe 204
  }

  "Server routes (parameterized paths)" should "handle single parameter routes" in {
    val userId = param[Int]("userId")
    val server = YaesServer.route(
      GET(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"User $id")
      }
    )

    val request  = req(Method.GET, "/users/123")
    val response = server.routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "User 123"
  }

  it should "handle multiple parameter routes" in {
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")
    val server = YaesServer.route(
      GET(p"/users" / userId / "posts" / postId) { (req, uid: Int, pid: Long) =>
        Response.ok(s"User $uid, Post $pid")
      }
    )

    val request  = req(Method.GET, "/users/42/posts/99")
    val response = server.routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "User 42, Post 99"
  }

  it should "return 400 for invalid parameter types" in {
    val userId = param[Int]("userId")
    val server = YaesServer.route(
      GET(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"User $id")
      }
    )

    val request  = req(Method.GET, "/users/not-a-number")
    val response = server.routes.handle(request)

    response.status shouldBe 400
    response.body should include("Invalid path parameter")
  }

  "Server routes (mixed exact and parameterized)" should "handle complex routing scenarios" in {
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")

    val server = YaesServer.route(
      GET(p"/health") { req =>
        Response.ok("OK")
      },
      GET(p"/users") { req =>
        Response.ok("All users")
      },
      GET(p"/users/admin") { req =>
        Response.ok("Admin user")
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
        Response.created(s"Created: ${req.body}")
      }
    )

    // Exact routes
    server.routes.handle(req(Method.GET, "/health")).body shouldBe "OK"
    server.routes.handle(req(Method.GET, "/users")).body shouldBe "All users"
    server.routes.handle(req(Method.GET, "/users/admin")).body shouldBe "Admin user"

    // Parameterized routes
    server.routes.handle(req(Method.GET, "/users/42")).body shouldBe "User 42"
    server.routes.handle(req(Method.GET, "/users/42/posts")).body shouldBe "Posts for user 42"
    server.routes.handle(req(Method.GET, "/users/42/posts/99")).body shouldBe "User 42, Post 99"

    // POST with body
    val postReq = req(Method.POST, "/users", body = "Alice")
    server.routes.handle(postReq).body shouldBe "Created: Alice"
  }

  "Server routes (request access in parameterized handlers)" should "allow access to request properties" in {
    val userId = param[Int]("userId")
    val server = YaesServer.route(
      GET(p"/users" / userId) { (req, id: Int) =>
        val contentType = req.headers.getOrElse("Content-Type", "none")
        Response.ok(s"User $id with content-type: $contentType")
      },
      POST(p"/users" / userId) { (req, id: Int) =>
        Response.ok(s"User $id received: ${req.body}")
      }
    )

    // Test header access
    val getReq = req(Method.GET, "/users/42", headers = Map("Content-Type" -> "application/json"))
    server.routes.handle(getReq).body shouldBe "User 42 with content-type: application/json"

    // Test body access
    val postReq = req(Method.POST, "/users/42", body = "update data")
    server.routes.handle(postReq).body shouldBe "User 42 received: update data"
  }

  "Server routes (all HTTP methods)" should "support GET, POST, PUT, DELETE, PATCH with parameters" in {
    val userId = param[Int]("userId")
    val server = YaesServer.route(
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

    server.routes.handle(req(Method.GET, "/users/1")).body shouldBe "GET User 1"
    server.routes.handle(req(Method.POST, "/users/1")).body shouldBe "POST User 1"
    server.routes.handle(req(Method.PUT, "/users/1")).body shouldBe "PUT User 1"
    server.routes.handle(req(Method.DELETE, "/users/1")).status shouldBe 204
    server.routes.handle(req(Method.PATCH, "/users/1")).body shouldBe "PATCH User 1"
  }

  "Server routes (edge cases)" should "handle root path" in {
    val server = YaesServer.route(
      GET(p"/") { req =>
        Response.ok("Root")
      }
    )

    val response = server.routes.handle(req(Method.GET, "/"))
    response.status shouldBe 200
    response.body shouldBe "Root"
  }

  it should "handle three parameters" in {
    val orgId  = param[String]("orgId")
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")

    val server = YaesServer.route(
      GET(p"/orgs" / orgId / "users" / userId / "posts" / postId) { (req, oid: String, uid: Int, pid: Long) =>
        Response.ok(s"Org $oid, User $uid, Post $pid")
      }
    )

    val request  = req(Method.GET, "/orgs/acme/users/42/posts/123")
    val response = server.routes.handle(request)

    response.status shouldBe 200
    response.body shouldBe "Org acme, User 42, Post 123"
  }
}
