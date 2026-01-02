package in.rcard.yaes.http.server

import in.rcard.yaes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues.*
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

class ServerShutdownSpec extends AnyFlatSpec with Matchers {

  "Server" should "start and shutdown cleanly" in {
    var shutdownHookCalled = false

    val result = IO.runBlocking(10.seconds) {
      Async.run {
        val serverDef = YaesServer
          .route(
            GET(p"/hello") { req => Response.ok("Hello!") }
          )
          .onShutdown(() => {
            shutdownHookCalled = true
          })

        val server = serverDef.run(port = 8888)

        // Shutdown immediately
        server.shutdown()

        "completed"
      }
    }

    result.success.value shouldBe "completed"
    shutdownHookCalled shouldBe true
  }

  it should "allow server to handle requests before shutdown" in {
    val result = IO.runBlocking(10.seconds) {
      Async.run {
        val serverDef = YaesServer.route(
          GET(p"/test") { req => Response.ok("Test") }
        )

        val server = serverDef.run(port = 8889)

        // Give server time to start
        Async.delay(100.millis)

        // Could add actual HTTP request here in future

        server.shutdown()
      }
    }

    // Test passes if no exceptions thrown
    result.isSuccess shouldBe true
  }

  it should "work without shutdown hook" in {
    val result = IO.runBlocking(10.seconds) {
      Async.run {
        val serverDef = YaesServer.route(
          GET(p"/hello") { req => Response.ok("Hello!") }
        )

        val server = serverDef.run(port = 8890)
        server.shutdown()

        "completed"
      }
    }

    result.success.value shouldBe "completed"
  }

  it should "handle multiple shutdown calls gracefully" in {
    val result = IO.runBlocking(10.seconds) {
      Async.run {
        val serverDef = YaesServer.route(
          GET(p"/hello") { req => Response.ok("Hello!") }
        )

        val server = serverDef.run(port = 8891)

        // First shutdown - should succeed
        server.shutdown()

        // Second shutdown - should print message and not throw
        server.shutdown()

        // Third shutdown - should also be safe
        server.shutdown()

        "completed"
      }
    }

    result.success.value shouldBe "completed"
  }
}
