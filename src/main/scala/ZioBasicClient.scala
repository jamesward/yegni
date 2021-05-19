import services._
import zio.{App, ExitCode, ZEnv, ZIO}
import zio.console.Console
import zio.console.putStrLn

import java.lang.String
import java.io.IOException
import scala.collection.immutable.List
import scala.{Any, Nothing, Unit, &}

object ZioBasicClient extends App:
  override def run(args: List[String]) =
    // val u: ZIO[Console & TelemetryContext & HttpClient, IOException, Unit] = for
    //   resp   <- HttpClient.send("https://jamesward.com")
    //   _      <- putStrLn(resp.body)
    // yield ()
    // val peelOne = u
    //   .provideSomeLayer(HttpClient.live ++ TelemetryContext.liveOtel)
    // val peelTwo  = peelOne
    //   // .provideSomeLayer[TelemetryContext](TelemetryContext.liveOtel)
    // peelTwo.exitCode
    ZIO.succeed("").exitCode
