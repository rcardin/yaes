package in.rcard.yaes

import scala.concurrent.ExecutionContext.Implicits.global

/** Example server demonstrating type-safe path parameter usage.
  *
  * This example shows how to use the new compile-time safe routing DSL with typed path parameters.
  * Parameters are automatically validated and parsed - handlers receive typed values directly.
  *
  * To run this server:
  * {{{
  * sbt "yaes-http-server/test:runMain in.rcard.yaes.PathParamExampleServer"
  * }}}
  *
  * Then test with:
  * {{{
  * curl http://localhost:8080/users/123
  * curl http://localhost:8080/users/abc  # Returns 400 error (invalid Int)
  * curl http://localhost:8080/users/42/posts/99
  * }}}
  */
object PathParamExampleServer extends App {

  // Define reusable parameters
  val userId = param[Int]("userId")
  val id = param[Int]("id")
  val postId = param[Int]("postId")
  val itemId = param[Long]("itemId")

  val server = YaesServer.route(
    // Exact route (takes priority over parameterized /users/:id)
    GET(p"/users/me") { req =>
      Response.ok("Current user profile")
    },

    // Simple parameterized route - PathParamError automatically handled
    GET(p"/users" / id) { (req, userId: Int) =>
      Response.ok(s"User $userId")
    },

    // Route with multiple parameters
    GET(p"/users" / userId / "posts" / postId) { (req, uid: Int, pid: Int) =>
      Response.ok(s"Post $pid from User $uid")
    },

    // Exact route for listing users
    GET(p"/users") { req =>
      Response.ok("List of all users")
    },

    // Using Long parameters
    GET(p"/items" / itemId) { (req, id: Long) =>
      Response.ok(s"Item $id")
    },

    // DELETE with path parameter
    DELETE(p"/users" / id) { (req, userId: Int) =>
      // In real app, would delete the user here
      Response.noContent()
    }
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
