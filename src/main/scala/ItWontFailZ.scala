import zio.console.*

object ItWontFailZ extends zio.App:

  def run(args: List[String]) = myAppLogic.exitCode

  val myAppLogic: zio.URIO[Console, Unit] = putStrLn("hello, world")
