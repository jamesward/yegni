import com.sun.net.httpserver.{HttpHandler, HttpServer}

import java.lang.String
import java.net.InetSocketAddress
import scala.Predef.{augmentString, println}
import scala.util.Using
import scala.{List, main, sys}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.lang.ProcessBuilder.Redirect
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.time.Duration
import java.net.URI
import services.HttpServerInstrumentation

@main def BasicWebApp =

  val port = sys.env.getOrElse("PORT", "8080").toInt

  val server = HttpServer.create(new InetSocketAddress(port), 0)

  val client = HttpClient.newBuilder.build

  val handler: HttpHandler = exchange =>
    val request = 
      val builder = HttpRequest.newBuilder(URI("http://localhost:8081/api"))
      HttpServerInstrumentation.injectContext(builder)
      builder.build
    val response = client.send(request, HttpResponse.BodyHandlers.ofString)
    if (response.statusCode == 200)
      exchange.sendResponseHeaders(200, response.body.length)
      Using(exchange.getResponseBody)(_.write(response.body.toUpperCase.getBytes))
    else
      exchange.sendResponseHeaders(500, 0)
      exchange.close()

  server.createContext("/", handler)
  server.setExecutor(null)

  println(s"Listening at http://localhost:$port")

  server.start()
