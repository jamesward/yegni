import zio.console.*
import scala.{List, Unit}
import java.lang.String

object ItWontFailZ extends zio.App:

  def run(args: List[String]) = myAppLogic.exitCode

  val myAppLogic: zio.URIO[Console, Unit] = for
    _ <- putStrLn("hello, world")
    _ <- putStrLn("but can it?")
  yield ()
