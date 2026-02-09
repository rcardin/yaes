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
val scala3Version = "3.8.1"
scalaVersion := scala3Version

scalacOptions += "-target:24"
javacOptions ++= Seq("-source", "24", "-target", "24")

lazy val `yaes-data` = project
  .dependsOn(`yaes-core`)
  .settings(commonSettings)
  .settings(
    name         := "yaes-data",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

lazy val `yaes-core` = project
  .settings(commonSettings)
  .settings(
    name         := "yaes-core",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

lazy val `yaes-cats` = project
  .dependsOn(`yaes-data`)
  .settings(commonSettings)
  .settings(
    name         := "yaes-cats",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies ++ catsDependencies
  )

lazy val `yaes-slf4j` = project
  .dependsOn(`yaes-core`)
  .settings(commonSettings)
  .settings(
    name         := "yaes-slf4j",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies ++ slf4jDependencies
  )

lazy val `yaes-http` = project
  .aggregate(server)
  .settings(
    scalaVersion := scala3Version
  )

lazy val server = project
  .in(file("yaes-http/server"))
  .dependsOn(`yaes-core`)
  .settings(
    name         := "yaes-http-server",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

lazy val yaes = (project in file("."))
  .aggregate(`yaes-core`, `yaes-data`, `yaes-cats`, `yaes-slf4j`, `yaes-http`)
  .settings(
    scalaVersion := scala3Version,
    Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)
  )

lazy val dependencies =
  new {
    val scalatestVersion  = "3.2.19"
    val scalatest         = "org.scalatest"     %% "scalatest"       % scalatestVersion
    val scalacheckVersion = "3.2.19.0"
    val scalacheck        = "org.scalatestplus" %% "scalacheck-1-18" % scalacheckVersion
    val catsVersion       = "2.13.0"
    val catsCore          = "org.typelevel"     %% "cats-core"       % catsVersion
    val catsEffectVersion = "3.6.3"
    val catsEffect        = "org.typelevel"     %% "cats-effect"     % catsEffectVersion
    val slf4jVersion      = "2.0.17"
    val slf4jApi          = "org.slf4j"          % "slf4j-api"       % slf4jVersion
    val slf4jSimple       = "org.slf4j"          % "slf4j-simple"    % slf4jVersion
  }

lazy val commonDependencies = Seq(
  dependencies.scalatest  % Test,
  dependencies.scalacheck % Test
)

lazy val commonSettings = Seq(
  Test / logBuffered := false,
  Test / parallelExecution := false,
  Test / fork := true,
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
)

lazy val catsDependencies = Seq(
  dependencies.catsCore,
  dependencies.catsEffect
)

lazy val slf4jDependencies = Seq(
  dependencies.slf4jApi,
  dependencies.slf4jSimple % Test
)
