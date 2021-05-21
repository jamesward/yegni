import services.*
import scala.{
  Any,
  Boolean,
  List,
  Nothing,
}
import scala.Predef.ArrowAssoc
import scala.Predef.{
  ArrowAssoc,
  augmentString,
}
import java.lang.String
import java.io.IOException
import zio.{
  App,
  ExitCode,
  ZEnv,
  ZIO,
}
import zio.random.nextBoolean
import zio.system.env

object Flaky extends App:

  val handler: HttpHandler =
    for
      succeed <- nextBoolean
      result <-
        if (succeed)
          ZIO.succeed(HttpResponse("hello, flaky"))
        else
          ZIO.fail(new IOException("erggg"))
    yield result

  override def run(args: List[String]) =
    val server = for
      maybePort <- env("PORT")
      port = maybePort.map(_.toInt).getOrElse(8081)
      homeRoute = "/" -> handler
      flakyRoute = "/flaky" -> handler
      s <- HttpServer.serve(port)(homeRoute, flakyRoute)
    yield s

    server.exitCode
