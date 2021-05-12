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
    val handler = ZIO.succeed(new HttpResponse { val body = "hello, world" })
    HttpServer.serve(8080)(("/",  handler)).exitCode
    /*
    val s = for
      client <- Asdf
      handler <- Asdf
      server <- Asdf(handler)
    yield server

    s.provideLayer(ot).exitCode
    //HttpServer.serve(8080)(("/",  null)).exitCode
     */
