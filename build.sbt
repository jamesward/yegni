import java.io.OutputStream
import java.io.IOException

scalaVersion := "3.0.0-RC3"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "1.0.7",
)

//scalacOptions += "-language:strictEquality"

lazy val runIt = taskKey[Unit]("runIt")

val badOutputStream = new OutputStream {
  def write(i: Int): Unit = throw new IOException("bad")
}

runIt := {
  val opts = forkOptions.value.withOutputStrategy(OutputStrategy.CustomOutput(badOutputStream))
  implicit val scalaRun = new ForkRun(opts)
  val cp = (Runtime / fullClasspath).value.map(_.data)
  sbt.Run.run("itwontfail", cp, Seq.empty, streams.value.log).get
}

lazy val runItZ = taskKey[Unit]("runItZ")

runItZ := {
  val opts = forkOptions.value.withOutputStrategy(OutputStrategy.CustomOutput(badOutputStream))
  implicit val scalaRun = new ForkRun(opts)
  val cp = (Runtime / fullClasspath).value.map(_.data)
  sbt.Run.run("ItWontFailZ", cp, Seq.empty, streams.value.log).get
}