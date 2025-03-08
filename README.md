![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/rcardin/yaes/scala.yml?branch=main)

# Yet Another Effect System (yæs)

YÆS is an experimental effect system in Scala based upon Capabilities and Algebraic Effects. Using Scala 3 [context parameters](https://docs.scala-lang.org/scala3/reference/contextual/using-clauses.html) and [context functions](https://docs.scala-lang.org/scala3/reference/contextual/context-functions.html), it provides a way to define and handle effects in a modular and composable manner. 

What's new in YÆS when compared to other effect systems? Well, you can choose to use a monadic style like the following:

```scala 3
import in.rcard.yaes.Random.Random
import in.rcard.yaes.Raise.Raise
import in.rcard.yaes.Yaes.*

def drunkFlip(using Random, Raise[String]): String = for {
  caught <- Random.nextBoolean
  heads  <- if (caught) Random.nextBoolean else Raise.raise("We dropped the coin")
} yield if (heads) "Heads" else "Tails"
```

Or a more direct style like this:

```scala 3
import in.rcard.yaes.Raise.Raise
import in.rcard.yaes.Random.Random

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

In YÆS types like `Random` and `Raise` are capabilities, or *Effects*. An *Effect* is an unpredictable interaction, usually with an external system. An Effect System manages *Effects* by wrapping them and providing a set of components that replace effectful functions in standard libraries. We manage Effect behavior by putting that Effect in a kind of box. 

Calling the above `drunkFlip` function will not execute the effects. Instead, it will return a value that represents something that can be run but hasn’t yet. This is called deferred execution. It's kinda different than _direct-style_ approach. We call it **Capability-Passing Style**. As you might image, *Effect* and *Capabilities* are quite sinonyms in the YÆS context. 

An Effect System provides all the tools to manage and execute Effectful computations in a deferred manner. In YÆS, such tools are called *Handlers*.

```scala 3
import in.rcard.yaes.Random.Random
import in.rcard.yaes.Raise.Raise

val result: String = Raise.run { 
  Random.run { 
    drunkFlip
  }
}
```

In the above code, we are running the `drunkFlip` function with the `Random` and `Raise` capabilities. The `Raise.run` and `Random.run` functions are defined using *Handlers* that will execute the deferred effects. The approach remids the one defined in the Algebraic Effects and Handlers. theory.

## Dependency

The library is available on Maven Central. To use it, add the following dependency to your build.sbt files:

```sbt
libraryDependencies += "in.rcard.yaes" %% "yaes-core" % "0.0.1"
```

The library is only available for Scala 3.

## Usage

The library provides a set of effects that can be used to define and handle effectful computations. The available effects are:

- [`IO`](#the-io-effect): Allows for running side-effecting operations.
- [`Async`](#the-async-effect): Allows for asynchronous computations and fiber management.
- `Raise`: Allows for raising and handling errors.
- `Input`: Allows for reading input from the console.
- `Output`: Allows for printing output to the console.
- `Random`: Allows for generating random numbers.

Each effect provides a not-comprehensive set of operations that can be used to define effectful computations. The operations are defined directly on the companion object of the effect. For example, here is the set of functions available on the `Random` effect:

```scala 3
object Random {
  def nextInt(using r: Random): Int
  def nextBoolean(using r: Random): Boolean
  def nextDouble(using r: Random): Double
  def nextLong(using r: Random): Long
}
```

Each effect also defines an `Unsafe` trait that provides the implementation of the effect in an unsafe manner. The `Unsafe` trait is always part of the effect companion object:

```scala 3
object Random {
  trait Unsafe {
    def nextInt: Int
    def nextBoolean: Boolean
    def nextDouble: Double
    def nextLong: Long
  }
}
```

The `Unsafe` trait is used to define the *Handlers* that will execute the deferred effects. It's not intended to be used directly by the user since is does not provide any kind of safety (referential transparency, for example). The `Handler` trait is in the `Yeas` object:

```scala 3
trait Handler[F, A, B] {
  def handle(program: Yaes[F] ?=> A): B // FIXME Make it inline
}
```

Each handler handles an effectful program that requires a capability of type `Yeas[F]`. The `Yaes` type is a class wrapping an instance of capability (or effect) of type `F`:

```scala 3
class Yaes[F](val unsafe: F)
```

It's the core of the library since it allows the definition of the `flatMap` and `map` functions on the `Yaes[F] ?=> A` type.

Every effect companion objects defines a type alias for the capability of the effect:

```scala 3
object Random {
  type Random = Yaes[Random]
}
```

Handlers are used to define the `run` method of each effect, which is the method that will execute the deferred effects:

```scala 3
object Random {
  def run[A](block: Random ?=> A): A = {
    val handler = new Yaes.Handler[Random.Unsafe, A, A] {
      override def handle(program: Random ?=> A): A = program(using
        new Yaes(new Random.Unsafe {
          override def nextInt(): Int         = scala.util.Random.nextInt()
          override def nextLong(): Long       = scala.util.Random.nextLong()
          override def nextBoolean(): Boolean = scala.util.Random.nextBoolean()
          override def nextDouble(): Double   = scala.util.Random.nextDouble()
        })
      )
    }
    Yaes.handle(block)(using handler)
  }
}
```

It follows the list of effects and for each of them a brief description of their operations.

## Effects (or Capabilities)

### The `IO` Effect

The `IO` effect allows for running side-effecting operations:

```scala 3
import in.rcard.yaes.IO.IO

case class User(name: String)

def saveUser(user: User)(using IO): Long =
  throw new RuntimeException("Read timed out")
```

The above code can throw an uncontrolled exception if the connection with the database times out. The generic `IO` effect lift the function in the world of the effectful computations, making it referentially transparent. It means that everything that is not referentially transparent should be defined using the `IO` effect. In fact, the `IO` effect provides a guard rail to uncontrolled exceptions since its handler returns always a monad that wraps the result of the effectful computation.


To run the effectful computation, we can use the provided handlers.

The first handler doesn't block the current thread:

```scala 3
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val result: Future[Long] = IO.run {
  saveUser(User("John"))
}
```

The library also provides a blocking handler that will block the current thread until the effectful computation is finished:

```scala 3
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
import in.rcard.yaes.Async.Async

def findUserByName(name: String): Option[User] = Some(User(name))
val fb: Async ?=> Fiber[Option[User]] = Async.fork { findUserByName("John") }
```

The `fb` variable represent a fiber (lightweight thread) that is executing the `findUserByName` function. The `fork` operation returns a `Fiber` object that can be used to manage the execution of the asynchronous computation. In details, we can wait for the value of the computation using the `value` operation:

```scala 3
import in.rcard.yaes.Raise.Raise
import in.rcard.yaes.Async.Async
import in.rcard.yaes.Async.Cancelled

val maybeUser: (Async, Raise[Cancelled]) ?=> Option[User] = fb.value
```

Or, we can just wait for the computation to finish:

```scala 3
val p: Async ?=> Option[User] = fb.join()
```

As for the `IO` effect, forking a new fiber or joining it doesn't execute the effectful computation. It just returns a value that represents the computation that can be run but hasn't yet.

Again, we can run the effectful computation using the provided handlers:

```scala 3
import in.rcard.yaes.Async.Async

val maybeUser: Raise[Cancelled] ?=> Option[User] = Async.run {
    val fb: Async ?=> Fiber[Option[User]] = Async.fork { findUserByName("John") }
    fb.value
  }
```

The above code shows another important aspect of the YÆS library. We can handle an effect eliminating it from the needed capabilities one at time. In the above code, we are handling the `Async` effect first, and we remain with the `Raise` effect. It's a powerful feature that allows for a fine-grained management of the effects.

The `Async` effect is transparent to possible exceptions thrown by the effectful computation. Please, add the `IO` effect if you think the effectful computation can throw any exception.

#### Structured Concurrency

The `Async` effect implements **structured concurrency**. The `Async.run` handler creates a new structured concurrency scope where all the fibers are executed. The `Async.run` will wait for all the fibers to finish before returning the result of the effectful computation both if the fibers are joined or not.

```scala 3
import in.rcard.yaes.Async.Async

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
import in.rcard.yaes.Async.Async
import in.rcard.yaes.Async.Cancelled
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
import in.rcard.yaes.Async.Async
import in.rcard.yaes.Async.Cancelled
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

### Input

The Input effect allows for reading input from the console.

Example:

```scala 3
import in.rcard.yaes.{Input, Raise}
import java.io.IOException

val result = Raise.run {
  Input.run {
    Input.readLn()
  }
}

println(result) // Output: (depends on user input)
```

### IO

The IO effect allows for running side-effecting operations.

Example:
```scala 3
import in.rcard.yaes.IO

val result = IO.run {
  val fortyTwo: IO ?=> Int = IO {
    42
  }
  fortyTwo + 1
}

println(result) // Output: 43
```

### Output

The Output effect allows for printing output to the console.

Example:
```scala 3
import in.rcard.yaes.Output

Output.run {
  Output.printLn("Hello, World!")
}
```

### Random

The Random effect allows for generating random numbers.

Example:
```scala 3
import in.rcard.yaes.Random

val result = Random.run {
  Random.nextInt
}

println(result) // Output: (random integer)
```

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