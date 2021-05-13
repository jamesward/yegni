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

object Flaky extends App:

  def flaky(random: Random.Service): ZIO[HttpContext, IOException, HttpResponse] =
    def succeedOrFail(succeed: Boolean) =
      if (succeed)
        ZIO.succeed(HttpResponse("hello, world"))
      else
        ZIO.fail(new IOException("erggg"))

    random.nextBoolean.flatMap(succeedOrFail)

  override def run(args: List[String]) =
    val server = for
      random <- ZIO.access[Random](_.get)
      handler = flaky(random)
      s <- HttpServer.serve(8080)("/" -> handler)
    yield s

    server.exitCode
