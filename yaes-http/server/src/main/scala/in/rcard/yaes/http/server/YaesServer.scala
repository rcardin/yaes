package in.rcard.yaes.http.server

import in.rcard.yaes.*
import in.rcard.yaes.Async.Deadline
import in.rcard.yaes.http.server.parsing.{HttpParser, HttpWriter}
import in.rcard.yaes.http.server.routing.Route
import java.net.{ServerSocket, Socket, SocketException}
import scala.concurrent.duration.DurationInt
import scala.util.boundary
import scala.util.boundary.break

/** Server configuration and route definitions.
  *
  * Represents a pure description of an HTTP server with its routes. The server is not started until
  * [[YaesServer.run]] is called.
  *
  * @param routes
  *   The routes mapping requests to handlers
  * @param shutdownHook
  *   Optional callback to run when the server shuts down
  */
case class ServerDef(routes: Routes) {

  /** Run the HTTP server.
    *
    * Starts the server with the specified configuration. The server runs until shutdown() is called
    * or the JVM shuts down.
    *
    * This is a convenience method equivalent to YaesServer.run(this, config).
    *
    * @param config
    *   Server configuration including port, deadline, and size limits
    * @param log
    *   Log context for lifecycle logging
    * @param shutdown
    *   Shutdown context for graceful shutdown coordination
    * @return
    *   Unit after server stops
    */
  def run(config: ServerConfig)(using
      Log,
      Shutdown
  ): Unit = {
    YaesServer.run(this, config)
  }

  /** Run the HTTP server (backward-compatible overload).
    *
    * Starts the server on the specified port with a deadline. This is a convenience method that
    * creates a ServerConfig internally.
    *
    * @param port
    *   Port to bind to
    * @param deadline
    *   Maximum time to wait for in-flight requests after shutdown is initiated (default: 30
    *   seconds)
    * @param log
    *   Log context for lifecycle logging
    * @param shutdown
    *   Shutdown context for graceful shutdown coordination
    * @return
    *   Unit after server stops
    */
  def run(port: Int, deadline: Deadline = Deadline.after(30.seconds))(using
      Log,
      Shutdown
  ): Unit = {
    YaesServer.run(this, ServerConfig(port = port, deadline = deadline))
  }
}

/** HTTP server built on YAES effects and java.net.ServerSocket.
  *
  * Provides a simple, effect-based HTTP server using virtual threads for request handling. Each
  * incoming request is automatically handled in its own fiber (virtual thread) via [[Async.fork]]
  * under YAES structured concurrency.
  *
  * Example:
  * {{{
  * import in.rcard.yaes.http.server.*
  * import scala.concurrent.duration.*
  *
  * val server = YaesServer.route(
  *   GET / "hello" -> { req => Response.ok("Hello!") },
  *   GET / "delay" -> { req =>
  *     Async.delay(1.second)
  *     Response.ok("Delayed response")
  *   }
  * )
  *
  * Shutdown.run {
  *   Raise.run {
  *     Output.run {
  *       server.run(ServerConfig(port = 8080))
  *     }
  *   }
  * }
  * }}}
  */
object YaesServer {

  /** Define server routes.
    *
    * Creates a pure server definition from type-safe route specifications.
    *
    * Handlers receive a [[Request]] and typed parameters, returning a [[Response]]. They
    * automatically have access to YAES effect contexts (Async, etc.) when the server is run.
    *
    * Example:
    * {{{
    * val userId = param[Int]("userId")
    * val postId = param[Long]("postId")
    *
    * val server = YaesServer.route(
    *   GET(p"/health") { req =>
    *     Response.ok("OK")
    *   },
    *   GET(p"/users" / userId) { (req, id: Int) =>
    *     Response.ok(s"User $id")
    *   },
    *   POST(p"/users" / userId / "posts" / postId) { (req, uid: Int, pid: Long) =>
    *     Response.ok(s"Created post $pid for user $uid")
    *   }
    * )
    * }}}
    *
    * @param routes
    *   Variable argument list of typed Route instances
    * @return
    *   A ServerDef representing the server configuration
    */
  def route(routes: Route[?, ?]*): ServerDef = {
    ServerDef(Routes(routes*))
  }

  /** Run the HTTP server.
    *
    * Starts an HTTP server using java.net.ServerSocket, handling each incoming request in its own
    * fiber under YAES structured concurrency.
    *
    * **Effect Requirements:**
    *   - Requires [[Log]] context for lifecycle logging
    *   - Requires [[Shutdown]] context for graceful shutdown coordination with JVM signals
    *
    * **Request Handling:**
    *   - Each request is handled in a forked fiber via [[Async.fork]]
    *   - Requests are parsed using HTTP/1.1 protocol parser
    *   - Handlers receive the full YAES effect context
    *   - Errors in handlers result in 500 Internal Server Error responses
    *   - Unmatched routes result in 404 Not Found responses
    *   - Parse errors result in appropriate error responses (400, 413, 501, 505)
    *
    * **Automatic Shutdown Hook:**
    *   - JVM shutdown hooks are automatically managed by the [[Shutdown]] effect
    *   - When the JVM receives SIGTERM, SIGINT, or begins shutdown (e.g., Ctrl+C, container stop),
    *     the [[Shutdown]] effect triggers graceful shutdown
    *   - This ensures clean termination in containers (Kubernetes, Docker) and local development
    *
    * **Graceful Shutdown:**
    *   - Shutdown can be triggered via JVM shutdown hook or by calling
    *     [[Shutdown.initiateShutdown]]
    *   - New requests during shutdown receive 503 Service Unavailable
    *   - All in-flight requests (already accepted) complete before shutdown finishes
    *   - This is enforced by [[Async.withGracefulShutdown]] which coordinates with [[Shutdown]]
    *   - If in-flight requests don't complete within the deadline, a warning is logged and shutdown
    *     completes normally
    *   - Server resources are managed via [[Resource]] effect for automatic cleanup
    *
    * Example:
    * {{{
    * import in.rcard.yaes.http.server.*
    * import scala.concurrent.duration.*
    *
    * val server = YaesServer.route(
    *   GET / "hello" -> { req => Response.ok("Hello!") }
    * )
    *
    * Shutdown.run {
    *   Log.run() {
    *     server.run(ServerConfig(port = 8080, maxBodySize = 5.megabytes))
    *   }
    * }
    * }}}
    *
    * @param serverDef
    *   Server configuration with routes
    * @param config
    *   Server configuration including port, deadline, and size limits
    * @param log
    *   Log context for lifecycle logging
    * @param shutdown
    *   Shutdown context for graceful shutdown coordination
    * @return
    *   Unit after server stops
    */
  def run(serverDef: ServerDef, config: ServerConfig)(using
      log: Log,
      shutdown: Shutdown
  ): Unit = {
    val logger = Log.getLogger("YaesServer")
    Raise.onError {
      Resource.run {
        // Install server socket as a resource with automatic cleanup
        Resource.install({
          Async.withGracefulShutdown(config.deadline) {
            logger.info(s"Starting server on port ${config.port}")
            val serverSocket = new ServerSocket(config.port)
            logger.info(s"Server ready, listening on port ${config.port}")

            // Accept loop - runs as main fiber in structured scope
            // Each request is handled in a forked fiber
            // Virtual thread interruption breaks accept() on shutdown
            try {
              while (!Shutdown.isShuttingDown()) {
                try {
                  val socket = serverSocket.accept()
                  // Fork a fiber to handle this request
                  Async.fork {
                    handleConnection(socket, serverDef.routes, config)
                  }
                } catch {
                  case _: SocketException if Shutdown.isShuttingDown() =>
                    // Expected during shutdown - ServerSocket was closed
                    // Break out of the accept loop
                    ()
                  case ex: Exception =>
                    // Log unexpected errors but continue accepting
                    logger.error(s"Error accepting connection: ${ex.getMessage}")
                }
              }
            } finally {
              logger.info("Server shutting down...")
            }

            serverSocket // Return the server socket for cleanup
          }
        }) { serverSocket =>
          // Cleanup: close the server socket
          serverSocket.close()
          logger.info("Server stopped")
        }
      }
    } { (_: Async.ShutdownTimedOut) =>
      // Shutdown deadline exceeded - log warning and complete normally
      logger.warn(
        s"Shutdown deadline (${config.deadline}) exceeded, some requests may not have completed"
      )
    }
  }

  /** Backward-compatible run method with port and deadline.
    *
    * @param serverDef
    *   Server configuration with routes
    * @param port
    *   Port to bind to
    * @param deadline
    *   Maximum time to wait for in-flight requests after shutdown is initiated
    * @param log
    *   Log context for lifecycle logging
    * @param shutdown
    *   Shutdown context for graceful shutdown coordination
    * @return
    *   Unit after server stops
    */
  def run(serverDef: ServerDef, port: Int, deadline: Deadline)(using
      log: Log,
      shutdown: Shutdown
  ): Unit = {
    run(serverDef, ServerConfig(port = port, deadline = deadline))
  }

  /** Handle a single client connection.
    *
    * Parses the HTTP request, routes it to the appropriate handler, and writes the response.
    * Handles all errors by returning appropriate HTTP error responses. Closes the socket when done.
    *
    * @param socket
    *   The client socket to handle
    * @param routes
    *   The routes to match against
    * @param config
    *   Server configuration for size limits
    * @param shutdown
    *   Shutdown context to check if server is shutting down
    */
  private def handleConnection(socket: Socket, routes: Routes, config: ServerConfig)(using
      shutdown: Shutdown
  ): Unit = {
    Resource.run {
      Resource.ensuring {
        socket.close()
      }

      try {
        boundary {
          // Check if server is shutting down
          if (Shutdown.isShuttingDown()) {
            val response = Response.serviceUnavailable("Server is shutting down")
            HttpWriter.writeResponse(socket.getOutputStream, response)
            break()
          }

          // Parse the HTTP request and handle parse errors
          Raise.onError {
            val request = HttpParser.parseRequest(socket.getInputStream, config)

            // Route the request
            try {
              val response = routes.handle(request)
              HttpWriter.writeResponse(socket.getOutputStream, response)
            } catch {
              case ex: Exception =>
                // Handler threw exception - return 500
                val errorResponse = Response.internalServerError(ex.getMessage)
                HttpWriter.writeResponse(socket.getOutputStream, errorResponse)
            }
          } { parseError =>
            // Parse error - convert to response and write
            val errorResponse = parseError.toResponse
            HttpWriter.writeResponse(socket.getOutputStream, errorResponse)
          }
        }
      } catch {
        case _: SocketException =>
          // Client disconnected or socket error - silent close (expected behavior)
          ()
        case ex: Exception =>
          // Unexpected error - try to send 500 if possible, then close
          try {
            val errorResponse = Response.internalServerError("Internal server error")
            HttpWriter.writeResponse(socket.getOutputStream, errorResponse)
          } catch {
            case _: Exception =>
              // Can't even write the error response - just close
              ()
          }
      }
    }
  }
}
