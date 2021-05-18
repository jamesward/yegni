import services._
import zio.{
  App,
  ExitCode,
  Schedule,
  ZEnv,
  ZIO,
  ZLayer,
}
import zio.clock.Clock
import zio.system.env
import java.io.IOException
import java.lang.String
import scala.{
  &,
  List,
}
import scala.Predef.{
  ArrowAssoc,
  augmentString,
}

object ZioWebApp extends App:
  override def run(args: List[String]) =
    def upper(resp: HttpResponse): HttpResponse =
      resp.copy(body = resp.body.toUpperCase)

    def flaky(url: String): ZIO[HttpClient & Clock, IOException, HttpResponse] =
      HttpClient.send(url).retry(Schedule.recurs(5))

    def slow(url: String): ZIO[HttpClient, IOException, HttpResponse] =
      HttpClient.send(url)

    def flakyOrSlow(flakyZ: ZIO[HttpClient & Clock, IOException, HttpResponse],
                    slowZ: ZIO[HttpClient, IOException, HttpResponse]) =
      flakyZ.disconnect.race(slowZ.disconnect).provideCustomLayer(HttpClient.live)

    java.lang.System.err.println("Starting server!")
    val server = for
      port <- env("PORT")
      maybeFlakyUrl <- env("FLAKY_URL")
      flakyUrl = maybeFlakyUrl.getOrElse("http://localhost:8081/flaky")
      maybeSlowUrl <- env("SLOW_URL")
      slowUrl = maybeSlowUrl.getOrElse("http://localhost:8082/slow")
      route = "/" -> flakyOrSlow(flaky(flakyUrl), slow(slowUrl))
      s    <- HttpServer.serve(port.map(_.toInt).getOrElse(8080))(route)
    yield s

    server.exitCode