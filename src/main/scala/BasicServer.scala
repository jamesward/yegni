import java.lang.String
import scala.{List, sys, main}
import scala.Predef.{augmentString, println}

import java.net.InetSocketAddress

import com.sun.net.httpserver.{HttpHandler, HttpServer}

import scala.util.Using

@main def BasicServer =

  val port = sys.env.getOrElse("PORT", "8080").toInt

  val server = HttpServer.create(new InetSocketAddress(port), 0)

  val handler: HttpHandler = exchange => {
    val response = "hello, world".getBytes
    exchange.sendResponseHeaders(200, response.length)
    Using(exchange.getResponseBody)(_.write(response))
  }

  server.createContext("/", handler)

  println(s"Listening at http://localhost:$port")

  server.start()
