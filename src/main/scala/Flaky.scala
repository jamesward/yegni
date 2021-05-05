import zio.{App, ExitCode, Task, ZEnv, ZIO}
import zio.random.Random
import zio.interop.catz.*

import scala.{List, Nothing, PartialFunction, Unit}

import java.lang.String

import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.implicits.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

object Flaky extends zio.App:

  val dsl = Http4sDsl[Task]
  import dsl.*

  def index(random: Random.Service): PartialFunction[Request[Task], Task[Response[Task]]] =
    case GET -> Root =>
      for
        b <- random.nextBoolean
        r <- if (b) Ok("hello, world") else InternalServerError()
      yield r

  def routes(random: Random.Service) = HttpRoutes.of[Task](index(random)).orNotFound

  val appLogic = for
    random <- ZIO.access[Random](_.get)
    _ <- Http4Server.createHttp4Server(routes(random), Port(8081))
  yield ()

  override def run(args:  List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    appLogic.exitCode
