package in.rcard.yaes

import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

/** Example HTTP server demonstrating YAES server capabilities.
  *
  * Run this example with: `sbt "yaes-http-server/Test/runMain in.rcard.yaes.ExampleServer"`
  */
object ExampleServer extends App {

  val server = YaesServer.route(
    // Simple GET endpoint
    (Method.GET, "/hello", (req: Request) => Response.ok("Hello, World!")),

    // POST endpoint echoing the request body
    (Method.POST, "/echo", (req: Request) => {
      Response.ok(s"You sent: ${req.body}")
    }),

    // Endpoint using request headers
    (Method.GET, "/greet", (req: Request) => {
      val name = req.headers.getOrElse("X-Name", "Guest")
      Response.ok(s"Hello, $name!")
    }),

    // JSON-like endpoint
    (Method.GET, "/api/status", (req: Request) => {
      Response(
        status = 200,
        headers = Map("Content-Type" -> "application/json"),
        body = """{"status": "ok", "server": "YaesServer"}"""
      )
    }),

    // Error endpoint for testing error handling
    (Method.GET, "/error", (req: Request) => {
      throw new RuntimeException("Intentional server error")
    })
  )

  println("Starting YAES HTTP Server on port 8080...")
  println("Available endpoints:")
  println("  GET  http://localhost:8080/hello")
  println("  POST http://localhost:8080/echo")
  println("  GET  http://localhost:8080/greet (set X-Name header)")
  println("  GET  http://localhost:8080/api/status")
  println("  GET  http://localhost:8080/error (returns 500)")
  println("\nPress Ctrl+C to stop the server")

  Async.run {
    IO.run {
      YaesServer.run(server, port = 8080)
    }
  }
}
