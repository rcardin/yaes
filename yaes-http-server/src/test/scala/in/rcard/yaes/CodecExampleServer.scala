package in.rcard.yaes

/** Example HTTP server demonstrating custom codec usage.
  *
  * This example shows how to define a custom BodyCodec for a domain model (User) and use it in HTTP
  * handlers. In production, you would typically use Circe for JSON codec derivation instead of manual
  * JSON parsing.
  *
  * Run this example with: `sbt "yaes-http-server/Test/runMain in.rcard.yaes.CodecExampleServer"`
  */
object CodecExampleServer extends YaesApp {

  // Example domain model
  case class User(name: String, age: Int)

  // Custom codec for User (manual JSON for now)
  given BodyCodec[User] with {
    def contentType: String = "application/json"

    def encode(user: User): String =
      s"""{"name":"${user.name}","age":${user.age}}"""

    def decode(body: String): User raises DecodingError = {
      // Very simple JSON parsing (in real code, use Circe)
      val namePattern = """"name":"([^"]+)"""".r
      val agePattern  = """"age":(\d+)""".r

      (namePattern.findFirstMatchIn(body), agePattern.findFirstMatchIn(body)) match {
        case (Some(n), Some(a)) =>
          User(n.group(1), a.group(1).toInt)
        case _ =>
          Raise.raise(DecodingError.ParseError("Invalid User JSON"))
      }
    }
  }

  val server = YaesServer.route(
    // GET endpoint returning a User
    GET(p"/users/1") { req =>
      Response.ok(User("Alice", 30))
    },

    // POST endpoint accepting a User in the body
    POST(p"/users") { req =>
      // Using Raise.either to handle decoding errors
      val result = Raise.either {
        val user = req.as[User]
        println(s"Received user: $user")
        Response.created(user)
      }
      result match {
        case Right(response) => response
        case Left(error) => Response.badRequest(s"Invalid user: ${error.message}")
      }
    },

    // GET endpoint returning a list as JSON (manual encoding)
    GET(p"/users") { req =>
      val users = List(User("Alice", 30), User("Bob", 25))
      val json = users.map(u => s"""{"name":"${u.name}","age":${u.age}}""").mkString("[", ",", "]")
      Response(
        status = 200,
        headers = Map("Content-Type" -> "application/json"),
        body = json
      )
    }
  )

  def run: (Output, Input, Random, Clock, System, Log) ?=> Unit = {
    Output.printLn("Starting YAES HTTP Server with Custom Codecs on port 8080...")
    Output.printLn("Available endpoints:")
    Output.printLn("  GET  http://localhost:8080/users/1")
    Output.printLn("       Returns: {\"name\":\"Alice\",\"age\":30}")
    Output.printLn("  GET  http://localhost:8080/users")
    Output.printLn("       Returns: [{\"name\":\"Alice\",\"age\":30},{\"name\":\"Bob\",\"age\":25}]")
    Output.printLn("  POST http://localhost:8080/users")
    Output.printLn("       Accepts: {\"name\":\"...\",\"age\":...}")
    Output.printLn("       Example: curl -X POST -d '{\"name\":\"Charlie\",\"age\":35}' http://localhost:8080/users")
    Output.printLn("\nPress Ctrl+C to stop the server")

    Async.run {
      IO.run {
        YaesServer.run(server, port = 8080)
      }
    }
  }
}
