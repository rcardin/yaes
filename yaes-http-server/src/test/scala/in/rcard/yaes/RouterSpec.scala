package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RouterSpec extends AnyFlatSpec with Matchers {

  "Router" should "route GET requests to the correct handler" in {
    val router = Router(
      (Method.GET, "/hello", (req: Request) => Response.ok("Hello!")),
      (Method.GET, "/goodbye", (req: Request) => Response.ok("Goodbye!"))
    )

    val request = Request(Method.GET, "/hello", Map.empty, "")
    val response = router.handle(request)

    response.status shouldBe 200
    response.body shouldBe "Hello!"
  }

  it should "route POST requests to the correct handler" in {
    val router = Router(
      (Method.POST, "/echo", (req: Request) => Response.ok(req.body))
    )

    val request = Request(Method.POST, "/echo", Map.empty, "test body")
    val response = router.handle(request)

    response.status shouldBe 200
    response.body shouldBe "test body"
  }

  it should "distinguish between different HTTP methods on the same path" in {
    val router = Router(
      (Method.GET, "/resource", (req: Request) => Response.ok("GET response")),
      (Method.POST, "/resource", (req: Request) => Response.created("POST response"))
    )

    val getRequest = Request(Method.GET, "/resource", Map.empty, "")
    val getResponse = router.handle(getRequest)

    getResponse.status shouldBe 200
    getResponse.body shouldBe "GET response"

    val postRequest = Request(Method.POST, "/resource", Map.empty, "")
    val postResponse = router.handle(postRequest)

    postResponse.status shouldBe 201
    postResponse.body shouldBe "POST response"
  }

  it should "return 404 for unmatched routes" in {
    val router = Router(
      (Method.GET, "/hello", (req: Request) => Response.ok("Hello!"))
    )

    val request = Request(Method.GET, "/unknown", Map.empty, "")
    val response = router.handle(request)

    response.status shouldBe 404
    response.body shouldBe "Not Found"
  }

  it should "return 404 when method doesn't match but path does" in {
    val router = Router(
      (Method.GET, "/resource", (req: Request) => Response.ok("GET only"))
    )

    val request = Request(Method.POST, "/resource", Map.empty, "")
    val response = router.handle(request)

    response.status shouldBe 404
  }

  it should "handle multiple routes correctly" in {
    val router = Router(
      (Method.GET, "/users", (req: Request) => Response.ok("List users")),
      (Method.POST, "/users", (req: Request) => Response.created("User created")),
      (Method.GET, "/posts", (req: Request) => Response.ok("List posts")),
      (Method.DELETE, "/posts", (req: Request) => Response.noContent())
    )

    router.handle(Request(Method.GET, "/users", Map.empty, "")).status shouldBe 200
    router.handle(Request(Method.POST, "/users", Map.empty, "")).status shouldBe 201
    router.handle(Request(Method.GET, "/posts", Map.empty, "")).status shouldBe 200
    router.handle(Request(Method.DELETE, "/posts", Map.empty, "")).status shouldBe 204
  }

  it should "pass request headers to handlers" in {
    val router = Router(
      (
        Method.GET,
        "/headers",
        (req: Request) => {
          val authHeader = req.headers.getOrElse("Authorization", "none")
          Response.ok(s"Auth: $authHeader")
        }
      )
    )

    val request = Request(Method.GET, "/headers", Map("Authorization" -> "Bearer token123"), "")
    val response = router.handle(request)

    response.body shouldBe "Auth: Bearer token123"
  }
}
