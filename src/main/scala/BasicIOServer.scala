import scala.List
import scala.concurrent.ExecutionContext

import java.lang.String

import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.{HttpRoutes, Request, Response, StaticFile, Uri}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder

object BasicIOServer extends IOApp:

  def run(args: List[String]) =

    val routes = HttpRoutes.of[IO] {
      case GET -> Root => Ok("hello, world")
    }.orNotFound

    val server = BlazeServerBuilder[IO](ExecutionContext.global).bindHttp(8080, "0.0.0.0").withHttpApp(routes).resource

    server.use(_ => IO.never).as(ExitCode.Success)