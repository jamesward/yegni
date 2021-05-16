package services

import java.lang.{
  String,
  Throwable,
}
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
case class HttpResponse(body: String)
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
      scala.Console.println("stop")
      try server.stop(0)
      finally lock.notifyAll()
    def block(): Unit = ()
      lock.synchronized {
          lock.wait()
      }
    java.lang.System.err.println("Started the server!")
    effectBlockingCancelable(block())(ZIO.effectTotal(stop()))


  // TODO - implement
  private def adaptHandler(handler: HttpHandler): JvmHttpHandler =
    exchange =>
      java.lang.System.err.println("Starting http handler")
      // Grab Distirbuted trace
      import io.opentelemetry.api.trace.StatusCode
      try HttpServerInstrumentation.extractContext(exchange)
      catch
        case e: java.lang.Throwable =>
          java.lang.System.err.println("Failed to extract context!")
          e.printStackTrace()
      java.lang.System.err.println(s"Done extracting context")
      import io.opentelemetry.api.trace.Span
      java.lang.System.err.println("Found existing span: " + Span.current)

      // Start span for HTTP
      val span = HttpServerInstrumentation.startHttpServerSpan(exchange)
      // TODO: Start a timer      

      val context = ZLayer.succeed {
        new HttpContext.Service {
          override def request: HttpRequest = new HttpRequest {}
        }
      }
      java.lang.System.err.println(s"Running logic for request: $span")
      val a = Using.resource(span.makeCurrent)(_ =>
        HttpServerInstrumentation.instrumentZio(Runtime.default)
        //Runtime.default
        .unsafeRunSync(handler.provideLayer(context)))

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
        span.setStatus(StatusCode.OK)
        span.end()

      a.fold(fail, success)


type HttpClient = Has[HttpClient.Service]
object HttpClient:
  import java.net.http.{
    HttpClient => JvmHttpClient,
    HttpRequest => JvmHttpRequest,
    HttpResponse => JvmHttpResponse,
  }
  import java.net.URI

  trait Service:
    def send(url: String): ZIO[Any, IOException, HttpResponse]

  def send(url: String): ZIO[HttpClient, IOException, HttpResponse] =
    ZIO.accessM[HttpClient](_.get.send(url))

  case class HttpClientLive() extends HttpClient.Service:
    def send(url: String): ZIO[Any, IOException, HttpResponse] =
      // todo: URI creation can fail
      ZIO.effect {
        val client = JvmHttpClient.newBuilder.build
        val builder = JvmHttpRequest.newBuilder(URI(url))
        HttpServerInstrumentation.injectContext(builder)
        val request = builder.build
        java.lang.System.err.println(request.headers.toString)
        client.send(request, JvmHttpResponse.BodyHandlers.ofString)
      } refineOrDie {
        case t: Throwable =>
          new IOException(t)
      } flatMap { response =>
        if (response.statusCode == 200)
          ZIO.succeed(HttpResponse(response.body))
        else
          ZIO.fail(new IOException(s"Request failed with ${response.statusCode}"))
      }

  val live: ZLayer[Any, Nothing, HttpClient] =
    ZLayer.succeed(HttpClientLive())
