import services._
import zio.{App, ExitCode, ZEnv, ZIO, ZLayer}

import java.io.IOException
import java.lang.{String, Throwable}
import scala.collection.immutable.List
import scala.{Any, Nothing}

object ZioWebApp extends App:
  override def run(args: List[String]) =
    val httpContext = ZLayer.succeed {
      new HttpContext.Service {
        override def request: HttpRequest = new HttpRequest {}
      }
    }

    val handler = HttpClient.send("http://localhost:8080").map { resp =>
      new HttpResponse { val body = resp.body.toUpperCase }
    }.provideSomeLayer(HttpClient.live).provideLayer(httpContext).mapError { e =>
      new IOException(e)
    }

    HttpServer.serve(8081)(("/",  handler)).exitCode
