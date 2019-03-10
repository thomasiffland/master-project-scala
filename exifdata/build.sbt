name := "exifdata"
import com.typesafe.sbt.packager.docker._

version := "0.1"
enablePlugins(JavaAppPackaging)

scalaVersion := "2.12.8"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.1.7",
  "com.typesafe.akka" %% "akka-stream" % "2.5.19",
  "commons-io" % "commons-io" % "2.6"

)

daemonUserUid in Docker := None
daemonUser in Docker := "root"
dockerExposedPorts ++=Seq(8080)
dockerCommands ++= Seq(
  ExecCmd("RUN","apt-get", "update"),
  ExecCmd("RUN", "apt-get", "install","imagemagick","-y"),
  ExecCmd("RUN", "apt-get", "install","software-properties-common","-y"),
  ExecCmd("RUN", "add-apt-repository", "ppa:dhor/myway"),
  ExecCmd("RUN", "apt-get", "install","dcraw","-y"),
  ExecCmd("RUN", "apt-get", "install","ffmpeg","-y"),
  ExecCmd("RUN", "apt-get", "install","libimage-exiftool-perl","-y"),
)

