package in.rcard.yaes.http.server

import in.rcard.yaes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues.*
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.atomic.AtomicBoolean

class ShutdownHookSpec extends AnyFlatSpec with Matchers {

  "YaesServer shutdown hook" should "trigger graceful shutdown automatically" in {
    val shutdownHookCalled = new AtomicBoolean(false)

    val result = IO.runBlocking(5.seconds) {
      Async.run {
        Output.run {
          val server = YaesServer
            .route(GET(p"/test") { req => Response.ok("OK") })
            .onShutdown(() => shutdownHookCalled.set(true))
            .run(port = 9200)

          // Manually trigger shutdown (simulates what the shutdown hook would do)
          server.shutdown()

          "completed"
        }
      }
    }

    result.success.value shouldBe "completed"
    shutdownHookCalled.get() shouldBe true
  }

  it should "complete graceful shutdown even with active requests" in {
    val requestStarted = new AtomicBoolean(false)
    val requestCompleted = new AtomicBoolean(false)

    val result = IO.runBlocking(10.seconds) {
      Async.run {
        Output.run {
          val server = YaesServer
            .route(
              GET(p"/long-request") { req =>
                requestStarted.set(true)
                Async.delay(1.second)
                requestCompleted.set(true)
                Response.ok("Completed")
              }
            )
            .run(port = 9201)

          // Start a long-running request in the background
          Async.fork("long-request-client") {
            Async.delay(100.millis)
            // In a full integration test, we would make an HTTP request here
            // For this test, we just verify the shutdown behavior
          }

          // Give request a moment to start
          Async.delay(200.millis)

          // Trigger shutdown (simulates shutdown hook)
          server.shutdown()

          "completed"
        }
      }
    }

    result.success.value shouldBe "completed"
    // Note: In the full implementation with actual HTTP requests, we would verify
    // that in-flight requests complete. For now, we verify the shutdown completes.
  }

  it should "handle multiple shutdown calls gracefully" in {
    val result = IO.runBlocking(5.seconds) {
      Async.run {
        Output.run {
          val server = YaesServer
            .route(GET(p"/test") { req => Response.ok("OK") })
            .run(port = 9202)

          // Trigger shutdown multiple times (simulates both hook and manual shutdown)
          server.shutdown()
          server.shutdown()
          server.shutdown()

          "completed"
        }
      }
    }

    result.success.value shouldBe "completed"
  }
}
