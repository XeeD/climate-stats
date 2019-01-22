name := "climate-stats"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= {
  val akkaVersion = "2.5.16"
  val akkaHttpVersion = "10.1.4"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  )
}

libraryDependencies += "org.jsoup" % "jsoup" % "1.11.2",
