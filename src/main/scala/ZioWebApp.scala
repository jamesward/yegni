import services._
import zio.{
  App,
  ExitCode,
  ZEnv,
  ZIO,
  ZLayer,
}
import zio.system.env
import zio.clock.Clock
import java.io.IOException
import java.lang.String
import scala.List
import scala.Predef.{
  ArrowAssoc,
  augmentString,
}

object ZioWebApp extends App:
  override def run(args: List[String]) =
    def upper(resp: HttpResponse): HttpResponse =
      resp.copy(body = resp.body.toUpperCase)

    def handler(url: String): HttpHandler =
      HttpClient.send(url).provideSomeLayer(HttpClient.live)
      //HttpClient.send(url).retry(Schedule.recurs(5)).provideSomeLayer(HttpClient.live ++ Clock.live)

    java.lang.System.err.println("Starting server!")
    val server = for
      port <- env("PORT")
      url  <- env("CLIENT_URL")
      route = "/" -> handler(url.getOrElse("http://localhost:8080/api"))
      s    <- HttpServer.serve(port.map(_.toInt).getOrElse(8081))(route)
    yield s

    server.exitCode