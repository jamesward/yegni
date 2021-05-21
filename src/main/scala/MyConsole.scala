import java.lang.{
  Exception,
  String,
  System,
}
import java.io.{
  IOException,
  OutputStream,
  PrintStream,
}
import scala.{
  Any,
  Int,
  Console,
  Unit,
}
import zio.{
  IO,
  ZIO,
}

object MyConsole:

  val badOutputStream = new OutputStream:
    def write(i: Int): Unit = throw new Exception("bad")

  System.setOut(new PrintStream(badOutputStream))

  def println(a: Any) =
    Console.println(a)

  def putStrLn(line: String): IO[IOException, Unit] =
    IO.effect(println(line)).catchAll(t => ZIO.fail(new IOException(t)))
