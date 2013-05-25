name := "mud"

version := "0.1"

scalaVersion := "2.10.1"

resolvers ++= Seq(
  "snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "releases"  at "http://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  "org.scalaz" % "scalaz-core_2.10" % "7.0.0",
  "org.scalaz" % "scalaz-effect_2.10" % "7.0.0",
  "org.scala-stm" %% "scala-stm" % "0.7",
  "io.netty" % "netty" % "4.0.0.Alpha8"
)





