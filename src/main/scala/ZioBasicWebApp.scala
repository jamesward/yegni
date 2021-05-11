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
    ZIO,
    ZEnv,
}

object ZioBasicWebApp extends App:
  override def run(args: List[String]) =
    HttpServer.serve(8080)(("/",  null)).exitCode