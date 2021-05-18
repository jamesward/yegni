import java.io.OutputStream
import java.io.IOException

enablePlugins(GraalVMNativeImagePlugin)

name := "yegni"

scalaVersion := "3.0.0"

resolvers += "Maven Central Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "com.google.cloud.opentelemetry" % "exporter-trace" % "0.15.0",
  "com.google.cloud.opentelemetry" % "exporter-metrics" % "0.15.0-alpha",
  "dev.zio"    %% "zio"                 % "1.0.7+48-a6b0c87d-SNAPSHOT",
  "org.slf4j"  %  "slf4j-simple"        % "1.7.30",
)

// must use jdk 11 for static / muslc
javacOptions ++= Seq("-source", "11", "-target", "11")

scalacOptions += "-release:11"
scalacOptions += "-Yno-imports"

reStart / mainClass := Some("ZioWebApp")

Compile / packageDoc / publishArtifact := false

Compile / doc / sources := Seq.empty

graalVMNativeImageOptions ++= Seq(
  "--verbose",
  "--no-server",
  "--no-fallback",
  "--static",
  "--install-exit-handlers",
  "--enable-http",
  "--enable-https",
  "--enable-all-security-services",
  "--libc=musl",
  "-H:+RemoveSaturatedTypeFlows",
  "-H:+ReportExceptionStackTraces",
  "-H:+PrintAOTCompilation",
  "-H:+PrintClassInitialization",
  "-H:+PrintFeatures",
  "-H:+PrintStaticTruffleBoundaries",
  "-H:+StackTrace",
  "-H:+TraceLoggingFeature",
  "-H:+ReportExceptionStackTraces",
  "--allow-incomplete-classpath",
  //"--report-unsupported-elements-at-runtime",
)

// todo: a task for each
//GraalVMNativeImage / mainClass := Some("Flaky")
//GraalVMNativeImage / mainClass := Some("graaler")
GraalVMNativeImage / mainClass := sys.props.get("mainClass").orElse(Some("graaler"))

// todo: disable musl when not running via docker
lazy val flakyGraal = taskKey[Unit]("flakyGraal")

flakyGraal := {
  (GraalVMNativeImage / packageBin).value
}

// debugging tasks to generate graal configs

lazy val flakyGraalRun = taskKey[Unit]("flakyGraalRun")

flakyGraalRun := {
  val opts = forkOptions.value.withRunJVMOptions(Vector("-agentlib:native-image-agent=config-output-dir=src/graal"))
  implicit val scalaRun = new ForkRun(opts)
  val cp = (Runtime / fullClasspath).value.map(_.data)
  sbt.Run.run("Flaky", cp, Seq.empty, streams.value.log).get
}

lazy val zioWebAppGraalRun = taskKey[Unit]("zioWebAppGraalRun")

zioWebAppGraalRun := {
  val opts = forkOptions.value.withRunJVMOptions(Vector("-agentlib:native-image-agent=config-output-dir=src/graal"))
  implicit val scalaRun = new ForkRun(opts)
  val cp = (Runtime / fullClasspath).value.map(_.data)
  sbt.Run.run("ZioWebApp", cp, Seq.empty, streams.value.log).get
}
