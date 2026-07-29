---
title: Testing with RaiseSpec
description: ScalaTest utilities for testing λÆS Raise effect code without boilerplate.
sidebar:
  label: Testing with RaiseSpec
  order: 1
---

`yaes-core-test-scalatest` provides `RaiseSpec`, a ScalaTest mixin trait that eliminates boilerplate handler wiring when testing code using the `Raise` effect. Mix it into any spec class to get two focused test helpers: one for asserting success, one for asserting a raised error.

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "io.yaes" %% "yaes-core-test-scalatest" % "0.23.0" % Test
```

`yaes-core` and ScalaTest are included transitively — no additional declarations needed.

> Check [Maven Central](https://central.sonatype.com/artifact/io.yaes/yaes-core-test-scalatest_3) for the latest version.

---

## Quick Start

Mix `RaiseSpec` into your spec class alongside your usual ScalaTest traits:

```scala
import io.yaes.{Raise, raises}
import io.yaes.test.scalatest.RaiseSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

sealed trait AppError
case object NotFound extends AppError
case class InvalidInput(msg: String) extends AppError

class UserServiceSpec extends AnyFlatSpec with Matchers with RaiseSpec {

  def findUser(id: Int): String raises AppError =
    if id > 0 then s"user-$id" else Raise.raise(NotFound)

  "findUser" should "return a user for valid id" in {
    val result = failOnRaise[AppError, String] { findUser(1) }
    result shouldBe "user-1"
  }

  it should "raise NotFound for id zero" in {
    val error = interceptRaised[AppError, String] { findUser(0) }
    error shouldBe NotFound
  }
}
```

---

## `failOnRaise`

```scala
def failOnRaise[E, A](body: Raise[E] ?=> A): A
```

Runs `body` with a `Raise[E]` context. If the body completes successfully, the result is returned. If an error is raised, the test fails immediately with:

```
Expected the test not to raise any errors but it did with error '<error>'
```

Use `failOnRaise` in happy-path tests where an unexpected error should be a test failure, and where you want to chain further assertions on the returned value:

```scala
val user = failOnRaise[AppError, User] { createUser("alice") }
user.name shouldBe "alice"
user.email shouldBe "alice@example.com"
```

---

## `interceptRaised`

```scala
def interceptRaised[E, A](body: Raise[E] ?=> A): E
```

Runs `body` with a `Raise[E]` context. If an error is raised, it is returned for further inspection. If the body completes successfully, the test fails with:

```
Expected an error to be raised but body evaluated successfully
```

Use `interceptRaised` in error-path tests to assert on the structure of the raised error:

```scala
val error = interceptRaised[InvalidInput, Int] {
  validateAge(-1)
}
error.msg shouldBe "age must be positive"
```

---

## Union Types

Both methods use `Raise.either` internally, so no `ClassTag` is required. Union error types work without any special handling:

```scala
val error = interceptRaised[String | Int, Boolean] {
  Raise.raise("something went wrong")
}
error shouldBe "something went wrong"
```

---

## Requirements

- **Java 25+**: Required by λÆS for virtual threads and structured concurrency
- **Scala 3.8.1+**: Uses Scala 3 context functions (`?=>`)
- **ScalaTest 3.x**: Included transitively
