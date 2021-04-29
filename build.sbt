scalaVersion := "3.0.0-RC3"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "1.0.7",
)

scalacOptions += "-Yexplicit-nulls"

lazy val runIt = taskKey[Unit]("runIt")

runIt := {
  // fork := true
  // outputStrategy := None
  (Compile / runMain).toTask(" itwontfail").value
}
