package in.rcard.yaes.http.server.parsing

import in.rcard.yaes.http.server.*
import in.rcard.yaes.http.server.parsing.HttpWriter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayOutputStream

class HttpWriterSpec extends AnyFlatSpec with Matchers {

  "HttpWriter.reasonPhrase" should "return correct reason phrase for 200 OK" in {
    HttpWriter.reasonPhrase(200) shouldBe "OK"
  }

  it should "return correct reason phrase for 201 Created" in {
    HttpWriter.reasonPhrase(201) shouldBe "Created"
  }

  it should "return correct reason phrase for 202 Accepted" in {
    HttpWriter.reasonPhrase(202) shouldBe "Accepted"
  }

  it should "return correct reason phrase for 204 No Content" in {
    HttpWriter.reasonPhrase(204) shouldBe "No Content"
  }

  it should "return correct reason phrase for 400 Bad Request" in {
    HttpWriter.reasonPhrase(400) shouldBe "Bad Request"
  }

  it should "return correct reason phrase for 404 Not Found" in {
    HttpWriter.reasonPhrase(404) shouldBe "Not Found"
  }

  it should "return correct reason phrase for 405 Method Not Allowed" in {
    HttpWriter.reasonPhrase(405) shouldBe "Method Not Allowed"
  }

  it should "return correct reason phrase for 413 Payload Too Large" in {
    HttpWriter.reasonPhrase(413) shouldBe "Payload Too Large"
  }

  it should "return correct reason phrase for 500 Internal Server Error" in {
    HttpWriter.reasonPhrase(500) shouldBe "Internal Server Error"
  }

  it should "return correct reason phrase for 501 Not Implemented" in {
    HttpWriter.reasonPhrase(501) shouldBe "Not Implemented"
  }

  it should "return correct reason phrase for 503 Service Unavailable" in {
    HttpWriter.reasonPhrase(503) shouldBe "Service Unavailable"
  }

  it should "return correct reason phrase for 505 HTTP Version Not Supported" in {
    HttpWriter.reasonPhrase(505) shouldBe "HTTP Version Not Supported"
  }

  it should "return empty string for unknown status code" in {
    HttpWriter.reasonPhrase(999) shouldBe ""
  }

  it should "return empty string for another unknown status code" in {
    HttpWriter.reasonPhrase(418) shouldBe "" // I'm a teapot
  }

  "HttpWriter.writeResponse" should "write 200 OK response with body" in {
    val response = Response(200, body = "Hello, World!")
    val output   = new ByteArrayOutputStream()

    HttpWriter.writeResponse(output, response)

    val result = output.toString("UTF-8")
    result shouldBe "HTTP/1.1 200 OK\r\nContent-Length: 13\r\n\r\nHello, World!"
  }

  it should "write 404 Not Found with message" in {
    val response = Response(404, body = "Not Found")
    val output   = new ByteArrayOutputStream()

    HttpWriter.writeResponse(output, response)

    val result = output.toString("UTF-8")
    result shouldBe "HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\n\r\nNot Found"
  }

  it should "write 500 Internal Server Error" in {
    val response = Response(500, body = "Error")
    val output   = new ByteArrayOutputStream()

    HttpWriter.writeResponse(output, response)

    val result = output.toString("UTF-8")
    result shouldBe "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 5\r\n\r\nError"
  }

  it should "write response with custom headers" in {
    val response = Response(
      200,
      headers = Map("Content-Type" -> "application/json", "X-Custom" -> "value"),
      body = """{"status":"ok"}"""
    )
    val output = new ByteArrayOutputStream()

    HttpWriter.writeResponse(output, response)

    val result = output.toString("UTF-8")
    // Headers can be in any order, so check parts
    result should startWith("HTTP/1.1 200 OK\r\n")
    result should include("Content-Type: application/json\r\n")
    result should include("X-Custom: value\r\n")
    result should include("Content-Length: 15\r\n")
    result should endWith("\r\n\r\n{\"status\":\"ok\"}")
  }

  it should "write empty body (204 No Content)" in {
    val response = Response(204)
    val output   = new ByteArrayOutputStream()

    HttpWriter.writeResponse(output, response)

    val result = output.toString("UTF-8")
    result shouldBe "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n"
  }

  it should "compute Content-Length correctly for UTF-8" in {
    val response = Response(200, body = "Hëllö, Wørld! 你好")
    val output   = new ByteArrayOutputStream()

    HttpWriter.writeResponse(output, response)

    val result            = output.toString("UTF-8")
    val expectedBodyBytes = "Hëllö, Wørld! 你好".getBytes("UTF-8").length
    result should include(s"Content-Length: $expectedBodyBytes\r\n")
    result should endWith("\r\n\r\nHëllö, Wørld! 你好")
  }

  it should "write response with unknown status code" in {
    val response = Response(999, body = "Unknown")
    val output   = new ByteArrayOutputStream()

    HttpWriter.writeResponse(output, response)

    val result = output.toString("UTF-8")
    result shouldBe "HTTP/1.1 999 \r\nContent-Length: 7\r\n\r\nUnknown"
  }

  it should "handle response with only Content-Length in headers" in {
    val response = Response(200, headers = Map("X-Test" -> "value"), body = "Body")
    val output   = new ByteArrayOutputStream()

    HttpWriter.writeResponse(output, response)

    val result = output.toString("UTF-8")
    result should include("Content-Length: 4\r\n")
    result should include("X-Test: value\r\n")
  }
}
