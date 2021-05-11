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
}
import zio.blocking.{
    Blocking,
    effectBlockingCancelable
}


trait HttpRequest
trait HttpResponse
type HttpContext = Has[HttpContext.Service]
object HttpContext:
  trait Service:
    def request: HttpRequest
  def request: ZIO[HttpContext, Nothing, HttpRequest] =
    ZIO.accessM[HttpContext](ctx => ZIO.succeed(ctx.get.request))


type HttpHandler = ZIO[HttpContext, IOException, HttpResponse]
type HttpRoute = (String, HttpHandler)




object HttpServer:
  import com.sun.net.httpserver.{HttpHandler=>JvmHttpHandler, HttpServer=>JvmHttpServer}
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
      val response = "HELLO!".getBytes
      exchange.sendResponseHeaders(200, response.length)
      Using(exchange.getResponseBody)(_.write(response))