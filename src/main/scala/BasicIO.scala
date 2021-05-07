import cats.effect.{ExitCode, IO, IOApp}

import java.lang.String
import scala.List

object BasicIO extends IOApp:

  def run(args: List[String]) = IO.println("Hello, World!").as(ExitCode.Success)
