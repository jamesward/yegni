import java.lang.{
  Exception,
  String,
}
import java.io.{
  IOException,
  OutputStream,
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

val badOutputStream = new OutputStream:
  def write(i: Int): Unit = throw new Exception("bad")

def println(a: Any) =
  Console.withOut(badOutputStream)(Console.println(a))

def putStrLn(line: String): IO[IOException, Unit] =
  IO.effect(println(line)).catchAll( t => ZIO.fail(new IOException(t)))
