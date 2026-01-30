package in.rcard.yaes.http.server

import in.rcard.yaes.*
import in.rcard.yaes.Async.{Deadline, ShutdownTimedOut}
import com.sun.net.httpserver.{HttpServer => JdkHttpServer, HttpExchange}
import java.net.InetSocketAddress
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

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
    * Starts the server on the specified port and returns a Server handle. The server runs until
    * shutdown() is called on the returned Server.
    *
    * This is a convenience method equivalent to YaesServer.run(this, port).
    *
    * @param port
    *   Port to bind to
    * @param deadline
    *   Maximum time to wait for in-flight requests after shutdown is initiated (default: 30
    *   seconds)
    * @param async
    *   Async context for forking request fibers and shutdown coordination
    * @param output
    *   Output context for logging shutdown status
    * @param shutdown
    *   Shutdown context for graceful shutdown coordination
    * @return
    *   A Server instance that can be used to stop the server
    */
  def run(port: Int, deadline: Deadline = Deadline.after(30.seconds))(using
      Async,
      Output,
      Shutdown,
      Raise[ShutdownTimedOut]
  ): Unit = {
    YaesServer.run(this, port, deadline)
  }
}

/** HTTP server built on YAES effects and JDK HttpServer.
  *
  * Provides a simple, effect-based HTTP server using virtual threads for request handling. Each
  * incoming request is automatically handled in its own fiber (virtual thread) via [[Async.fork]].
  *
  * Example:
  * {{{
  * import in.rcard.yaes.http.server.*
  * import scala.concurrent.duration.*
  *
  * val server = YaesServer.route(
  *   (Method.GET, "/hello", (req: Request) => Response.ok("Hello!")),
  *   (Method.GET, "/delay", (req: Request) => {
  *     Async.delay(1.second)
  *     Response.ok("Delayed response")
  *   })
  * )
  *
  * Shutdown.run {
  *   Raise.run {
  *     Async.run {
  *       Output.run {
  *         YaesServer.run(server, port = 8080)
  *       }
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
    * Starts an HTTP server on the specified port, handling each incoming request in its own fiber.
    * Returns a Server handle that can be used to programmatically stop the server.
    *
    * **Effect Requirements:**
    *   - Requires [[Async]] context for fiber-per-request handling and shutdown coordination
    *   - Requires [[Output]] context for logging shutdown status
    *   - Requires [[Shutdown]] context for graceful shutdown coordination with JVM signals
    *   - Requires [[Raise]]`[`[[ShutdownTimedOut]]`]` context for handling shutdown timeout errors
    *
    * **Request Handling:**
    *   - Each request is automatically handled in a forked fiber via [[Async.fork]]
    *   - Handlers receive the full YAES effect context
    *   - Errors in handlers result in 500 Internal Server Error responses
    *   - Unmatched routes result in 404 Not Found responses
    *   - HttpExchange resources are automatically closed via [[Resource]] effect (internal)
    *
    * **Automatic Shutdown Hook:**
    *   - JVM shutdown hooks are automatically managed by the [[Shutdown]] effect
    *   - When the JVM receives SIGTERM, SIGINT, or begins shutdown (e.g., Ctrl+C, container stop),
    *     the [[Shutdown]] effect triggers graceful shutdown
    *   - This ensures clean termination in containers (Kubernetes, Docker) and local development
    *
    * **Graceful Shutdown:**
    *   - Call [[Server.shutdown]] on the returned handle to stop the server programmatically
    *   - Shutdown is also triggered automatically via JVM shutdown hook (see above)
    *   - New requests are immediately rejected with 503 Service Unavailable
    *   - All in-flight requests (already accepted) complete before shutdown finishes
    *   - This is enforced by [[Async.withGracefulShutdown]] which coordinates with [[Shutdown]]
    *   - If in-flight requests don't complete within the deadline, [[ShutdownTimedOut]] is raised
    *   - Shutdown hook (if registered) runs after all requests complete
    *   - Server lifecycle is managed via [[Resource]] effect for automatic cleanup
    *
    * Example:
    * {{{
    * val server = YaesServer.route(
    *   (Method.GET, "/hello", (req: Request) => Response.ok("Hello!"))
    * )
    *
    * Shutdown.run {
    *   Raise.run {
    *     Async.run {
    *       Output.run {
    *         println("Starting server on port 8080...")
    *         val serverHandle = YaesServer.run(server, port = 8080)
    *
    *         // ... do work ...
    *
    *         serverHandle.shutdown()
    *         println("Server stopped")
    *       }
    *     }
    *   }
    * }
    * }}}
    *
    * @param serverDef
    *   Server configuration with routes
    * @param port
    *   Port to bind to
    * @param deadline
    *   Maximum time to wait for in-flight requests after shutdown is initiated (default: 30
    *   seconds)
    * @param async
    *   Async context for forking request fibers and shutdown coordination
    * @param output
    *   Output context for logging shutdown status
    * @param shutdown
    *   Shutdown context for graceful shutdown coordination
    * @return
    *   A Server instance that can be used to stop the server
    */
  def run(serverDef: ServerDef, port: Int, deadline: Deadline = Deadline.after(30.seconds))(using
      async: Async,
      output: Output,
      shutdown: Shutdown,
      raise: Raise[ShutdownTimedOut]
  ): Unit = {
    Resource.run {
      // Install server as a resource with automatic cleanup
      Resource.install({
        Async.withGracefulShutdown(deadline) {
          val srv = JdkHttpServer.create(new InetSocketAddress(port), 0)

          srv.setExecutor { cmd =>
            Async.fork(s"http-request-${cmd.hashCode()}") {
              cmd.run()
            }
          }

          srv.createContext(
            "/",
            (exchange: HttpExchange) => {
              Resource.run {
                Resource.ensuring {
                  exchange.close()
                }
                // Check if server is accepting requests using Shutdown effect
                if (Shutdown.isShuttingDown()) {
                  // Server is shutting down - reject with 503
                  val shutdownResponse = Response.serviceUnavailable("Server is shutting down")
                  writeResponse(exchange, shutdownResponse)
                  exchange.close()
                } else {
                  handleRequest(exchange, serverDef.routes)
                }
              }
            }
          )

          srv.start()
          srv // Return the started server
        }
      }) { server => 
        // Stop the JDK server immediately (0 = no grace period)
        server.stop(0)
      }
    }
  }

  private def handleRequest(
      exchange: HttpExchange,
      routes: Routes
  )(using async: Async): Unit = {
    try {
      // Parse request
      val request = parseRequest(exchange)

      // Route to handler
      val response = routes.handle(request)

      // Write response
      writeResponse(exchange, response)
    } catch {
      case ex: Exception =>
        val errorResponse = Response.internalServerError(ex.getMessage)
        writeResponse(exchange, errorResponse)
    }
  }

  private def parseRequest(exchange: HttpExchange): Request = {
    val method  = Method.valueOf(exchange.getRequestMethod)
    val uri     = exchange.getRequestURI
    val path    = uri.getPath
    val headers = exchange.getRequestHeaders.asScala.map { case (k, v) =>
      k -> v.asScala.headOption.getOrElse("")
    }.toMap
    val body        = new String(exchange.getRequestBody.readAllBytes())
    val queryString = parseQueryString(Option(uri.getQuery).getOrElse(""))

    Request(method, path, headers, body, queryString)
  }

  private def parseQueryString(query: String): Map[String, List[String]] = {
    if (query.isEmpty) {
      Map.empty
    } else {
      query.split("&").foldLeft(Map.empty[String, List[String]]) { (acc, pair) =>
        pair.split("=", 2) match {
          case Array(key, value) =>
            val decodedKey   = java.net.URLDecoder.decode(key, "UTF-8")
            val decodedValue = java.net.URLDecoder.decode(value, "UTF-8")
            acc.updatedWith(decodedKey) {
              case Some(existing) => Some(existing :+ decodedValue)
              case None           => Some(List(decodedValue))
            }
          case Array(key) =>
            val decodedKey = java.net.URLDecoder.decode(key, "UTF-8")
            acc.updatedWith(decodedKey) {
              case Some(existing) => Some(existing :+ "")
              case None           => Some(List(""))
            }
          case _ => acc
        }
      }
    }
  }

  private def writeResponse(exchange: HttpExchange, response: Response): Unit = {
    // Set headers
    response.headers.foreach { case (k, v) =>
      exchange.getResponseHeaders.set(k, v)
    }

    // Write response
    val responseBytes = response.body.getBytes
    exchange.sendResponseHeaders(response.status, responseBytes.length)
    val os = exchange.getResponseBody
    os.write(responseBytes)
    os.close()
  }
}
