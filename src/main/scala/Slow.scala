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
import zio.random.nextIntBetween

object Slow extends App:

  override def run(args: List[String]) =
    val handler = 
      for
        delay <- nextIntBetween(100, 5000)
        _ <- ZIO.sleep(delay.milliseconds)
      yield
        HttpResponse(s"hello, slow.  We took: $delay millisconds.  Hope you got a coffee.")

    val server = for
      port <- env("PORT")
      s    <- HttpServer.serve(port.map(_.toInt).getOrElse(8082))("/slow" -> handler, "/" -> handler)
    yield s

    server.exitCode
