scalaVersion := "3.0.0-RC3"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "1.0.7",
)

scalacOptions += "-Yexplicit-nulls"

lazy val runIt = taskKey[Unit]("runIt")

runIt := {
  implicit val scalaRun = runner.value
  val cp = (Runtime / fullClasspath).value.map(_.data)
  sbt.Run.run("itwontfail", cp, Seq.empty, null).get
}
