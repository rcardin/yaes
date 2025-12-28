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

  // Path parameter tests

  it should "route parameterized paths with single parameter" in {
    val router = Router(
      (
        Method.GET,
        "/users/:id",
        (req: Request) => {
          val id = req.pathParams("id")
          Response.ok(s"User $id")
        }
      )
    )

    val request = Request(Method.GET, "/users/123", Map.empty, "")
    val response = router.handle(request)

    response.status shouldBe 200
    response.body shouldBe "User 123"
  }

  it should "route parameterized paths with multiple parameters" in {
    val router = Router(
      (
        Method.GET,
        "/users/:userId/posts/:postId",
        (req: Request) => {
          val userId = req.pathParams("userId")
          val postId = req.pathParams("postId")
          Response.ok(s"User $userId, Post $postId")
        }
      )
    )

    val request = Request(Method.GET, "/users/42/posts/99", Map.empty, "")
    val response = router.handle(request)

    response.status shouldBe 200
    response.body shouldBe "User 42, Post 99"
  }

  it should "prioritize exact matches over parameterized routes" in {
    val router = Router(
      (Method.GET, "/users/:id", (req: Request) => Response.ok("Parameterized")),
      (Method.GET, "/users/admin", (req: Request) => Response.ok("Exact match"))
    )

    val exactRequest = Request(Method.GET, "/users/admin", Map.empty, "")
    val exactResponse = router.handle(exactRequest)
    exactResponse.body shouldBe "Exact match"

    val paramRequest = Request(Method.GET, "/users/123", Map.empty, "")
    val paramResponse = router.handle(paramRequest)
    paramResponse.body shouldBe "Parameterized"
  }

  it should "return 404 when parameterized path doesn't match pattern" in {
    val router = Router(
      (Method.GET, "/users/:id", (req: Request) => Response.ok("User"))
    )

    val request = Request(Method.GET, "/posts/123", Map.empty, "")
    val response = router.handle(request)

    response.status shouldBe 404
  }

  it should "distinguish methods on parameterized paths" in {
    val router = Router(
      (Method.GET, "/users/:id", (req: Request) => Response.ok("GET user")),
      (Method.DELETE, "/users/:id", (req: Request) => Response.noContent())
    )

    val getResponse = router.handle(Request(Method.GET, "/users/123", Map.empty, ""))
    getResponse.status shouldBe 200
    getResponse.body shouldBe "GET user"

    val deleteResponse = router.handle(Request(Method.DELETE, "/users/123", Map.empty, ""))
    deleteResponse.status shouldBe 204
  }

  it should "handle mix of exact and parameterized routes" in {
    val router = Router(
      (Method.GET, "/users", (req: Request) => Response.ok("All users")),
      (
        Method.GET,
        "/users/:id",
        (req: Request) => Response.ok(s"User ${req.pathParams("id")}")
      ),
      (Method.GET, "/users/:id/posts", (req: Request) => Response.ok("User posts"))
    )

    router.handle(Request(Method.GET, "/users", Map.empty, "")).body shouldBe "All users"
    router.handle(Request(Method.GET, "/users/123", Map.empty, "")).body shouldBe "User 123"
    router
      .handle(Request(Method.GET, "/users/123/posts", Map.empty, ""))
      .body shouldBe "User posts"
  }

  it should "extract typed path parameters" in {
    val router = Router(
      (
        Method.GET,
        "/users/:id",
        (req: Request) => {
          val result = Raise.either {
            val userId = req.pathParam[Int]("id")
            Response.ok(s"User ID: $userId")
          }
          result match {
            case Right(response) => response
            case Left(error)     => Response.badRequest(error.message)
          }
        }
      )
    )

    val validRequest = Request(Method.GET, "/users/123", Map.empty, "")
    val validResponse = router.handle(validRequest)
    validResponse.status shouldBe 200
    validResponse.body shouldBe "User ID: 123"

    val invalidRequest = Request(Method.GET, "/users/abc", Map.empty, "")
    val invalidResponse = router.handle(invalidRequest)
    invalidResponse.status shouldBe 400
  }
}
