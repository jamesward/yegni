package services

import java.lang.{
  String,
  Throwable,
}
import java.io.IOException
import scala.{
    &,
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
  ZEnv,
}
import zio.blocking.{
    Blocking,
    effectBlockingCancelable
}
import zio.internal.Platform


trait HttpRequest
case class HttpResponse(body: String)
type HttpHandler = ZIO[TelemetryContext & ZEnv, IOException, HttpResponse]
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

      // Wrap the incoming handler in a version that does Telemetry propagation
      // and obserrvability.
      val effect: HttpHandler =
        for
          _ <- TelemetryContext.extract(exchange)
          result <- TelemetryContext.httpSpan[ZEnv, IOException, HttpResponse](exchange)(handler)
        yield result
      val context = TelemetryContext.liveOtel
      val a = Runtime.default.unsafeRunSync(effect.provideSomeLayer(context))

      def fail(cause: Cause[IOException]): Unit =
        val body = cause.prettyPrint.getBytes
        exchange.sendResponseHeaders(500, body.length)
        Using(exchange.getResponseBody)(_.write(body))

      def success(response: HttpResponse): Unit =
        val body = response.body.getBytes
        exchange.sendResponseHeaders(200, body.length)
        Using(exchange.getResponseBody)(_.write(body))

      a.fold(fail, success)


type HttpClient = Has[HttpClient.Service]
object HttpClient:
  import java.net.http.{
    HttpClient => JvmHttpClient,
    HttpRequest => JvmHttpRequest,
    HttpResponse => JvmHttpResponse,
  }
  import java.net.URI
  import zio.ZEnv
  import zio.blocking.{
    Blocking,
    effectBlocking,
  }
  import zio.blocking.effectBlocking

  trait Service:
    def send(url: JvmHttpRequest): ZIO[Blocking, IOException, HttpResponse]


  private def requestBuilder(url: String) =
    ZIO.effect(JvmHttpRequest.newBuilder(URI(url))) refineOrDie {
      case t: Throwable =>
        new IOException(t)
    }
  def send(url: String): ZIO[HttpClient & TelemetryContext & Blocking, IOException, HttpResponse] =
    for
      request <- requestBuilder(url)
      _ <- TelemetryContext.inject(request)
      service <- ZIO.accessM[HttpClient](x => ZIO.succeed(x.get))
      result <- service.send(request.build)
    yield result

  case class HttpClientLive() extends HttpClient.Service:
    def send(request: JvmHttpRequest): ZIO[Blocking, IOException, HttpResponse] =
      // todo: URI creation can fail
      effectBlocking {
        val client = JvmHttpClient.newBuilder.build
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
