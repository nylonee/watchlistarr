ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "watchlistarr",
    assembly / mainClass := Some("Server")
  )

val caseInsensitiveVersion = "1.4.0"
val catsCoreVersion = "2.9.0"
val catsEffectVersion = "3.5.0"
val catsEffectKernelVersion = "3.5.1"
val circeGenericExtrasVersion = "0.14.3"
val circeVersion = "0.14.6"
val fs2Version = "3.7.0"
val http4sVersion = "0.23.23"
val logbackVersion = "1.4.11"
val scaffeineVersion = "5.2.1"
val scalamockVersion = "5.2.0"
val scalatestVersion = "3.2.17"
val shapelessVersion = "2.3.10"
val slf4jVersion = "2.0.12"
val snakeYamlVersion = "2.2"
val vaultVersion = "3.5.0"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % scalaVersion.value % "provided",
  "ch.qos.logback" % "logback-classic" % logbackVersion % Runtime,
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-client" % http4sVersion,
  "org.http4s" %% "http4s-core" % http4sVersion,
  "co.fs2" %% "fs2-core" % fs2Version,
  "co.fs2" %% "fs2-io" % fs2Version,
  "com.chuusai" %% "shapeless" % shapelessVersion,
  "io.circe" %% "circe-core" % circeVersion,
  "org.typelevel" %% "case-insensitive" % caseInsensitiveVersion,
  "org.typelevel" %% "cats-core" % catsCoreVersion,
  "org.typelevel" %% "cats-effect" % catsEffectVersion,
  "org.typelevel" %% "cats-effect-kernel" % catsEffectKernelVersion,
  "org.typelevel" %% "vault" % vaultVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-generic-extras" % circeGenericExtrasVersion,
  "org.yaml" % "snakeyaml" % snakeYamlVersion,
  "com.github.blemale" %% "scaffeine" % scaffeineVersion % "compile",
  "io.circe" %% "circe-parser" % circeVersion % Test,
  "org.scalamock" %% "scalamock" % scalamockVersion % Test,
  "org.scalatest" %% "scalatest" % scalatestVersion % Test
)

enablePlugins(JavaAppPackaging)

ThisBuild / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case PathList("META-INF", _*) => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
