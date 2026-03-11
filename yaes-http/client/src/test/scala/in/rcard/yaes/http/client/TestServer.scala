package in.rcard.yaes.http.client

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import java.net.InetSocketAddress

object TestServer:
  def findFreePort(): Int =
    val socket = new java.net.ServerSocket(0)
    val port   = socket.getLocalPort
    socket.close()
    port

  def start(handler: HttpExchange => Unit): (HttpServer, String) =
    val port   = findFreePort()
    val server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext(
      "/",
      exchange => {
        handler(exchange)
      }
    )
    server.setExecutor(null)
    server.start()
    (server, s"http://localhost:$port")
