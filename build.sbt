
ThisBuild / version := "0.1.1-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.11"

lazy val root = (project in file("."))
  .settings(
    name := "api-template",
    assembly / mainClass := Some("Server"),
  )

val http4sVersion = "0.23.23"
val logbackVersion = "1.4.7"
val fs2Version = "3.7.0"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % scalaVersion.value % "provided",
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "co.fs2" %% "fs2-core" % fs2Version
)

enablePlugins(JavaAppPackaging)

ThisBuild / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
