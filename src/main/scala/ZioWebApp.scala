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

    def flaky(url: String): ZIO[HttpClient & Clock & Blocking & TelemetryContext, IOException, HttpResponse] =
      HttpClient.send(url).retry(Schedule.recurs(5))

    def slow(url: String): ZIO[HttpClient & Blocking & TelemetryContext, IOException, HttpResponse] =
      HttpClient.send(url)

    def flakyOrSlow(flakyZ: ZIO[HttpClient & Clock & Blocking & TelemetryContext, IOException, HttpResponse],
                    slowZ: ZIO[HttpClient & Blocking & TelemetryContext, IOException, HttpResponse]): HttpHandler =
      flakyZ.disconnect.race(slowZ.disconnect)
        .provideSomeLayer(HttpClient.live)

    val server = for
      maybePort <- env("PORT")
      port = maybePort.map(_.toInt).getOrElse(8080)

      maybeFlakyUrl <- env("FLAKY_URL")
      flakyUrl = maybeFlakyUrl.getOrElse("http://localhost:8081/flaky")

      maybeSlowUrl <- env("SLOW_URL")
      slowUrl = maybeSlowUrl.getOrElse("http://localhost:8082/slow")

      route = "/" -> flakyOrSlow(flaky(flakyUrl), slow(slowUrl))

      _ <- putStrLn(s"Starting server: http://localhost:$port")
      s <- HttpServer.serve(port)(route)
    yield s

    server.exitCode