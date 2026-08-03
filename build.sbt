inThisBuild(
  List(
    organization := "io.yaes",
    homepage     := Some(url("https://github.com/yaes-io/yaes")),
    // Alternatively License.Apache2 see https://github.com/sbt/librarymanagement/blob/develop/core/src/main/scala/sbt/librarymanagement/License.scala
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "rcardin",
        "Riccardo Cardin",
        "riccardo DOT cardin AT gmail.com",
        url("https://github.com/rcardin")
      )
    )
  )
)

name := "yaes"
val scala3Version = "3.8.3"
scalaVersion := scala3Version

scalacOptions += "-target:25"
javacOptions ++= Seq("-source", "25", "-target", "25")

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

lazy val `yaes-test` = project
  .in(file("yaes-test"))
  .aggregate(`yaes-core-test-scalatest`, `yaes-http-test-scalatest`)
  .settings(scalaVersion := scala3Version)

lazy val `yaes-core-test-scalatest` = project
  .in(file("yaes-test/core/scalatest"))
  .dependsOn(`yaes-core`)
  .settings(commonSettings)
  .settings(
    name         := "yaes-core-test-scalatest",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(dependencies.scalatest)
  )

lazy val `yaes-http-test-scalatest` = project
  .in(file("yaes-test/http/scalatest"))
  .settings(commonSettings)
  .settings(
    name         := "yaes-http-test-scalatest",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(dependencies.scalatest)
  )

lazy val `yaes-http` = project
  .aggregate(core, server, client, circe, jsoniter)
  .settings(scalaVersion := scala3Version)

lazy val core = project
  .in(file("yaes-http/core"))
  .dependsOn(`yaes-core`)
  .settings(commonSettings)
  .settings(
    name         := "yaes-http-core",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

lazy val client = project
  .in(file("yaes-http/client"))
  .dependsOn(`yaes-core`, core)
  .settings(commonSettings)
  .settings(
    name         := "yaes-http-client",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

lazy val circe = project
  .in(file("yaes-http/circe"))
  .dependsOn(`yaes-core`, core)
  .settings(commonSettings)
  .settings(
    name         := "yaes-http-circe",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies ++ circeDependencies ++ Seq(
      dependencies.circeGeneric % Test
    )
  )

lazy val jsoniter = project
  .in(file("yaes-http/jsoniter"))
  .dependsOn(`yaes-core`, core)
  .settings(commonSettings)
  .settings(
    name         := "yaes-http-jsoniter",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies ++ jsoniterDependencies
  )

lazy val server = project
  .in(file("yaes-http/server"))
  .dependsOn(`yaes-core`, core)
  .settings(
    name         := "yaes-http-server",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

// Scalafix migration rules, published as `io.yaes:yaes-migration_2.13`. Even
// though the rest of YAES targets Scala 3, the rule module is compiled with
// Scala 2.13: external Scalafix rules (those pulled in via `scalafixDependencies`)
// are loaded by the `scalafix-reflect_2.13` layer, so the user's documented
// single line `scalafixDependencies += "io.yaes" %% "yaes-migration"` resolves
// `_2.13` regardless of the consumer's own Scala version. (A `_3` build compiles
// (Scalafix's own `scalafix-rules_3` does the same via `for3Use2_13`) but it
// rides inside the CLI classpath and is not reachable as an external rule, so
// publishing `_3` would break that one-liner.)
lazy val scalafixRuleScalaVersion = "2.13.16"
lazy val scalafixVersion          = "0.14.3"

lazy val `yaes-migration` = project
  .in(file("yaes-migration/rules"))
  .settings(
    name         := "yaes-migration",
    moduleName   := "yaes-migration",
    scalaVersion := scalafixRuleScalaVersion,
    libraryDependencies ++= Seq(
      "ch.epfl.scala" %% "scalafix-core" % scalafixVersion,
      // scalafix-testkit is cross-published with the full Scala patch version,
      // hence CrossVersion.full rather than the binary `%%`.
      ("ch.epfl.scala" %% "scalafix-testkit" % scalafixVersion % Test)
        .cross(CrossVersion.full)
    )
  )

lazy val yaes = (project in file("."))
  .aggregate(`yaes-core`, `yaes-data`, `yaes-cats`, `yaes-slf4j`, `yaes-http`, `yaes-test`, `yaes-migration`)
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
    val circeVersion      = "0.14.15"
    val circeCore         = "io.circe"           %% "circe-core"     % circeVersion
    val circeParser       = "io.circe"           %% "circe-parser"   % circeVersion
    val circeGeneric      = "io.circe"           %% "circe-generic"  % circeVersion
    val jsoniterVersion   = "2.38.9"
    val jsoniterCore      = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % jsoniterVersion
    val jsoniterMacros    = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterVersion
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

lazy val circeDependencies = Seq(
  dependencies.circeCore,
  dependencies.circeParser
)

lazy val jsoniterDependencies = Seq(
  dependencies.jsoniterCore,
  dependencies.jsoniterMacros % "provided,test"
)
