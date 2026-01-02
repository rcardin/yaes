package in.rcard.yaes.http.server

import in.rcard.yaes.*
import in.rcard.yaes.Channel
import com.sun.net.httpserver.{HttpServer => JdkHttpServer, HttpExchange}
import java.net.InetSocketAddress
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
case class ServerDef(routes: Routes, shutdownHook: Option[() => Unit] = None) {

  /** Register a callback to run when the server shuts down.
    *
    * The hook runs during Resource cleanup, before the server stops.
    *
    * Example:
    * {{{
    * val server = YaesServer.route(...)
    *   .onShutdown(() => {
    *     println("Cleaning up...")
    *     // Close database connections, etc.
    *   })
    *   .run(8080)
    * }}}
    *
    * @param hook
    *   The cleanup function to run on shutdown
    * @return
    *   A new ServerDef with the shutdown hook registered
    */
  def onShutdown(hook: () => Unit): ServerDef = {
    copy(shutdownHook = Some(hook))
  }

  /** Run the HTTP server.
    *
    * Starts the server on the specified port and returns a Server handle. The server runs until
    * shutdown() is called on the returned Server.
    *
    * This is a convenience method equivalent to YaesServer.run(this, port).
    *
    * @param port
    *   Port to bind to
    * @param async
    *   Async context for forking request fibers and shutdown coordination
    * @param io
    *   IO context for server lifecycle
    * @return
    *   A Server instance that can be used to stop the server
    */
  def run(port: Int)(using Async, IO): Server = {
    YaesServer.run(this, port)
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
  * Async.run {
  *   IO.run {
  *     YaesServer.run(server, port = 8080)
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
    * automatically have access to YAES effect contexts (IO, Async, etc.) when the server is run.
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
    *   - Requires [[IO]] context for virtual thread execution
    *   - Requires [[Async]] context for fiber-per-request handling and shutdown coordination
    *
    * **Request Handling:**
    *   - Each request is automatically handled in a forked fiber via [[Async.fork]]
    *   - Handlers receive the full YAES effect context
    *   - Errors in handlers result in 500 Internal Server Error responses
    *   - Unmatched routes result in 404 Not Found responses
    *   - HttpExchange resources are automatically closed via [[Resource]] effect (internal)
    *
    * **Shutdown:**
    *   - Call [[Server.shutdown]] on the returned handle to stop the server
    *   - Shutdown hook (if registered) runs before the server stops
    *   - Server lifecycle is managed via [[Resource]] effect for automatic cleanup
    *
    * Example:
    * {{{
    * val server = YaesServer.route(
    *   (Method.GET, "/hello", (req: Request) => Response.ok("Hello!"))
    * )
    *
    * Async.run {
    *   IO.run {
    *     println("Starting server on port 8080...")
    *     val serverHandle = YaesServer.run(server, port = 8080)
    *
    *     // ... do work ...
    *
    *     serverHandle.shutdown()
    *     println("Server stopped")
    *   }
    * }
    * }}}
    *
    * @param serverDef
    *   Server configuration with routes
    * @param port
    *   Port to bind to
    * @param async
    *   Async context for forking request fibers and shutdown coordination
    * @param io
    *   IO context for server lifecycle
    * @return
    *   A Server instance that can be used to stop the server
    */
  def run(serverDef: ServerDef, port: Int)(using async: Async, io: IO): Server = {
    // Internal rendezvous channel for shutdown signaling
    val shutdownChannel = Channel.rendezvous[Unit]()

    Resource.run {
      // Install server as a resource with automatic cleanup
      val jdkServer = Resource.install(
        acquire = {
          val srv = JdkHttpServer.create(new InetSocketAddress(port), 0)

          srv.createContext(
            "/",
            (exchange: HttpExchange) => {
              // Fork a fiber for this request
              Async.fork(s"http-request-${exchange.getRequestURI}") {
                Resource.run {
                  Resource.ensuring {
                    exchange.close()
                  }
                  handleRequest(exchange, serverDef.routes)
                }
              }
            }
          )

          srv.setExecutor(null) // Use default executor
          srv.start()
          srv // Return the started server
        }
      )(
        release = server => {
          // Run user shutdown hook if registered
          serverDef.shutdownHook.foreach(hook => hook())
          // Stop the JDK server immediately (0 = no grace period)
          server.stop(0)
        }
      )

      // Fork a fiber to wait for shutdown signal
      Async.fork("http-server-shutdown-waiter") {
        Raise.run {
          shutdownChannel.receive()
          // Close channel after receiving shutdown signal
          // This ensures subsequent shutdown() calls fail gracefully
          shutdownChannel.close()
        }
      }
      // No join needed - Async structured concurrency ensures Resource.run
      // waits for all forked fibers to complete before exiting
    }
    // Resource cleanup happens here after shutdown-waiter fiber completes

    // Return server handle
    Server(shutdownChannel)
  }

  private def handleRequest(
      exchange: HttpExchange,
      routes: Routes
  )(using async: Async, io: IO): Unit = {
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
    val body = new String(exchange.getRequestBody.readAllBytes())
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
            val decodedKey = java.net.URLDecoder.decode(key, "UTF-8")
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
