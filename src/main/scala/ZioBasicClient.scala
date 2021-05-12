import services._
import zio.{App, ExitCode, ZEnv, ZIO}
import zio.console.putStrLn

import java.lang.String
import scala.collection.immutable.List
import scala.{Any, Nothing}

object ZioBasicClient extends App:
  override def run(args: List[String]) =
    val u = for
      resp   <- HttpClient.send("https://jamesward.com")
      _      <- putStrLn(resp.body)
    yield ()
    u.provideCustomLayer(HttpClient.live).exitCode
