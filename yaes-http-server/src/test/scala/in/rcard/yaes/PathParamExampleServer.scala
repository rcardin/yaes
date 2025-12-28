package in.rcard.yaes

import scala.concurrent.ExecutionContext.Implicits.global

/** Example server demonstrating path parameter usage.
  *
  * This example shows how to use parameterized routes with type-safe parameter extraction and
  * error handling using the `Raise[PathParamError]` effect.
  *
  * To run this server:
  * {{{
  * sbt "yaes-http-server/test:runMain in.rcard.yaes.PathParamExampleServer"
  * }}}
  *
  * Then test with:
  * {{{
  * curl http://localhost:8080/users/123
  * curl http://localhost:8080/users/abc  # Returns 400 error
  * curl http://localhost:8080/users/42/posts/99
  * }}}
  */
object PathParamExampleServer extends App {

  val server = YaesServer.route(
      // Simple parameterized route with error handling using Raise.fold
      (
        Method.GET,
        "/users/:id",
        (req: Request) => {
          Raise.fold {
            val userId = req.pathParam[Int]("id")
            Response.ok(s"User $userId")
          } {
            case PathParamError.InvalidType(name, value, targetType) =>
              Response.badRequest(s"Invalid $name: expected $targetType, got '$value'")
            case PathParamError.MissingParam(name) =>
              Response.internalServerError(s"Server error: missing param $name")
          } { response => response }
        }
      ),
      // Route with multiple parameters using Raise.either
      (
        Method.GET,
        "/users/:userId/posts/:postId",
        (req: Request) => {
          val result = Raise.either {
            val userId = req.pathParam[Int]("userId")
            val postId = req.pathParam[Int]("postId")
            Response.ok(s"Post $postId from User $userId")
          }
          result match {
            case Right(response) => response
            case Left(error)     => Response.badRequest(error.message)
          }
        }
      ),
      // Exact route (takes priority over parameterized /users/:id)
      (
        Method.GET,
        "/users/me",
        (req: Request) => Response.ok("Current user profile")
      ),
      // Mix of exact and parameterized paths
      (
        Method.GET,
        "/users",
        (req: Request) => Response.ok("List of all users")
      ),
      // Using Long parameters
      (
        Method.GET,
        "/items/:id",
        (req: Request) => {
          Raise.fold {
            val itemId = req.pathParam[Long]("id")
            Response.ok(s"Item $itemId")
          } {
            case error => Response.badRequest(error.message)
          } { response => response }
        }
      ),
      // DELETE with path parameter
      (
        Method.DELETE,
        "/users/:id",
        (req: Request) => {
          val result = Raise.either {
            val userId = req.pathParam[Int]("id")
            // In real app, would delete the user here
            userId
          }
          result match {
            case Right(_) => Response.noContent()
            case Left(error) => Response.badRequest(error.message)
          }
        }
      )
  )

  println("Starting Path Param Example Server on port 8080...")
  println("Available endpoints:")
  println("  GET    http://localhost:8080/users/:id")
  println("  GET    http://localhost:8080/users/:userId/posts/:postId")
  println("  GET    http://localhost:8080/users/me")
  println("  GET    http://localhost:8080/users")
  println("  GET    http://localhost:8080/items/:id")
  println("  DELETE http://localhost:8080/users/:id")
  println("\nPress Ctrl+C to stop the server")

  Async.run {
    IO.run {
      YaesServer.run(server, port = 8080)
    }
  }
}
