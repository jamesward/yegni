import java.io.OutputStream
import java.io.IOException

scalaVersion := "3.0.0-RC2"

libraryDependencies ++= Seq(
  "dev.zio"    %% "zio"                 % "1.0.6",
  "org.http4s" %% "http4s-blaze-server" % "1.0.0-M21",
  "org.http4s" %% "http4s-blaze-client" % "1.0.0-M21",
  "org.http4s" %% "http4s-dsl"          % "1.0.0-M21",
  "dev.zio"    %% "zio-interop-cats"    % "3.0.2.0",
  "org.slf4j"  %  "slf4j-simple"        % "1.7.30",
  "com.google.cloud.opentelemetry" % "exporter-trace" % "0.15.0",
  "com.google.cloud.opentelemetry" % "exporter-metrics" % "0.15.0-alpha"
)

scalacOptions += "-Yno-imports"

//scalacOptions += "-language:strictEquality"

lazy val runIt = taskKey[Unit]("runIt")

val badOutputStream = new OutputStream {
  def write(i: Int): Unit = throw new IOException("bad")
}

reStart / mainClass := Some("WebApp")

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