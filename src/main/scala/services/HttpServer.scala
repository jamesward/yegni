package services

import java.io.IOException
import scala.{
    Any,
    Int,
    PartialFunction,
    Unit
}
import zio.{
  Layer,
  Has,
  ZLayer,
  ZIO
}

trait HttpRequest
trait HttpResponse

type HttpRoute = PartialFunction[HttpRequest, ZIO[Any, IOException, HttpResponse]]


object HttpServer:
  def server(port: Int)(routes: HttpRoute): ZIO[Any, IOException, Unit] = null



  private def wrapRoute(route: HttpRoute): PartialFunction[HttpRequest, ZIO[Telemetry, IOException, HttpResponse]] = {
      case request =>
        // TODO - build route with appropriate semantic conventions for rendering in cloud trace.
        Telemetry.span("route").bracket(Telemetry.endSpan) { _ =>
          route(request)
        }
  }