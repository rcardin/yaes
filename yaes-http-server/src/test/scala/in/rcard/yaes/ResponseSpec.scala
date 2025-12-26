package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ResponseSpec extends AnyFlatSpec with Matchers {

  "Response.ok" should "create a 200 response with text/plain content type" in {
    val response = Response.ok("Hello!")

    response.status shouldBe 200
    response.body shouldBe "Hello!"
    response.headers should contain("Content-Type" -> "text/plain")
  }

  "Response.created" should "create a 201 response" in {
    val response = Response.created("Resource created")

    response.status shouldBe 201
    response.body shouldBe "Resource created"
  }

  it should "handle empty body" in {
    val response = Response.created()

    response.status shouldBe 201
    response.body shouldBe ""
  }

  "Response.accepted" should "create a 202 response" in {
    val response = Response.accepted("Processing")

    response.status shouldBe 202
    response.body shouldBe "Processing"
  }

  "Response.noContent" should "create a 204 response with empty body" in {
    val response = Response.noContent()

    response.status shouldBe 204
    response.body shouldBe ""
    response.headers shouldBe Map.empty
  }

  "Response.badRequest" should "create a 400 response" in {
    val response = Response.badRequest("Invalid input")

    response.status shouldBe 400
    response.body shouldBe "Invalid input"
  }

  it should "use default message when none provided" in {
    val response = Response.badRequest()

    response.status shouldBe 400
    response.body shouldBe "Bad Request"
  }

  "Response.notFound" should "create a 404 response" in {
    val response = Response.notFound("Resource not found")

    response.status shouldBe 404
    response.body shouldBe "Resource not found"
  }

  it should "use default message when none provided" in {
    val response = Response.notFound()

    response.status shouldBe 404
    response.body shouldBe "Not Found"
  }

  "Response.internalServerError" should "create a 500 response" in {
    val response = Response.internalServerError("Database error")

    response.status shouldBe 500
    response.body shouldBe "Database error"
  }

  it should "use default message when none provided" in {
    val response = Response.internalServerError()

    response.status shouldBe 500
    response.body shouldBe "Internal Server Error"
  }

  "Response case class" should "allow custom headers" in {
    val response = Response(
      status = 200,
      headers = Map("X-Custom" -> "value", "Content-Type" -> "application/json"),
      body = """{"key": "value"}"""
    )

    response.status shouldBe 200
    response.headers should contain("X-Custom" -> "value")
    response.headers should contain("Content-Type" -> "application/json")
  }

  it should "default headers to empty map" in {
    val response = Response(200, body = "OK")

    response.headers shouldBe Map.empty
  }

  it should "default body to empty string" in {
    val response = Response(204)

    response.body shouldBe ""
  }
}
