import zio.ZIO
import zio.system.{System, env}
import scala.{Int, Char, Option}
import java.lang.{String, SecurityException}
import scala.util.{Either, Try, Right}
import scala.Predef.augmentString

opaque type Port = Char

// Tries to read the `PORT` environment variable and convert it to a valid port value
// Defaults to 8080, only if there was no `PORT` env var
// A Char (uint16) is used to store the port value
// See: https://twitter.com/jroper/status/1217525231868239872
//
// todo: possibly read the default from the environment
//
object Port:
  sealed trait PortError
  case class InvalidPortValue(port: String) extends PortError
  case class SecurityError(e: SecurityException) extends PortError

  val get: ZIO[System, PortError, Port] =
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

  def apply(port: Char): Port = port

  extension (port: Port)
    def toInt: Int = port.toInt
