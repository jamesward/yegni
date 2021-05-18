import scala.List
import java.lang.String

object ItWontFailZ extends zio.App:

  val myAppLogic = putStrLn("hello, world")//.catchAll(_ => zio.ZIO.unit)

  def run(args: List[String]) = myAppLogic.exitCode
