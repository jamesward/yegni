import java.io.OutputStream
import java.io.IOException

enablePlugins(GraalVMNativeImagePlugin)

name := "yegni"

scalaVersion := "3.0.0-RC2"

libraryDependencies ++= Seq(
  "com.google.cloud.opentelemetry" % "exporter-trace" % "0.15.0",
  "com.google.cloud.opentelemetry" % "exporter-metrics" % "0.15.0-alpha"
  "dev.zio"    %% "zio"                 % "1.0.6",
  "org.typelevel" %% "cats-effect"         % "3.1.0",
  "org.http4s" %% "http4s-blaze-server" % "1.0.0-M21",
  "org.http4s" %% "http4s-blaze-client" % "1.0.0-M21",
  "org.http4s" %% "http4s-dsl"          % "1.0.0-M21",
  "dev.zio"    %% "zio-interop-cats"    % "3.0.2.0",
  "org.slf4j"  %  "slf4j-simple"        % "1.7.30",
  //"org.scalameta" % "svm-subs"             % "101.0.0",
  //"org.scalameta" % "svm-subs_2.13" % "20.2.0",
)

// must use jdk 11 for static / muslc
javacOptions ++= Seq("-source", "11", "-target", "11")

scalacOptions += "-release:11"
//scalacOptions += "-target:11"
//scalacOptions += "-target:jvm-11"
scalacOptions += "-Yno-imports"

reStart / mainClass := Some("WebApp")

Compile / packageDoc / publishArtifact := false

Compile / doc / sources := Seq.empty

graalVMNativeImageOptions ++= Seq(
  "--verbose",
  "--no-server",
  "--no-fallback",
//  "--static",
  "--install-exit-handlers",
  "--enable-http",
  "--enable-https",
  "--enable-all-security-services",
//  "--libc=musl",
//  "-H:+RemoveSaturatedTypeFlows",
  "-H:+ReportExceptionStackTraces",
  "-H:+PrintAOTCompilation",
  "-H:+PrintClassInitialization",
  "-H:+PrintFeatures",
  "-H:+PrintStaticTruffleBoundaries",
  "-H:+StackTrace",
  "-H:+TraceLoggingFeature",
  "-H:+ReportExceptionStackTraces",
//  "--allow-incomplete-classpath",
//  "--report-unsupported-elements-at-runtime",
)

//GraalVMNativeImage / mainClass := Some("itwontfail")
//GraalVMNativeImage / mainClass := Some("ItWontFailZ")
//GraalVMNativeImage / mainClass := Some("Flaky")
//GraalVMNativeImage / mainClass := Some("BasicServer")
GraalVMNativeImage / mainClass := Some("BasicWebApp")
//GraalVMNativeImage / mainClass := Some("BasicIO")

//fork := true
//run / javaOptions += s"-agentlib:native-image-agent=config-output-dir=src/graal"

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