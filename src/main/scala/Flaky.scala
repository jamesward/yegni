import zio.{ZIO, UIO, RIO}
import zio.system.env
import zio.system.System
import zio.ZManaged
import zio.blocking
import zio.blocking.Blocking

import scala.util.Try
import scala.util.Using
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import java.net.InetSocketAddress

object Flaky extends zio.App:
  sealed trait PortError
  case class InvalidPortValue(port: String) extends PortError
  case class SecurityError(e: SecurityException) extends PortError

  // Tries to read the `PORT` environment variable and convert it to a valid port value
  // Defaults to 8080, only if there was no `PORT` env var
  // A Char (uint16) is used to store the port value
  // See: https://twitter.com/jroper/status/1217525231868239872
  //
  // todo: possibly read the default from the environment
  //

  opaque type Port = Char

  val zioPort: ZIO[System, PortError, Port] =
    def convertToCharOrFail(s: String): Either[InvalidPortValue, Port] =
      val charTry = for
        i <- Try(s.toInt)
        if i >= Char.MinValue
        if i <= Char.MaxValue
      yield i.toChar

      charTry.toEither.left.map(_ => InvalidPortValue(s))

    def portToZio(s: Option[String]): ZIO[System, PortError, Port] =
      val toEither = s.fold[Either[InvalidPortValue, Port]](Right(8080.toChar))(convertToCharOrFail)
      ZIO.fromEither(toEither)

    env("PORT").mapError(SecurityError.apply).flatMap(portToZio)

  // todo: ZManaged ?
  def server(port: Port, handler: (String, HttpHandler)): RIO[Blocking, Unit] =
    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.createContext(handler._1, handler._2)
    blocking.effectBlockingCancelable(server.start())(UIO.effectTotal(server.stop(5))) // waits up to 5 seconds for connections to close

  val handler: HttpHandler = exchange =>
    val response = "hello, world".getBytes
    exchange.sendResponseHeaders(200, response.length)
    Using(exchange.getResponseBody)(_.write(response))

  val appLogic = for
    port <- zioPort
    _ <- server(port, ("/", handler)).forever
  yield ()

  override def run(args:  List[String]): ZIO[zio.ZEnv, Nothing, zio.ExitCode] =
    appLogic.exitCode
