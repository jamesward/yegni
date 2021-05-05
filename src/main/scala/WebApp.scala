import zio.{App, ExitCode, Task, ZEnv, ZIO, RIO}
import zio.system.System
import zio.interop.catz.*

import scala.{List, Nothing, PartialFunction, Unit, Any}
import scala.Predef.???
import scala.Console.println

import java.lang.{String, Throwable}

import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.implicits.*
import org.http4s.dsl.Http4sDsl
import org.http4s.client.Client
import org.http4s.server.Router

object WebApp extends zio.App:

  val dsl = Http4sDsl[Task]
  import dsl.*

  def index(client: Client[Task]): PartialFunction[Request[Task], Task[Response[Task]]] =
    case GET -> Root =>
      client.expect[String]("http://localhost:8081/").flatMap(resp => Ok(resp.toUpperCase))

  def routes(client: Client[Task]) = HttpRoutes.of[Task](index(client)).orNotFound

  val appLogic: ZIO[Http4Client.Http4Client with ZEnv, Throwable, Unit] = for
    client <- ZIO.access[Http4Client.Http4Client](_.get)
    // port <- Port.get // todo
    _ <- Http4Server.createHttp4Server(routes(client), Port(8080))

  yield ()

  override def run(args:  List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    appLogic.provideCustomLayer(Http4Client.createHttp4ClientZLayer).exitCode
