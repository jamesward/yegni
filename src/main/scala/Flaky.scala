import services._

import scala.{
  Any,
  Boolean,
  List,
  Nothing,
}
import scala.Predef.ArrowAssoc
import java.lang.String
import java.io.IOException
import zio.{
  App,
  ExitCode,
  ZEnv,
  ZIO,
}
import zio.random.Random
import zio.system.env
import scala.Predef.{
  ArrowAssoc,
  augmentString,
}

object Flaky extends App:

  def flaky(random: Random.Service): ZIO[TelemetryContext, IOException, HttpResponse] =
    def succeedOrFail(succeed: Boolean) =
      if (succeed)
        ZIO.succeed(HttpResponse("hello, flaky"))
      else
        ZIO.fail(new IOException("erggg"))

    random.nextBoolean.flatMap(succeedOrFail)

  override def run(args: List[String]) =
    val server = for
      port <- env("PORT")
      random <- ZIO.access[Random](_.get)
      handler = flaky(random)
      s <- HttpServer.serve(port.map(_.toInt).getOrElse(8081))("/flaky" -> handler)
    yield s

    server.exitCode
