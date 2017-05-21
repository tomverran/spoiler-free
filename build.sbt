name := "spoiler-free"

maintainer := "Tom"

packageSummary := "Spoiler-Free web app"

packageDescription := "Unsubscribe from /r/formula1 every race weekend"

scalaVersion := "2.12.2"

libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.5"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.7"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.4.13"

libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.4.13"

libraryDependencies += "com.gu" %% "scanamo" % "0.9.2"

libraryDependencies += "org.mnode.ical4j" % "ical4j" % "2.0.0"

enablePlugins(JavaServerAppPackaging, SystemdPlugin)

enablePlugins(DebianPlugin)

enablePlugins(GitVersioning)

git.gitTagToVersionNumber := { tag: String =>
  if(tag matches "[0-9]+\\..*") Some(tag)
  else None
}

val circeVersion = "0.7.0"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)