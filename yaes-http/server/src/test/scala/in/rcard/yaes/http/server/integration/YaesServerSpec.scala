package in.rcard.yaes.http.server.integration

import in.rcard.yaes.*
import in.rcard.yaes.Async.ShutdownTimedOut
import in.rcard.yaes.Log.given
import in.rcard.yaes.http.server.*
import in.rcard.yaes.http.server.PathBuilder.given
import in.rcard.yaes.http.server.params.query.queryParam
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import scala.concurrent.duration.*

/** Integration tests for YaesServer lifecycle management.
  *
  * These tests verify actual server startup, request handling, and shutdown behavior by starting
  * real HTTP servers and making actual HTTP requests.
  */
class YaesServerSpec extends AnyFlatSpec with Matchers {

  // Test infrastructure
  private def findFreePort(): Int = {
    val socket = new ServerSocket(0)
    val port   = socket.getLocalPort
    socket.close()
    port
  }

  private val client = HttpClient.newHttpClient()

  // Short deadline for tests to avoid 30-second waits during shutdown
  private val testDeadline = Async.Deadline.after(2.seconds)

  /** Wait for server to be ready by attempting to connect with retries.
    *
    * @param port
    *   Port to check
    * @param maxRetries
    *   Maximum number of connection attempts
    * @param delayBetweenRetries
    *   Time to wait between attempts
    */
  private def waitForServer(
      port: Int,
      maxRetries: Int = 20,
      delayBetweenRetries: scala.concurrent.duration.FiniteDuration = 50.millis
  )(using Async): Unit = {
    var retries   = 0
    var connected = false

    while (retries < maxRetries && !connected) {
      try {
        val socket = new java.net.Socket()
        socket.connect(new java.net.InetSocketAddress("localhost", port), 100)
        socket.close()
        connected = true
      } catch {
        case _: Exception =>
          retries += 1
          if (retries < maxRetries) {
            Async.delay(delayBetweenRetries)
          }
      }
    }

    if (!connected) {
      throw new RuntimeException(s"Server did not start on port $port after $maxRetries retries")
    }
  }

  "YaesServer" should "start and accept requests" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/test") { req =>
                Response.ok("Test response")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Make HTTP request
            val request = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/test"))
              .GET()
              .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            // Verify response
            response.statusCode() shouldBe 200
            response.body() shouldBe "Test response"

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "handle multiple concurrent requests" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/concurrent") { req =>
                // Simulate some processing time
                Async.delay(50.millis)
                Response.ok("Concurrent response")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Make multiple concurrent requests using Java's HttpClient which supports concurrent requests
            import scala.jdk.CollectionConverters.*
            val requests = (1 to 5).map { i =>
              HttpRequest
                .newBuilder()
                .uri(URI.create(s"http://localhost:$port/concurrent"))
                .GET()
                .build()
            }

            // Send all requests concurrently using the client's async API
            val responseFutures = requests.map { request =>
              client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            }

            // Wait for all to complete
            val responses = responseFutures.map(_.get())

            // Verify all responses
            responses.foreach { response =>
              response.statusCode() shouldBe 200
              response.body() shouldBe "Concurrent response"
            }

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "stop cleanly via Resource cleanup" in {
    val port = findFreePort()

    // First server lifecycle - wrapped in its own Shutdown.run to have independent shutdown state
    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/cleanup") { req =>
                Response.ok("Cleanup test")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Make a request to verify server is running
            val request1 = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/cleanup"))
              .GET()
              .build()

            val response1 = client.send(request1, HttpResponse.BodyHandlers.ofString())
            response1.statusCode() shouldBe 200

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }

    // Wait a bit for port to be fully released
    Thread.sleep(50)

    // Second server lifecycle - verify port is available for reuse
    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server2 = YaesServer.route(
              GET(p"/reuse") { req =>
                Response.ok("Port reused")
              }
            )

            val serverFiber2 = Async.fork("server2") {
              server2.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Make a request to verify new server is running
            val request2 = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/reuse"))
              .GET()
              .build()

            val response2 = client.send(request2, HttpResponse.BodyHandlers.ofString())
            response2.statusCode() shouldBe 200
            response2.body() shouldBe "Port reused"

            // Shutdown second server
            Shutdown.initiateShutdown()
            serverFiber2.join()
          }
        }
      }
    }
  }

  it should "handle POST request with body" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              POST(p"/echo") { req =>
                Response.ok(s"Received: ${req.body}")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Make POST request with body
            val request = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/echo"))
              .POST(HttpRequest.BodyPublishers.ofString("Hello, Server!"))
              .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            // Verify response
            response.statusCode() shouldBe 200
            response.body() shouldBe "Received: Hello, Server!"

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "extract path parameters correctly" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val userId = param[Int]("userId")
            val server = YaesServer.route(
              GET(p"/users" / userId) { (req, id: Int) =>
                Response.ok(s"User ID: $id")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Make request with path parameter
            val request = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/users/42"))
              .GET()
              .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            // Verify response
            response.statusCode() shouldBe 200
            response.body() shouldBe "User ID: 42"

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "extract query parameters correctly" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/search" ? queryParam[String]("q")) { req =>
                // Note: In the current implementation, query parameters are extracted
                // during routing but not yet passed to the handler. We can only verify
                // that the route matches correctly.
                Response.ok(s"Search endpoint")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Make request with query parameter
            val request = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/search?q=scala"))
              .GET()
              .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            // Verify response (route matched successfully with query parameter)
            response.statusCode() shouldBe 200
            response.body() shouldBe "Search endpoint"

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "return 404 for unknown route" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/known") { req =>
                Response.ok("Known route")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Make request to unknown route
            val request = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/unknown"))
              .GET()
              .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            // Verify response
            response.statusCode() shouldBe 404

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "return 500 for handler exception" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/error") { req =>
                throw new RuntimeException("Handler failed")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Make request that triggers exception
            val request = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/error"))
              .GET()
              .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            // Verify response
            response.statusCode() shouldBe 500

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "return 501 for unknown HTTP method" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/test") { req =>
                Response.ok("Test")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Make request with unsupported method (TRACE)
            // Note: Java HttpClient doesn't support custom methods easily,
            // so we'll use a raw socket connection
            var socket: java.net.Socket = null
            try {
              socket = new java.net.Socket("localhost", port)
              socket.setSoTimeout(2000) // 2 second timeout
              val out = socket.getOutputStream
              val in  = socket.getInputStream

              // Send TRACE request
              out.write("TRACE /test HTTP/1.1\r\n".getBytes("UTF-8"))
              out.write("Host: localhost\r\n".getBytes("UTF-8"))
              out.write("\r\n".getBytes("UTF-8"))
              out.flush()

              // Read all available bytes from response
              val buffer    = new Array[Byte](4096)
              val bytesRead = in.read(buffer)
              val response  = new String(buffer, 0, bytesRead, "UTF-8")

              // Verify response (should be 501 Not Implemented)
              response should include("501")
            } finally {
              if (socket != null) socket.close()
            }

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }

  // Shutdown Tests

  it should "complete in-flight requests during graceful shutdown" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/slow") { req =>
                // Simulate slow processing (reduced from 1s to 200ms for faster tests)
                Async.delay(200.millis)
                Response.ok("Slow response completed")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Start a slow request in a background fiber
            val requestFiber = Async.fork("slow-request") {
              val request = HttpRequest
                .newBuilder()
                .uri(URI.create(s"http://localhost:$port/slow"))
                .GET()
                .build()

              val response = client.send(request, HttpResponse.BodyHandlers.ofString())
              response.statusCode() shouldBe 200
              response.body() shouldBe "Slow response completed"
            }

            // Give the request time to start processing
            Async.delay(50.millis)

            // Initiate shutdown while request is in-flight
            Shutdown.initiateShutdown()

            // Wait for the request to complete
            requestFiber.join()

            // Wait for server to stop
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "return 503 Service Unavailable for new requests during shutdown" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/test") { req =>
                Response.ok("Normal response")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Verify server works before shutdown
            val request1 = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/test"))
              .GET()
              .build()
            val response1 = client.send(request1, HttpResponse.BodyHandlers.ofString())
            response1.statusCode() shouldBe 200

            // Initiate shutdown
            Shutdown.initiateShutdown()

            // Give shutdown time to register
            Async.delay(50.millis)

            // Try to make a new request after shutdown initiated
            val request2 = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/test"))
              .GET()
              .build()
            val response2 = client.send(request2, HttpResponse.BodyHandlers.ofString())

            // Verify 503 response
            response2.statusCode() shouldBe 503

            // Wait for server to stop
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "clean up resources and allow port reuse after shutdown" in {
    // This test is already covered by "stop cleanly via Resource cleanup"
    // which verifies that the port can be reused after server shutdown.
    // Including here for completeness of Phase 5.2 requirements.
    succeed
  }

  // Error Handling Tests

  it should "return 400 Bad Request for malformed request" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/test") { req =>
                Response.ok("Test")
              }
            )

            // Start server in a background fiber with short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Send malformed request (missing HTTP version)
            var socket: java.net.Socket = null
            try {
              socket = new java.net.Socket("localhost", port)
              socket.setSoTimeout(2000)
              val out = socket.getOutputStream
              val in  = socket.getInputStream

              // Send malformed request line (only two parts instead of three)
              out.write("GET /test\r\n".getBytes("UTF-8"))
              out.write("Host: localhost\r\n".getBytes("UTF-8"))
              out.write("\r\n".getBytes("UTF-8"))
              out.flush()

              // Read response
              val buffer    = new Array[Byte](4096)
              val bytesRead = in.read(buffer)
              val response  = new String(buffer, 0, bytesRead, "UTF-8")

              // Verify 400 Bad Request
              response should include("400")
            } finally {
              if (socket != null) socket.close()
            }

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "return 413 Payload Too Large when body exceeds maxBodySize" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              POST(p"/upload") { req =>
                Response.ok(s"Received ${req.body.length} bytes")
              }
            )

            // Start server with small maxBodySize (1 KB) and short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, maxBodySize = 1024, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Create a body larger than 1 KB (2 KB)
            val largeBody = "x" * 2048

            // Send POST request with large body
            val request = HttpRequest
              .newBuilder()
              .uri(URI.create(s"http://localhost:$port/upload"))
              .POST(HttpRequest.BodyPublishers.ofString(largeBody))
              .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            // Verify 413 Payload Too Large
            response.statusCode() shouldBe 413

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }

  it should "return 400 Bad Request when headers exceed maxHeaderSize" in {
    val port = findFreePort()

    Shutdown.run {
      Raise.run {
        Async.run {
          Log.run(level = Log.Level.Info) {
            val server = YaesServer.route(
              GET(p"/test") { req =>
                Response.ok("Test")
              }
            )

            // Start server with small maxHeaderSize (256 bytes) and short deadline
            val serverFiber = Async.fork("server") {
              server.run(ServerConfig(port = port, maxHeaderSize = 256, deadline = testDeadline))
            }

            // Wait for server to be ready
            waitForServer(port)

            // Send request with large headers using raw socket
            var socket: java.net.Socket = null
            try {
              socket = new java.net.Socket("localhost", port)
              socket.setSoTimeout(2000)
              val out = socket.getOutputStream
              val in  = socket.getInputStream

              // Send request line
              out.write("GET /test HTTP/1.1\r\n".getBytes("UTF-8"))
              out.write("Host: localhost\r\n".getBytes("UTF-8"))

              // Add many large headers to exceed 256 bytes
              // Each header is about 30 bytes, so 10 headers = ~300 bytes
              for (i <- 1 to 10) {
                out.write(s"X-Custom-Header-$i: value$i\r\n".getBytes("UTF-8"))
              }

              // End headers
              out.write("\r\n".getBytes("UTF-8"))
              out.flush()

              // Read response
              val buffer    = new Array[Byte](4096)
              val bytesRead = in.read(buffer)
              val response  = new String(buffer, 0, bytesRead, "UTF-8")

              // Verify 400 Bad Request
              response should include("400")
            } finally {
              if (socket != null) socket.close()
            }

            // Shutdown
            Shutdown.initiateShutdown()
            serverFiber.join()
          }
        }
      }
    }
  }
}
