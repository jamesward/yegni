package services

import java.lang.String
import java.io.IOException
import scala.{
    Any,
    Int,
    PartialFunction,
    Unit,
    Nothing,
}
import scala.util.Using
import zio.{
  Layer,
  Has,
  ZLayer,
  ZManaged,
  ZIO,
  RIO,
  Runtime,
  Managed,
  Cause,
}
import zio.blocking.{
    Blocking,
    effectBlockingCancelable
}
import zio.internal.Platform


trait HttpRequest
trait HttpResponse:
  val body: String
type HttpContext = Has[HttpContext.Service]
object HttpContext:
  trait Service:
    def request: HttpRequest
  def request: ZIO[HttpContext, Nothing, HttpRequest] =
    ZIO.accessM[HttpContext](ctx => ZIO.succeed(ctx.get.request))


type HttpHandler = ZIO[HttpContext, IOException, HttpResponse]
type HttpRoute = (String, HttpHandler)


  

object HttpServer:
  import com.sun.net.httpserver.{
    HttpHandler => JvmHttpHandler,
    HttpServer => JvmHttpServer,
  }
  import java.net.InetSocketAddress
  /**
   * Starts an Http server with the given routes.
   */
  def serve(port: Int)(routes: HttpRoute*): RIO[Blocking, Unit] =
    val server = JvmHttpServer.create(new InetSocketAddress(port), 0)
    // Register all routes
    for (path, handler) <- routes
    do server.createContext(path, adaptHandler(handler))

    // Set up a lock so we block our current thread (since JVM forks a separate thread for server)
    object lock
    server.start()
    def stop(): Unit =
      try server.stop(0)
      finally lock.notifyAll()
    def block(): Unit =
      lock.synchronized {
          lock.wait()
      }
    effectBlockingCancelable(block())(ZIO.effectTotal(stop()))


  // TODO - implement
  private def adaptHandler(handler: HttpHandler): JvmHttpHandler =
    exchange =>
      // Grab Distirbuted trace
      import io.opentelemetry.api.trace.StatusCode
      HttpServerInstrumentation.extractContext(exchange)
      // Start span for HTTP
      val span = HttpServerInstrumentation.startHttpServerSpan(exchange)
      // Start a timer

      val context = ZLayer.succeed {
        new HttpContext.Service {
          override def request: HttpRequest = new HttpRequest {}
        }
      }

      val a = Runtime.default.unsafeRunSync(handler.provideLayer(context))

      def fail(cause: Cause[IOException]): Unit =
        val body = cause.prettyPrint.getBytes
        exchange.sendResponseHeaders(500, body.length)
        Using(exchange.getResponseBody)(_.write(body))
        // Stop Timer, record value
        // End span with failure
        span.setStatus(StatusCode.ERROR, cause.prettyPrint)
        span.end()

      def success(response: HttpResponse): Unit =
        val body = response.body.getBytes
        exchange.sendResponseHeaders(200, body.length)
        Using(exchange.getResponseBody)(_.write(body))
        // Stop TImer, record value
        // End span
        span.end()

      a.fold(fail, success)

/*
val client = HttpClient.newBuilder.build

  val handler: HttpHandler = exchange =>
    val request = HttpRequest.newBuilder(URI("http://localhost:8081")).build
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
 */

//object HttpClient:
//  val client: ZIO[HttpContext, IOException, HttpResponse] =
//    ZManaged.makeEffect(HttpClient.newBuilder.build)()