![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/rcardin/yaes/scala.yml?branch=main)
![Maven Central](https://img.shields.io/maven-central/v/in.rcard.yaes/yaes-core_3)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/rcardin/yaes)
[![javadoc](https://javadoc.io/badge2/in.rcard.yaes/yaes-core_3/javadoc.svg)](https://javadoc.io/doc/in.rcard.yaes/yaes-core_3)

![logo](./logo.svg)

# Yet Another Effect System (λÆS)

λÆS is an experimental effect system in Scala inspired by the ideas behind Algebraic Effects. Using Scala 3 [context parameters](https://docs.scala-lang.org/scala3/reference/contextual/using-clauses.html) and [context functions](https://docs.scala-lang.org/scala3/reference/contextual/context-functions.html), it provides a way to define and handle effects in a modular and composable manner. 

Here is the talk I gave at the **Scalar 2025** about the main concepts behind the library: 

[![Watch the video](https://img.youtube.com/vi/TXUxCsPpZp0/maxresdefault.jpg)](https://youtu.be/TXUxCsPpZp0)

Available modules are:
 * `yaes-core`: The main effects of the λÆS library.
 * `yaes-data`: A set of data structures that can be used with the λÆS library.

What's new in λÆS when compared to other effect systems? Well, you can choose to use a monadic style like the following:

```scala 3
import in.rcard.yaes.Random.*
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Yaes.*

def drunkFlip(using Random, Raise[String]): String = for {
  caught <- Random.nextBoolean
  heads  <- if (caught) Random.nextBoolean else Raise.raise("We dropped the coin")
} yield if (heads) "Heads" else "Tails"
```

Or a more direct style like this:

```scala 3
import in.rcard.yaes.Random.*
import in.rcard.yaes.Raise.*

def drunkFlip(using Random, Raise[String]): String = {
  val caught = Random.nextBoolean
  if (caught) {
    val heads = Random.nextBoolean
    if (heads) "Heads" else "Tails"
  } else {
    Raise.raise("We dropped the coin")
  }
}
```

In λÆS types like `Random` and `Raise` are *Effects*. A *Side Effect* is an unpredictable interaction, usually with an external system. An Effect System manages *Side Effects* by tracking and wrapping them into *Effects*. An *Effect* describes the type of the *Side Effect* and the return type of an effectful computation. We manage *Side Effect* behavior by putting them in a kind of box.
Calling the above `drunkFlip` function will not execute the effects. Instead, it will return a value that represents something that can be run but hasn’t yet. This is called deferred execution. 

An Effect System provides all the tools to manage and execute Effectful computations in a deferred manner. In λÆS, such tools are called *Handlers*.

```scala 3
import in.rcard.yaes.Random.*
import in.rcard.yaes.Raise.*

val result: String = Raise.run { 
  Random.run { 
    drunkFlip
  }
}
```

In the above code, we are running the `drunkFlip` function with the `Random` and `Raise` effects. The `Raise.run` and `Random.run` functions are defined using *Handlers* that will execute the deferred effects. The approach reminds the one defined in the Algebraic Effects and Handlers theory. The example shows how to handle the `Raise` and `Random` effects one at a time. However, we're free to handle only one effect at a time:

```scala 3
import in.rcard.yaes.Random.*

val result: Raise[String] ?=> String = Random.run { 
  drunkFlip
}
```

The above code shows how to handle only the `Random` effect. The `Raise` effect is still present. It's a powerful feature that allows for a fine-grained management of the effects.

## Dependency

The library is available on Maven Central. To use it, add the following dependency to your build.sbt files:

```sbt
libraryDependencies += "in.rcard.yaes" %% "yaes-core" % "0.5.0"
```

The library is only available for Scala 3 and is currently in an experimental stage. The API is subject to change.

## Usage

The library provides a set of effects that can be used to define and handle effectful computations. The available effects are:

- [`IO`](#the-io-effect): Allows for running side-effecting operations.
- [`Async`](#the-async-effect): Allows for asynchronous computations and fiber management.
- [`Raise`](#the-raise-effect): Allows for raising and handling errors.
- [`Resource`](#the-resource-effect): Allows for automatic resource management with guaranteed cleanup.
- [`Input`](#the-input-effect): Allows for reading input from the console.
- [`Output`](#the-output-effect): Allows for printing output to the console.
- [`Random`](#the-random-effect): Allows for generating random content.
- [`Clock`](#the-clock-effect): Allows for managing time.
- [`System`](#the-system-effect): Allows for managing system properties and environment variables.
- [`Log`](#the-log-effect): Allows for logging messages at different levels.

### The `IO` Effect

The `IO` effect allows for running side-effecting operations:

```scala 3
import in.rcard.yaes.IO.*

case class User(name: String)

def saveUser(user: User)(using IO): Long =
  throw new RuntimeException("Read timed out")
```

The above code can throw an uncontrolled exception if the connection with the database times out. The generic `IO` effect lift the function in the world of the effectful computations, making it referentially transparent. It means that everything that is not referentially transparent should be defined using the `IO` effect. In fact, the `IO` effect provides a guard rail to uncontrolled exceptions since its handler returns always a monad that wraps the result of the effectful computation.


To run the effectful computation, we can use the provided handlers.

The first handler doesn't block the current thread:

```scala 3
import in.rcard.yaes.IO.*

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val result: Future[Long] = IO.run {
  saveUser(User("John"))
}
```

The library also provides a blocking handler that will block the current thread until the effectful computation is finished:

```scala 3
import in.rcard.yaes.IO.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.Try

val result: Long = IO.blockingRun {
  saveUser(User("John"))
}
```

Please, be aware that running an `IO` effectful computation both using the `IO.run` and `IO.blockingRun` methods breaks the referential transparency. Handlers should be used only at the edge of the application.

The default `IO` handler is implemented using Java Virtual Threads machinery. For every effectful computation, a new virtual thread is created and the computation is executed in that thread. 

### The `Async` Effect

The `Async` effect is built around the ideas developed in the [Sus4s](https://github.com/rcardin/sus4s) library. It allows for running asynchronous computations and managing fibers.

The default implementation of the `Async` effect is based Java Structured Concurrency provided by Java versions after 21. The `Async` effect provides a way to define asynchronous computations that are executed in a structured way. It means that every asynchronous computation is executed in a fiber that is managed by the `Async` effect.

The most important operation of the `Async` effect is the `fork` operation:

```scala 3
import in.rcard.yaes.Async.*

def findUserByName(name: String): Option[User] = Some(User(name))
val fb: Async ?=> Fiber[Option[User]] = Async.fork { findUserByName("John") }
```

The `fb` variable represent a fiber (lightweight thread) that is executing the `findUserByName` function. The `fork` operation returns a `Fiber` object that can be used to manage the execution of the asynchronous computation. In details, we can wait for the value of the computation using the `value` operation:

```scala 3
import in.rcard.yaes.Async.*
import in.rcard.yaes.Raise.*

val maybeUser: (Async, Raise[Cancelled]) ?=> Option[User] = fb.value
```

Or, we can just wait for the computation to finish:

```scala 3
val p: Async ?=> Option[User] = fb.join()
```

As for the `IO` effect, forking a new fiber or joining it doesn't execute the effectful computation. It just returns a value that represents the computation that can be run but hasn't yet.

Again, we can run the effectful computation using the provided handlers:

```scala 3
import in.rcard.yaes.Async.*

val maybeUser: Raise[Cancelled] ?=> Option[User] = Async.run {
    val fb: Async ?=> Fiber[Option[User]] = Async.fork { findUserByName("John") }
    fb.value
  }
```

The above code shows another important aspect of the λÆS library. We can handle an effect eliminating it from the list of effects one at time. In the above code, we are handling the `Async` effect first, and we remain with the `Raise` effect. It's a powerful feature that allows for a fine-grained management of the effects.

The `Async` effect is transparent to possible exceptions thrown by the effectful computation. Please, add the `IO` effect if you think the effectful computation can throw any exception.

#### Structured Concurrency

The `Async` effect implements **structured concurrency**. The `Async.run` handler creates a new structured concurrency scope where all the fibers are executed. The `Async.run` will wait for all the fibers to finish before returning the result of the effectful computation both if the fibers are joined or not.

```scala 3
import in.rcard.yaes.Async.*

def updateUser(user: User): Unit                = ???
def updateClicks(user: User, clicks: Int): Unit = ???

Async.run {
  val john = User("John")
  Async.fork {
    updateUser(john)
  }
  Async.fork {
    updateClicks(john, 10)
  }
}
```

The `Async.run` function will wait for both the `updateUser` and `updateClicks` functions to finish before returning. It's a powerful feature that allows for a structured way to manage the execution of asynchronous computations.

Another important feature of strutctured concurrency is the *cancellation* of the fibers. Canceling a fiber is possible by calling the `cancel` method on the `Fiber` instance. The following code snippet shows how:

```scala 3
import in.rcard.yaes.Async.*
import java.util.concurrent.ConcurrentLinkedQueue

val actualQueue = Async.run {
  val queue = new ConcurrentLinkedQueue[String]()
  val cancellable = Async.fork {
    Async.delay(2.seconds)
    queue.add("cancellable")
  }
  val fb = Async.fork {
    Async.delay(500.millis)
    `cancellable.cancel()`
    queue.add("fb2")
  }
  cancellable.join()
  queue
}
```

Cancellation is collaborative. In the above example, the fiber `cancellable` is marked for cancellation by the call `cancellable.cancel()`. However, the fiber is not immediately canceled. The fiber is canceled when it reaches the first operation that can be interrupted by the JVM. Hence, cancellation is based on the concept of interruption. In the above example, the `cancellable` is canceled when it reaches the `delay(2.seconds)` operation. The fiber will never be canceled if we remove the delay operation. A similar behavior is implemented by Kotlin coroutines (see [Kotlin Coroutines - A Comprehensive Introduction / Cancellation](https://rockthejvm.com/articles/kotlin-101-coroutines#cancellation) for further details).

Cancelling a fiber follows the relationship between parent and child jobs. If a parent's fiber is canceled, all the children's fibers are canceled as well:

```scala 3
import in.rcard.yaes.Async.*
import java.util.concurrent.ConcurrentLinkedQueue

val actualQueue = Async.run {
  val queue = new ConcurrentLinkedQueue[String]()
  val fb1 = Async.fork("fb1") {
    Async.fork("inner-fb") {
      Async.fork("inner-inner-fb") {
        Async.delay(6.seconds)
        queue.add("inner-inner-fb")
      }

      Async.delay(5.seconds)
      queue.add("innerfb")
    }
    Async.delay(1.second)
    queue.add("fb1")
  }
  Async.fork("fb2") {
    Async.delay(500.millis)
    fb1.cancel()
    queue.add("fb2")
  }
  queue
}
```

Trying to get the value from a canceled fiber will raise a `Cancelled` error. However, joining a canceled fiber will not raise any error.

#### Structured Concurrency Primitives

Using the `Async.fork` DSL is quite low-level. The library provides a set of structured concurrency primitives that can be used to define more complex asynchronous computations. The available primitives are:

- `Async.par`: Runs two asynchronous computations in parallel and returns both .
- `Async.race`: Runs two asynchronous computations in parallel and returns the result of the first computation that finishes. The other one is canceled.
- `Async.racePair`: Runs two asynchronous computations in parallel and returns the result of the first computation that finishes along with the fiber that is still running.

### The `Raise` Effect

The `Raise[E]` type describes the possibility that a function can raise an error of type `E`. `E` can be a logic typed error or an exception. The DSL is heavinly inspired by the [`raise4s`](https://github.com/rcardin/raise4s) library.

Let's see an example:

```scala 3
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int)(using Raise[ArithmeticException]): Int =
  if (b == 0) Raise.raise(new ArithmeticException("Division by zero"))
  else a / b
```

In the above example, the `divide` function can raise an `ArithmeticException` if the second parameter is zero. In the example, we used an exception as the error type. However, we can use any type as the error type: 

```scala 3
import in.rcard.yaes.Raise.*

object DivisionByZero
type DivisionByZero = DivisionByZero.type

def divide(a: Int, b: Int)(using Raise[DivisionByZero]): Int =
  if (b == 0) Raise.raise(DivisionByZero)
  else a / b
```

The effect offers some functions to lift an program into an effectful computation that uses the `Raise[E]` effect. For example, we can rewrite the above example using the `ensure` utility function:

```scala 3
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int)(using Raise[DivisionByZero]): Int =
  Raise.ensure(b != 0) { DivisionByZero }
  a / b
```

If we know that a function can throw an exception, we can catch it and trasform it into an error of type `E` with the `catching` function:

```scala 3
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int)(using Raise[DivisionByZero]): Int =
  Raise.catching[ArithmeticException] {
    a / b
  } { _ => DivisionByZero }
```

The effect defines many handlers to deal with the raised errors. For example, we can execute the effectful computation and handle the raised error as a union type:

```scala 3
import in.rcard.yaes.Raise.*

val divisionByZeroResult: Int | DivisionByZero = Raise.run {
    divide(10, 0)
  }
```

Alternatively, we can handle the raised error transforming it into an `Either` type:

```scala 3
import in.rcard.yaes.Raise.*

val divisionByZeroResult: Either[DivisionByZero, Int] = Raise.either {
  divide(10, 0)
}
```

If we're not interested in propagating the exact reason of error, we can use the `option` handler:

```scala 3
import in.rcard.yaes.Raise.*

val divisionByZeroResult: Option[Int] = Raise.option {
  divide(10, 0)
}
```

We can even ignore the raised error returning a `Null` value:

```scala 3
import in.rcard.yaes.Raise.*

val divisionByZeroResult: Int | Null = Raise.nullable {
  divide(10, 0)
}
```

#### Error Mapping with `MapError`

The `Raise` effect provides a powerful `MapError` strategy that allows you to automatically map errors from one type to another using a `given` instance. This is particularly useful when you need to transform errors in a compositional way across different layers of your application.

```scala 3
import in.rcard.yaes.Raise.*

// Define different error types for different layers
sealed trait DatabaseError
case object ConnectionTimeout extends DatabaseError
case object RecordNotFound extends DatabaseError

sealed trait ServiceError
case class ValidationFailed(message: String) extends ServiceError
case class OperationFailed(cause: String) extends ServiceError

// A function that raises DatabaseError
def findUserInDatabase(id: Int)(using Raise[DatabaseError]): User =
  if (id < 0) Raise.raise(RecordNotFound)
  else User(s"User$id")

// Use MapError to automatically transform DatabaseError to ServiceError
def findUser(id: Int)(using Raise[ServiceError]): User = {
  // Define the mapping strategy as a given instance
  given MapError[DatabaseError, ServiceError] = MapError {
    case ConnectionTimeout => OperationFailed("Database unavailable")
    case RecordNotFound => ValidationFailed("User not found")
  }
  
  // The error will be automatically mapped from DatabaseError to ServiceError
  findUserInDatabase(id)
}

// Usage
val result: ServiceError | User = Raise.run {
  findUser(-1)
}
// result will be ValidationFailed("User not found")
```

The `MapError` strategy is particularly useful when working with layered architectures where different layers define their own error types, allowing for clean separation of concerns while maintaining composability.

#### Error Accumulation

The `Raise` effect allows you to accumulate multiple errors instead of short-circuiting on the first one using `accumulate` and `accumulating`:

```scala 3
import in.rcard.yaes.Raise.*

def validateName(name: String)(using Raise[String]): String =
  if (name.nonEmpty) name else Raise.raise("Name cannot be empty")

def validateAge(age: Int)(using Raise[String]): Int =
  if (age >= 0) age else Raise.raise("Age cannot be negative")

val result = Raise.either {
  Raise.accumulate {
    val name = accumulating { validateName("") }
    val age = accumulating { validateAge(-1) }
    (name, age)
  }
}
// result will be Left(List("Name cannot be empty", "Age cannot be negative"))
```

#### Error Tracing

The `traced` function adds debugging capabilities by capturing stack traces when errors occur:

```scala 3
import in.rcard.yaes.Raise.*

// Define custom tracing behavior
given TraceWith[String] = trace => {
  println(s"Error: ${trace.original}")
  trace.printStackTrace()
}

def riskyOperation(value: Int)(using Raise[String]): Int =
  if (value < 0) Raise.raise("Negative value not allowed")
  else value * 2

val result = Raise.either {
  traced {
    riskyOperation(-5)
  }
}
// Prints error details and stack trace, then returns Left("Negative value not allowed")
```

You can also use the default tracing strategy:

```scala 3
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Raise.given  // Import default tracing

val result = Raise.either {
  traced {
    Raise.raise("Something went wrong")
  }
}
// Automatically prints stack trace
```

**Note**: Tracing has performance implications since it creates full stack traces.

### The `Resource` Effect

The `Resource` effect provides automatic resource management with guaranteed cleanup. It ensures that all acquired resources are properly released in LIFO (Last In, First Out) order, even when exceptions occur. This is particularly useful for managing files, database connections, network connections, and other resources that need explicit cleanup.

```scala 3
import in.rcard.yaes.Resource.*
import java.io.{FileInputStream, FileOutputStream}

def copyFile(source: String, target: String)(using Resource): Unit = {
  val input = Resource.acquire(new FileInputStream(source))
  val output = Resource.acquire(new FileOutputStream(target))
  
  // Copy file contents
  val buffer = new Array[Byte](1024)
  var bytesRead = input.read(buffer)
  while (bytesRead != -1) {
    output.write(buffer, 0, bytesRead)
    bytesRead = input.read(buffer)
  }
}
```

The `Resource` effect provides several methods for resource management:

- `Resource.acquire`: For resources that implement `Closeable`, automatically calling `close()` when the scope ends
- `Resource.install`: For custom resource management with explicit acquisition and release functions
- `Resource.ensuring`: For registering cleanup actions that don't involve specific resources

Here's an example using custom resource management:

```scala 3
import in.rcard.yaes.Resource.*

def processWithConnection()(using Resource): String = {
  val connection = Resource.install(openDatabaseConnection()) { conn =>
    conn.close()
    println("Database connection closed")
  }
  
  Resource.ensuring {
    println("Processing completed")
  }
  
  // Use connection safely
  connection.executeQuery("SELECT * FROM users")
}
```

To execute resource-managed code, use the `Resource.run` handler:

```scala 3
import in.rcard.yaes.Resource.*

val result = Resource.run {
  copyFile("source.txt", "target.txt")
  processWithConnection()
}
// All resources are automatically cleaned up here, even if exceptions occurred
```

The `Resource` effect guarantees that:
- Resources are cleaned up in reverse order of acquisition (LIFO)
- Cleanup occurs even if exceptions are thrown
- Resource cleanup exceptions are handled appropriately
- Original exceptions from the main program are preserved

### The `Input` Effect

Every time we need to read input from the console, we can use the `Input` effect. The `Input` effect provides a set of operations to read input from the console. Since the project is still in an experimental stage, the only one developed operation is the `readLn` function that reads a line from the console:

```scala 3
import in.rcard.yaes.Input.*
import in.rcard.yaes.Raise.*
import java.io.IOException

val name: (Input, Raise[IOException]) ?=> String = Input.readLn()
```

The effect uses the Scala `scala.io.StdIn` object under the hood, which uses the Java `System.in` object to read input from the console. Reading from the console can result in an `IOException`, so the `Input` effect requires a `Raise[IOException]` effect.

To run the effectful computation, we can use the provided handlers, which returns the read line:

```scala 3
import in.rcard.yaes.Input.*
import in.rcard.yaes.Raise.*
import java.io.IOException

val result: String | Null = Raise.nullable {
  Input.run {
    name
  }
}
```

In the above example, we decided to ignore the `IOException` error and return a `Null` value if an error occurs.

### The `Output` Effect

The `Output` effect provides a set of operations to print output to the console. Is uses the `scala.Console` object under the hood.

```scala 3
import in.rcard.yaes.Output.*

val program: Output ?=> Unit = Output.printLn("Hello, world!")
```

As we can see, outputting to the console doesn't raise any error. The behavior mimics exactly the one exposed by the `scala.Console`, which silently ignores any error that can occur during the output operation.

To run the effectful computation, we can use the provided handlers:

```scala 3
import in.rcard.yaes.Output.*

// Prints "Hello, world!" to the console
Output.run {
  program
}
```

In a similar way, we can output to system err using the `printErr` function:

```scala 3
import in.rcard.yaes.Output.*

val program: Output ?=> Unit = Output.printErr("Hello, world!")
```

### The `Random` Effect

The `Random` effect provides a set of operations to generate random content. If we need to generate non-deterministic content, we can use it. Under the hood, the effect uses the `scala.util.Random` object. As we saw in the introduction, we can use the `Random` effect to define a function that generates a random boolean:

```scala 3
import in.rcard.yaes.Random.*

def flipCoin(using Random): Boolean = Random.nextBoolean
```

The other random content we can generate is:

- `nextInt`: Generates a random integer.
- `nextDouble`: Generates a random double.
- `nextLong`: Generates a random long.

As usual, we can run the effectful computation using the provided handlers:

```scala 3
import in.rcard.yaes.Random.*

val result: Boolean = Random.run {
  flipCoin
}
```

### The `Clock` Effect

The `Clock` effect provides a set of operations to manage time effectfully. It's possible to get the current time, even in a monotonic way. The `Clock.now` function returns a `java.time.Instant`, while the `Clock.nowMonotonic` returns a strictly monotonically increasing time value, guaranteed to always move forward. Returns a `Duration` rather than an Instant because monotonic time represents the time elapsed since some arbitrary starting point, not a specific point in calendar time.

Both functions use the `java.time` package under the hood.

```scala 3
import in.rcard.yaes.Clock.*
import in.rcard.yaes.Output.*

val program = Output.run {
  Clock.run {
    val now = Clock.now()
    val nowMonotonic = Clock.nowMonotonic()
    Output.printLn(s"Now: $now")
    Output.printLn(s"Now monotonic: $nowMonotonic")
  }
}
```

### The `System` Effect

The `System` effect provides a set of operations to manage system properties and environment variables. It allows for reading system properties and environment variables in a type-safe way.

Use the `System.env` function to read an environment variable and eventually use a default value if the variable is not set:

```scala 3
import in.rcard.yaes.System.*
import in.rcard.yaes.Raise.*

val port: (System, Raise[NumberFormatException]) ?=> Option[Int] = System.env[Int]("PORT")
val host: System ?=> String = System.env[String]("HOST", "localhost")
```

The same applies to system properties. Use the `System.property` function to read a system property and eventually use a default value if the property is not set:

```scala 3
import in.rcard.yaes.System.*
import in.rcard.yaes.Raise.*

val port: (System, Raise[NumberFormatException]) ?=> Option[Int] = System.property[Int]("server.port")
val host: System ?=> String = System.property[String]("server.host", "localhost")
```

The available types for properties and environment variables are:

- `String`: A string value.
- `Int`: An integer value.
- `Long`: A long value.
- `Double`: A double value.
- `Boolean`: A boolean value.
- `Float`: A float value.
- `Short`: A short value.
- `Byte`: A byte value.
- `Char`: A char value.

### The `Log` Effect

The `Log` effect provides the capability to log messages at different levels. The available levels are:
  - `TRACE`
  - `DEBUG`
  - `INFO`
  - `WARN`
  - `ERROR`
  - `FATAL`
  
We can log using a concrete implementation of the `in.rcard.yaes.Logger` interface. Each logger instance has a name and a log level associated with it. To create a logger, we can use the `Log.getLogger` method:

```scala 3
import in.rcard.yaes.Log.*

val logger: Log ?=> Logger = Log.getLogger("TestLogger", Log.Level.Trace)
```

It's possible to create a new logger providing only the name. In this case, the logger will use the default log level, which is `Log.Level.Debug`.

The only logger implementation available is the `ConsoleLogger`, which logs messages to the console. The message printed to the console has the following format:

```
2025-04-22T19:55:59 - TRACE - TestLogger - Trace message
```

To run the effectful computation, we can use the provided handlers:

```scala 3
import in.rcard.yaes.Log.*

val program = Log.run {
  val logger = Log.getLogger("TestLogger", Log.Level.Trace)

  logger.info("Info message")
}
```

It's possible to change the clock used by the logger. By default, the `java.time.Clock.systemDefaultZone()` is used. The clock is provided as a given parameter to the `Log.run` handler method. The default clock is defined as a given instance in the `Log` object.

```scala 3
object Log {
  given defaultClock: java.time.Clock = java.time.Clock.systemDefaultZone()
  // ...
}
```

## Contributing

If you want to contribute to the project, please do it 🙏! Any help is welcome.

## Acknowledgments

Many smart engineers helped me with thei ideas and suggestions. I want to thank them all. In particular, I want to thank:

- [Daniel Ciocîrlan](https://rockthejvm.com/): He's the first that saw something in me and gave me the opportunity to work with him. He's a great mentor and a great friend.
- [Simon Vergauwen](https://github.com/nomisRev): He's a great engineer. Now, he's focused on Kotlin and the Arrow Kt library, which drove many of the ideas behind the λÆS library.
- [Jon Pretty](https://github.com/propensive): We shared some great ideas about the [Raise] effect. I love the way he thinks about programming.
- [Noel Welsh](https://noelwelsh.com/): We chat about the `Raise` effect and the way to handle errors in a functional way. He's a great engineer and a great person.
- [Flavio Brasil](https://github.com/fwbrasil): He creates the Kyo library, which is a great inspiration for the λÆS library. He helped me a lot with good suggestions and ideas.

Thanks guys! 🙏

## References

It follows some quotations and links to valuable resources to understand the concepts behind the library:

1. [Introduction to Abilities: A Mental Model - What do we mean by effects](https://www.unison-lang.org/docs/fundamentals/abilities/#what-do-we-mean-by-effects):
   > […] You might think of an effectful computation as one which performs an action outside of its local scope compared to one which simply returns a calculable value. […] So when functional programmers talk about managing effects, they're talking about expressing the basic logic of their programs within some guard rails provided by data structures or programming language constructs.

2. [Abilities, not monads](https://softwaremill.com/trying-out-unison-part-3-effects-through-abilities/)
   > […] Unison offers abilities, which are an implementation of algebraic effects. An ability is a property of a function (it's not part of the value's type!).
   
3. [Abilities for the monadically inclined](https://www.unison-lang.org/docs/fundamentals/abilities/for-monadically-inclined/)

4. [Effect Oriented Programming, by Bill Frasure, Bruce Eckel, James Ward](https://effectorientedprogramming.com/)
   > An Effect is an unpredictable interaction, usually with an external system. […] An Effect System manages Effects by wrapping these calls. […] Unpredictable elements are Side Effects. […] A Side Effect occurs when calling a function changes the context of that function. […] There’s an important difference: Side Effects are unmanaged and Effects are managed. A Side Effect “just happens” but an Effect is explicitly tracked and controlled. […] With an Effect System, we manage Effect behavior by putting that Effect in a kind of box. […] An Effect System provides a set of components that replace Side-Effecting functions in standard libraries, along with the structure for managing Effectful functions that you write. An Effect System enables us to add almost any functionality to a program. […] Managing an Effect means we not only control what results are produced by a function like `nextInt()`, but also when those results are produced. The control of when is called deferred execution. Deferred execution is part of the solution for easily attaching functionality to an existing program. […] Deferring the execution of an Effect is part of what enables us to add functionality to that Effect. […] If Effects ran immediately, we could not freely add behaviors. […] When we manage an Effect, we hold a value that represents something that can be run but hasn’t yet.
   
5. [An Introduction to Algebraic Effects and Handlers](https://www.eff-lang.org/handlers-tutorial.pdf)
   > The idea behind it is that operation calls do not perform actual effects (e.g. printing to an output device), but behave as signals that propagate outwards until they reach a handler with a matching clause

6. [CanThrow Capabilities](https://docs.scala-lang.org/scala3/reference/experimental/canthrow.html)

7. [Essential Effects, by Adam Rosien](https://essentialeffects.dev/)
   > we’ll distinguish two aspects of code: computing values and interacting with the environment. At the same time, we’ll talk about how transparent, or not, our code can be in describing these aspects. […] To understand what plusOne does, you don’t have to look anywhere except the (literal) definition of plusOne. There are no references to anything outside of it. This is sometimes referred to as local reasoning. Under substitution, programs mean the same thing if they evaluate to the same value. 13 + 1 means exactly the same thing as 14. So does plusOne(12 + 1), or even (12 + 1) + 1. This is known as referential transparency. […] If we impose some conditions, we can tame the side effects into something safer; we’ll call these effects. […] The type of the program should tell us what kind of effects the program will perform, in addition to the type of the value it will produce. If the behavior we want relies upon some externally-visible side effect, we separate describing the effects we want to happen from actually making them happen. We can freely substitute the description of effects until the point we run them. […] We delay the side effect so it executes outside of any evaluation, ensuring substitution still holds within. We’ll call these conditions the Effect Pattern. […] We can construct individual effects, and run them, but how do we combine them? We may want to modify the output of an effect (via map), or use the output of an effect to create a new effect (via flatMap). But be careful! Composing effects must not execute them.

8. [Koka Language - 3.4. Effect Handlers](https://koka-lang.github.io/koka/doc/book.html#sec-handlers)
   > Effect handlers are a novel way to define control-flow abstractions and dynamic binding as user defined handlers – no need anymore to add special compiler extensions for exceptions, iterators, async-await, probabilistic programming, etc. Moreover, these handlers can be composed freely so the interaction between, say, async-await and exceptions are well-defined.

9. [Algebraic Effects from Scratch by Kit Langton](https://www.youtube.com/watch?v=qPvPdRbTF-E&t=763s)

10. [Effekt: Capability-passing style for type- and effect-safe, extensible effect handlers in Scala](https://www.cambridge.org/core/journals/journal-of-functional-programming/article/effekt-capabilitypassing-style-for-type-and-effectsafe-extensible-effect-handlers-in-scala/A19680B18FB74AD95F8D83BC4B097D4F)

11. [Object-capability model](https://en.wikipedia.org/wiki/Object-capability_model)
