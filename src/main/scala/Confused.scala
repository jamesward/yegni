import java.lang.System
import java.io.{
  IOException,
  OutputStream,
}
import scala.{
  Console,
  Int,
  Unit,
  main,
}

@main def confused() =

  val badOutputStream = new OutputStream:
    def write(i: Int): Unit = throw new IOException("bad")

  Console.withErr(System.err)(Console.println("ok"))

  try
    Console.withOut(badOutputStream)(Console.println("asdf"))
  catch
    case e: IOException =>
      Console.withErr(System.err)(Console.println(e.getMessage))