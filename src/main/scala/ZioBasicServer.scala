import services._

import scala.{
  Any,
  Nothing,
}
import scala.collection.immutable.List
import java.lang.String
import zio.{
  App,
  ExitCode,
  ZEnv,
  ZIO,
}

import java.io.IOException

object ZioBasicServer extends App:
  override def run(args: List[String]) =
    val handler: ZIO[Any, Nothing, HttpResponse] =
      ZIO.succeed(HttpResponse("hello, world"))

    HttpServer.serve(8080)(("/",  handler)).exitCode
