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

name := "yaes"
val scala3Version = "3.7.2"
scalaVersion := scala3Version

scalacOptions += "-target:24"
javacOptions ++= Seq("-source", "24", "-target", "24")

lazy val `yaes-data` = project
  .settings(
    name         := "yaes-data",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

lazy val `yaes-core` = project
  .dependsOn(`yaes-data`)
  .settings(
    name         := "yaes-core",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

lazy val yaes = (project in file("."))
  .aggregate(`yaes-core`, `yaes-data`)
  .settings(
    scalaVersion := scala3Version
  )

lazy val dependencies =
  new {
    val scalatestVersion  = "3.2.19"
    val scalatest         = "org.scalatest"     %% "scalatest"       % scalatestVersion
    val scalacheckVersion = "3.2.19.0"
    val scalacheck        = "org.scalatestplus" %% "scalacheck-1-18" % scalacheckVersion
  }

lazy val commonDependencies = Seq(
  dependencies.scalatest  % Test,
  dependencies.scalacheck % Test
)
