import services.*
import zio.random.Random
import zio.{
  App,
  ExitCode,
  ZEnv,
  ZIO,
}
import zio.duration.*
import zio.system.env
import java.io.IOException
import java.lang.String
import scala.{
  Any,
  Boolean,
  List,
  Nothing,
}
import scala.Predef.{
  ArrowAssoc,
  augmentString,
}

object Slow extends App:

  override def run(args: List[String]) =
    val handler = ZIO.sleep(3.seconds).map(_ => HttpResponse("hello, slow"))

    val server = for
      port <- env("PORT")
      s    <- HttpServer.serve(port.map(_.toInt).getOrElse(8082))("/slow" -> handler)
    yield s

    server.exitCode
