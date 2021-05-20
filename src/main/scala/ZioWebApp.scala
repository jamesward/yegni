import services.*
import zio.{
  App,
  ExitCode,
  Schedule,
  ZEnv,
  ZIO,
  ZLayer,
}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.putStrLn
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

    def flaky(url: String): HttpHandler =
      HttpClient.send(url).retry(Schedule.recurs(5)).provideSomeLayer(HttpClient.live)
      
    def slow(url: String): HttpHandler =
      HttpClient.send(url).provideSomeLayer(HttpClient.live)


    def flakyOrSlow(flakyZ: HttpHandler, slowZ: HttpHandler): HttpHandler =
      flakyZ.disconnect.race(slowZ.disconnect).map(upper)

    val server = for
      maybePort <- env("PORT")
      port = maybePort.map(_.toInt).getOrElse(8080)

      maybeFlakyUrl <- env("FLAKY_URL")
      flakyUrl = maybeFlakyUrl.getOrElse("http://localhost:8081/flaky")

      maybeSlowUrl <- env("SLOW_URL")
      slowUrl = maybeSlowUrl.getOrElse("http://localhost:8082/slow")

      route = "/" -> flakyOrSlow(flaky(flakyUrl), slow(slowUrl))
      route2 = "/retry" -> flaky(flakyUrl)
      route3 = "/hedge" -> slow(slowUrl).hedge()

      _ <- putStrLn(s"Starting server: http://localhost:$port")
      s <- HttpServer.serve(port)(route, route2, route3)
    yield s

    server.exitCode


