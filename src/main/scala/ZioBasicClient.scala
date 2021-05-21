import services.*
import zio.{
  App,
  ExitCode,
  ZEnv,
  ZIO,
}
import zio.blocking.Blocking
import zio.console.Console
import zio.console.putStrLn

import java.lang.String
import java.io.IOException
import scala.collection.immutable.List
import scala.{
  Any,
  Nothing,
  Unit,
  &,
}

object ZioBasicClient extends App:
  override def run(args: List[String]) =
    val u: ZIO[HttpClient & TelemetryContext & Blocking & Console, IOException, Unit] =
      for
        resp   <- HttpClient.send("https://jamesward.com")
        _      <- putStrLn(resp.body)
      yield ()
    val peelOne: ZIO[TelemetryContext & Blocking & Console, IOException, Unit] = u.provideSomeLayer(HttpClient.live)
    val peelTwo: ZIO[Blocking & Console, IOException, Unit]  = peelOne.provideSomeLayer(TelemetryContext.liveOtel)
    peelTwo.exitCode
