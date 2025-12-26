package in.rcard.yaes.integration

import in.rcard.yaes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Simplified integration tests for basic server functionality.
  *
  * These tests verify the core server components work together without
  * requiring actual HTTP requests (which is complex in test environments).
  * Full HTTP integration tests should be run manually or in a dedicated
  * integration test suite.
  */
class SimpleServerSpec extends AnyFlatSpec with Matchers {

  "ServerDef" should "be created from routes" in {
    val server = YaesServer.route(
      (Method.GET, "/hello", (req: Request) => Response.ok("Hello!")),
      (Method.POST, "/echo", (req: Request) => Response.ok(req.body))
    )

    server.router shouldBe a[Router]
  }

  "Server routes" should "handle requests through the router" in {
    val server = YaesServer.route(
      (Method.GET, "/test", (req: Request) => Response.ok("Test response"))
    )

    val request  = Request(Method.GET, "/test", Map.empty, "")
    val response = server.router.handle(request)

    response.status shouldBe 200
    response.body shouldBe "Test response"
  }

  it should "return 404 for unmatched routes" in {
    val server = YaesServer.route(
      (Method.GET, "/exists", (req: Request) => Response.ok("Found"))
    )

    val request  = Request(Method.GET, "/notfound", Map.empty, "")
    val response = server.router.handle(request)

    response.status shouldBe 404
  }

  it should "pass request body to handlers" in {
    val server = YaesServer.route(
      (Method.POST, "/echo", (req: Request) => Response.ok(s"Received: ${req.body}"))
    )

    val request  = Request(Method.POST, "/echo", Map.empty, "test data")
    val response = server.router.handle(request)

    response.status shouldBe 200
    response.body shouldBe "Received: test data"
  }

  it should "pass request headers to handlers" in {
    val server = YaesServer.route(
      (
        Method.GET,
        "/headers",
        (req: Request) => {
          val auth = req.headers.getOrElse("Authorization", "none")
          Response.ok(s"Auth: $auth")
        }
      )
    )

    val request = Request(Method.GET, "/headers", Map("Authorization" -> "Bearer token"), "")
    val response = server.router.handle(request)

    response.body shouldBe "Auth: Bearer token"
  }

  it should "support multiple HTTP methods on different paths" in {
    val server = YaesServer.route(
      (Method.GET, "/users", (req: Request) => Response.ok("List users")),
      (Method.POST, "/users", (req: Request) => Response.created("User created")),
      (Method.DELETE, "/users", (req: Request) => Response.noContent())
    )

    server.router.handle(Request(Method.GET, "/users", Map.empty, "")).status shouldBe 200
    server.router.handle(Request(Method.POST, "/users", Map.empty, "")).status shouldBe 201
    server.router.handle(Request(Method.DELETE, "/users", Map.empty, "")).status shouldBe 204
  }
}
