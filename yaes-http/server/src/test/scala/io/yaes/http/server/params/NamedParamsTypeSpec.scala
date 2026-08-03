package io.yaes.http.server.params

import io.yaes.http.server.*
import io.yaes.http.server.params.query.queryParam
import io.yaes.http.server.routing.Route
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Compile-time checks that path and query parameters are encoded as named tuples: the route type
  * is now writeable, parameter names are surfaced, and wrong names/types are rejected.
  */
class NamedParamsTypeSpec extends AnyFlatSpec with Matchers {

  "A single-path-parameter route" should "have a writeable named-tuple type" in {
    val userId = param[Int]("userId")
    // The full route type can be written explicitly, with the parameter name visible.
    val route: Route[(userId: Int), EmptyParams] =
      GET(p"/users" / userId) { (req, path, _) =>
        Response.ok(s"User ${path.userId}")
      }
    route.toPattern shouldBe "GET /users/:userId"
  }

  "A multi-parameter path" should "have its names and types in declaration order" in {
    val userId = param[Int]("userId")
    val postId = param[Long]("postId")
    val route: Route[(userId: Int, postId: Long), EmptyParams] =
      GET(p"/users" / userId / "posts" / postId) { (req, path, _) =>
        Response.ok(s"${path.userId}/${path.postId}")
      }
    route.toPattern shouldBe "GET /users/:userId/posts/:postId"
  }

  "A combined path and query route" should "carry both named tuples" in {
    val userId = param[Int]("userId")
    val route: Route[(userId: Int), (expand: Boolean)] =
      GET((p"/users" / userId) ? queryParam[Boolean]("expand")) { (req, path, query) =>
        Response.ok(s"${path.userId}:${query.expand}")
      }
    route.toPattern shouldBe "GET /users/:userId?expand:Boolean"
  }

  "Accessing a path parameter by the wrong name" should "not compile" in {
    assertDoesNotCompile(
      """
      val userId = param[Int]("userId")
      GET(p"/users" / userId) { (req, path, _) =>
        Response.ok(s"User ${path.postId}")
      }
      """
    )
  }

  "Declaring a route type with the wrong parameter name" should "not compile" in {
    assertTypeError(
      """
      val userId = param[Int]("userId")
      val route: Route[(wrongName: Int), EmptyParams] =
        GET(p"/users" / userId) { (req, path, _) =>
          Response.ok("x")
        }
      """
    )
  }

  "Declaring a route type with the wrong parameter type" should "not compile" in {
    assertTypeError(
      """
      val userId = param[Int]("userId")
      val route: Route[(userId: String), EmptyParams] =
        GET(p"/users" / userId) { (req, path, _) =>
          Response.ok("x")
        }
      """
    )
  }
}
