package in.rcard.yaes

import com.sun.net.httpserver.{HttpServer => JdkHttpServer, HttpExchange}
import java.net.InetSocketAddress
import scala.jdk.CollectionConverters.*

/** Server configuration and route definitions.
  *
  * Represents a pure description of an HTTP server with its routes. The server is not started until
  * [[YaesServer.run]] is called.
  *
  * @param router
  *   The router mapping requests to handlers
  */
case class ServerDef(router: Router)

/** HTTP server built on YAES effects and JDK HttpServer.
  *
  * Provides a simple, effect-based HTTP server using virtual threads for request handling. Each
  * incoming request is automatically handled in its own fiber (virtual thread) via [[Async.fork]].
  *
  * Example:
  * {{{
  * import in.rcard.yaes.*
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
    * Creates a pure server definition from route specifications. Each route maps an HTTP method and
    * path to a handler function.
    *
    * Handlers receive a [[Request]] and return a [[Response]]. They automatically have access to
    * YAES effect contexts (IO, Async, etc.) when the server is run.
    *
    * Example:
    * {{{
    * val server = YaesServer.route(
    *   (Method.GET, "/hello", (req: Request) => {
    *     Response.ok("Hello!")
    *   }),
    *   (Method.POST, "/echo", (req: Request) => {
    *     Response.ok(req.body)
    *   })
    * )
    * }}}
    *
    * @param routes
    *   Variable argument list of (Method, path, handler) triples
    * @return
    *   A ServerDef representing the server configuration
    */
  def route(routes: (Method, String, Request => Response)*): ServerDef = {
    ServerDef(Router(routes*))
  }

  /** Run the HTTP server.
    *
    * Starts an HTTP server on the specified port, handling each incoming request in its own fiber.
    * The server runs indefinitely until the program is terminated.
    *
    * **Effect Requirements:**
    *   - Requires [[IO]] context for virtual thread execution
    *   - Requires [[Async]] context for fiber-per-request handling
    *
    * **Request Handling:**
    *   - Each request is automatically handled in a forked fiber via [[Async.fork]]
    *   - Handlers receive the full YAES effect context
    *   - Errors in handlers result in 500 Internal Server Error responses
    *   - Unmatched routes result in 404 Not Found responses
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
    *     YaesServer.run(server, port = 8080)
    *   }
    * }
    * }}}
    *
    * @param serverDef
    *   Server configuration with routes
    * @param port
    *   Port to bind to
    * @param async
    *   Async context for forking request fibers
    * @param io
    *   IO context for server lifecycle
    */
  def run(serverDef: ServerDef, port: Int)(using async: Async, io: IO): Unit = {
    val server = JdkHttpServer.create(new InetSocketAddress(port), 0)

    server.createContext(
      "/",
      (exchange: HttpExchange) => {
        // Fork a fiber for this request
        val fiber = Async.fork(s"http-request-${exchange.getRequestURI}") {
          handleRequest(exchange, serverDef.router)
        }

        // Wait for the fiber to complete before responding
        fiber.join()
      }
    )

    server.setExecutor(null) // Use default executor
    server.start()

    // Block until interrupted (for now - later add graceful shutdown)
    Thread.currentThread().join()
  }

  private def handleRequest(
      exchange: HttpExchange,
      router: Router
  )(using async: Async, io: IO): Unit = {
    try {
      // Parse request
      val request = parseRequest(exchange)

      // Route to handler
      val response = router.handle(request)

      // Write response
      writeResponse(exchange, response)
    } catch {
      case ex: Exception =>
        val errorResponse = Response.internalServerError(ex.getMessage)
        writeResponse(exchange, errorResponse)
    } finally {
      exchange.close()
    }
  }

  private def parseRequest(exchange: HttpExchange): Request = {
    val method = Method.valueOf(exchange.getRequestMethod)
    val path   = exchange.getRequestURI.getPath
    val headers = exchange.getRequestHeaders.asScala.map { case (k, v) =>
      k -> v.asScala.headOption.getOrElse("")
    }.toMap
    val body = new String(exchange.getRequestBody.readAllBytes())

    Request(method, path, headers, body)
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
