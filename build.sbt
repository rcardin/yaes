inThisBuild(
  List(
    organization := "in.rcard.yaes",
    homepage     := Some(url("https://github.com/rcardin")),
    // Alternatively License.Apache2 see https://github.com/sbt/librarymanagement/blob/develop/core/src/main/scala/sbt/librarymanagement/License.scala
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "rcardin",
        "Riccardo Cardin",
        "riccardo DOT cardin AT gmail.com",
        url("https://github.com/rcardin/yaes")
      )
    )
  )
)

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository                 := "https://s01.oss.sonatype.org/service/local"
sonatypeProfileName                := "in.rcard"

name := "yaes"
val scala3Version = "3.6.2"
scalaVersion := scala3Version

lazy val `yaes-core` = project
  .settings(
    name         := "yaes-core",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

lazy val yaes = (project in file("."))
  .aggregate(`yaes-core`)
  .settings(
    scalaVersion := scala3Version
  )

lazy val dependencies =
  new {
    val scalatestVersion = "3.2.19"
    val scalatest        = "org.scalatest" %% "scalatest" % scalatestVersion
  }

lazy val commonDependencies = Seq(
  dependencies.scalatest % Test
)
