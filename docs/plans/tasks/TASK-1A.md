# TASK-1A | build.sbt + dirs

status: [x]
requires: —
verify: `sbt compile`

## Edit `build.sbt`

Add before `lazy val circe`:

```scala
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
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name         := "yaes-http-client",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )
```

Change `server`:
```scala
lazy val server = project
  .in(file("yaes-http/server"))
  .dependsOn(`yaes-core`, core)
  // rest unchanged
```

Change `circe`:
```scala
lazy val circe = project
  .in(file("yaes-http/circe"))
  .dependsOn(server, core)
  // rest unchanged
```

Change `yaes-http`:
```scala
lazy val `yaes-http` = project
  .aggregate(core, server, client, circe)
  .settings(scalaVersion := scala3Version)
```

## Create dirs

```
yaes-http/core/src/main/scala/in/rcard/yaes/http/core/
yaes-http/client/src/main/scala/in/rcard/yaes/http/client/
yaes-http/client/src/test/scala/in/rcard/yaes/http/client/
```
