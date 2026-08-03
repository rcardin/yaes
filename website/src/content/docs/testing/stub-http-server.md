---
title: Testing HTTP with StubHttpServer
description: ScalaTest utilities for testing HTTP interactions in λÆS with an in-process stub server.
sidebar:
  label: Testing HTTP with StubHttpServer
  order: 2
---

`yaes-http-test-scalatest` provides `StubHttpServerSpec`, a ScalaTest mixin trait that spins up a lightweight in-process HTTP stub server backed by the JDK's built-in `com.sun.net.httpserver.HttpServer`. It captures incoming requests and returns configurable responses — no external process or dependency required.

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "io.yaes" %% "yaes-http-test-scalatest" % "0.23.0" % Test
```

This module depends only on ScalaTest — no λÆS runtime dependency is pulled in transitively.

> Check [Maven Central](https://central.sonatype.com/artifact/io.yaes/yaes-http-test-scalatest_3) for the latest version.

---

## Quick Start

Mix `StubHttpServerSpec` into your spec class to get a running stub server wired into the ScalaTest lifecycle:

```scala
import io.yaes.test.http.scalatest.{StubHttpServerSpec, StubResponse}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.net.URI
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers

class MyHttpClientSpec
    extends AnyFlatSpec
    with Matchers
    with StubHttpServerSpec {

  private val http = HttpClient.newHttpClient()

  "MyHttpClient" should "call the correct endpoint" in {
    stubServer.setHandler(_ => StubResponse(200, """{"status":"ok"}"""))

    val request = HttpRequest
      .newBuilder(URI.create(s"$stubBaseUrl/api/resource"))
      .GET()
      .build()
    http.send(request, BodyHandlers.ofString())

    stubServer.capturedRequests.head.path shouldBe "/api/resource"
  }
}
```

---

## Lifecycle

The mixin manages the server automatically:

| Event | What happens |
|---|---|
| Suite start | Server binds to an ephemeral port and starts |
| Before each test | Captured requests cleared, handler reset to default (500) |
| After all tests | Server stopped, port released |

No manual setup or teardown is needed.

---

## `stubServer` and `stubBaseUrl`

`StubHttpServerSpec` exposes two members:

```scala
val stubServer: StubHttpServer  // the server instance
def stubBaseUrl: String         // e.g. "http://localhost:54321"
```

Use `stubBaseUrl` when constructing request URIs so tests automatically use the ephemeral port.

---

## Configuring Responses

Call `setHandler` with a function from `CapturedRequest` to `StubResponse`:

```scala
stubServer.setHandler { req =>
  if req.path == "/api/users" then
    StubResponse(200, """[{"id":1}]""", Map("Content-Type" -> "application/json"))
  else
    StubResponse(404, "not found")
}
```

`StubResponse` has three fields:

```scala
case class StubResponse(
    statusCode: Int,
    body: String,
    headers: Map[String, String] = Map.empty
)
```

If no handler is configured (or after a `reset`), the server returns `500` with body `"no handler configured"`.

---

## Inspecting Captured Requests

After making HTTP calls, read `capturedRequests` to assert on what was received:

```scala
val captured = stubServer.capturedRequests
captured should have size 1
captured.head.method   shouldBe "GET"
captured.head.path     shouldBe "/api/users"
captured.head.rawQuery shouldBe Some("page=1")
```

`CapturedRequest` contains:

```scala
case class CapturedRequest(
    method: String,
    path: String,
    rawQuery: Option[String],
    headers: Map[String, List[String]],  // header names are lower-cased
    body: String
)
```

---

## Requirements

- **Java 25+**: Required by λÆS for virtual threads and structured concurrency
- **Scala 3.8.1+**: Uses Scala 3 syntax
- **ScalaTest 3.x**: Included transitively
