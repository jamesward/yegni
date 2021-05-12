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

      def success(response: HttpResponse): Unit =
        val body = response.body.getBytes
        exchange.sendResponseHeaders(200, body.length)
        Using(exchange.getResponseBody)(_.write(body))

      a.fold(fail, success)
