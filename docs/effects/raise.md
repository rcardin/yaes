
# Raise Effect

The `Raise[E]` effect describes the possibility that a function can raise an error of type `E`. It provides typed error handling inspired by the [`raise4s`](https://github.com/rcardin/raise4s) library.

## Overview

The `Raise` effect allows you to define functions that can fail with specific error types, providing a functional approach to error handling without exceptions.

## Basic Usage

### With Exception Types

```scala
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int)(using Raise[ArithmeticException]): Int =
  if (b == 0) Raise.raise(new ArithmeticException("Division by zero"))
  else a / b
```

### With Custom Error Types

```scala
import in.rcard.yaes.Raise.*

object DivisionByZero
type DivisionByZero = DivisionByZero.type

def divide(a: Int, b: Int)(using Raise[DivisionByZero]): Int =
  if (b == 0) Raise.raise(DivisionByZero)
  else a / b
```

### Using the `raises` Infix Type

For more concise syntax, you can use the `raises` infix type instead of `using Raise[E]`:

```scala
import in.rcard.yaes.Raise.*

// Using the raises infix type
def divide(a: Int, b: Int): Int raises DivisionByZero =
  if (b == 0) Raise.raise(DivisionByZero)
  else a / b

// Equivalent to using Raise[E] explicitly
def divideExplicit(a: Int, b: Int)(using Raise[DivisionByZero]): Int =
  if (b == 0) Raise.raise(DivisionByZero) 
  else a / b

// Usage is the same
val result: Int | DivisionByZero = Raise.run {
  divide(10, 0)
}
```

## Utility Functions

### Ensuring Conditions

```scala
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int)(using Raise[DivisionByZero]): Int = {
  Raise.ensure(b != 0) { DivisionByZero }
  a / b
}
```

### Ensuring Non-Null Values

Ensure that a value is not null and raise an error if it is:

```scala
import in.rcard.yaes.Raise.*

object NullError
type NullError = NullError.type

def processName(name: String | Null)(using Raise[NullError]): String = {
  val validName = Raise.ensureNotNull(name) { NullError }
  validName.toUpperCase
}

// Usage example
val result = Raise.either {
  processName(null)
}
// result will be Left(NullError)

val result2 = Raise.either {
  processName("John")
}
// result2 will be Right("JOHN")
```

### Accumulating Errors

Use `accumulate` and `accumulating` to collect multiple errors instead of short-circuiting on the first one:

```scala
import in.rcard.yaes.Raise.*

def validateName(name: String)(using Raise[String]): String =
  if (name.nonEmpty) name else Raise.raise("Name cannot be empty")

def validateAge(age: Int)(using Raise[String]): Int =
  if (age >= 0) age else Raise.raise("Age cannot be negative")

// Accumulate validation errors
val result = Raise.either {
  Raise.accumulate {
    val name = accumulating { validateName("") }
    val age = accumulating { validateAge(-1) }
    (name, age)
  }
}
// result will be Left(List("Name cannot be empty", "Age cannot be negative"))
```

⚠️ **Important**: When using the `accumulate` function with lists or other collections, you **must** assign the result to a variable before returning it. Direct return of accumulated collections may not work correctly.

```scala
// ✅ CORRECT - Assign to variable first
val result = Raise.either {
  Raise.accumulate {
    val processedItems = List(1, 2, 3, 4, 5).map { i =>
      accumulating {
        if (i % 2 == 0) Raise.raise(i.toString)
        else i
      }
    }
    processedItems  // Return the assigned variable
  }
}

// ❌ INCORRECT - Direct return may not work
val result = Raise.either {
  Raise.accumulate {
    List(1, 2, 3, 4, 5).map { i =>
      accumulating {
        if (i % 2 == 0) Raise.raise(i.toString)
        else i
      }
    }  // Direct return without assignment
  }
}
```

The `mapAccumulating` function allows you to transform collections while accumulating any errors that occur during the transformation. This is particularly useful when you want to process all elements and collect all errors rather than stopping at the first failure.

```scala
import in.rcard.yaes.Raise.*

def validateNumber(n: Int)(using Raise[String]): Int =
  if (n > 0) n else Raise.raise(s"$n is not positive")

// Transform all elements, accumulating errors
val result = Raise.either {
  Raise.mapAccumulating(List(1, -2, 3, -4, 5)) { number =>
    validateNumber(number)
  }
}
// result will be Left(List("-2 is not positive", "-4 is not positive"))

// With all valid inputs
val successResult = Raise.either {
  Raise.mapAccumulating(List(1, 2, 3, 4, 5)) { number =>
    validateNumber(number)
  }
}
// successResult will be Right(List(1, 2, 3, 4, 5))
```

For more complex error types, you can provide a custom error combination function:

```scala
import in.rcard.yaes.Raise.*

case class ValidationErrors(errors: List[String])

def combineErrors(error1: ValidationErrors, error2: ValidationErrors): ValidationErrors =
  ValidationErrors(error1.errors ++ error2.errors)

def validateUserData(data: String)(using Raise[ValidationErrors]): String =
  if (data.isEmpty) Raise.raise(ValidationErrors(List("Data cannot be empty")))
  else if (data.length < 3) Raise.raise(ValidationErrors(List("Data too short")))
  else data

val result = Raise.either {
  Raise.mapAccumulating(List("Alice", "", "Bo", "Charlie"), combineErrors) { userData =>
    validateUserData(userData)
  }
}
// result will be Left(ValidationErrors(List("Data cannot be empty", "Data too short")))
```

#### Polymorphic Error Accumulation

The `accumulate` function is polymorphic and can collect errors into different collection types. This is useful when you want compile-time guarantees about non-empty error collections.

**Using NonEmptyList** (requires `yaes-cats`):

```scala
import in.rcard.yaes.{Raise, RaiseNel}  // RaiseNel = Raise[NonEmptyList[E]]
import in.rcard.yaes.Raise.accumulating
import in.rcard.yaes.instances.accumulate.given  // Import collector instances
import cats.data.NonEmptyList

def validatePositive(n: Int)(using Raise[String]): Int =
  if (n > 0) n else Raise.raise(s"$n is not positive")

val result: Either[NonEmptyList[String], (Int, Int)] = Raise.either {
  Raise.accumulate[NonEmptyList, String, (Int, Int)] {
    val a = accumulating { validatePositive(-1) }
    val b = accumulating { validatePositive(-2) }
    (a, b)
  }
}
// result: Left(NonEmptyList("-1 is not positive", List("-2 is not positive")))

// Using the RaiseNel type alias:
def validatePair(x: Int, y: Int): RaiseNel[String] ?=> (Int, Int) =
  Raise.accumulate[NonEmptyList, String, (Int, Int)] {
    val a = accumulating { validatePositive(x) }
    val b = accumulating { validatePositive(y) }
    (a, b)
  }
```

**Using NonEmptyChain** (requires `yaes-cats`):

```scala
import in.rcard.yaes.RaiseNec  // RaiseNec = Raise[NonEmptyChain[E]]
import cats.data.NonEmptyChain

val result: Either[NonEmptyChain[String], List[Int]] = Raise.either {
  Raise.accumulate[NonEmptyChain, String, List[Int]] {
    val numbers = List(1, -2, 3, -4, 5).map { n =>
      accumulating { validatePositive(n) }
    }
    numbers
  }
}
// result: Left(NonEmptyChain("-2 is not positive", "-4 is not positive"))

// Using the RaiseNec type alias:
def validateList(numbers: List[Int]): RaiseNec[String] ?=> List[Int] =
  Raise.accumulate[NonEmptyChain, String, List[Int]] {
    numbers.map { n =>
      accumulating { validatePositive(n) }
    }
  }
```

**Using List** (default, no extra imports needed):

```scala
val result: Either[List[String], (Int, Int)] = Raise.either {
  Raise.accumulate[List, String, (Int, Int)] {
    val a = accumulating { validatePositive(-1) }
    val b = accumulating { validatePositive(-2) }
    (a, b)
  }
}
// result: Left(List("-1 is not positive", "-2 is not positive"))
```

The type parameter `M[_]` specifies the error collection type. An `AccumulateCollector[M]` typeclass instance converts the internal error list to `M[Error]`:

- **`List`**: Built-in collector (always available)
- **`NonEmptyList`**: Requires `import in.rcard.yaes.instances.accumulate.given` from `yaes-cats`
- **`NonEmptyChain`**: Requires `import in.rcard.yaes.instances.accumulate.given` from `yaes-cats`

**Type Aliases:** The `yaes-cats` module provides convenient type aliases following Cats conventions:
- `RaiseNel[E]` = `Raise[NonEmptyList[E]]`
- `RaiseNec[E]` = `Raise[NonEmptyChain[E]]`

### Transforming Error Types

Transform errors from one type to another using `withError`:

```scala
import in.rcard.yaes.Raise.*

// Define different error types
sealed trait NetworkError
case object ConnectionTimeout extends NetworkError
case object InvalidResponse extends NetworkError

sealed trait ServiceError
case object ServiceUnavailable extends ServiceError
case object InvalidData extends ServiceError

// Function that raises NetworkError
def fetchData(url: String)(using Raise[NetworkError]): String =
  if (url.isEmpty) Raise.raise(InvalidResponse)
  else "data"

// Transform NetworkError to ServiceError
def processData(url: String)(using Raise[ServiceError]): String = {
  Raise.withError[ServiceError, NetworkError, String] {
    case ConnectionTimeout => ServiceUnavailable
    case InvalidResponse => InvalidData
  } {
    fetchData(url)
  }
}

// Usage example
val result = Raise.either {
  processData("")  // Will raise InvalidResponse, transformed to InvalidData
}
// result will be Left(InvalidData)
```

### Catching Exceptions

Transform exceptions into typed errors:

```scala
import in.rcard.yaes.Raise.*

def divide(a: Int, b: Int)(using Raise[DivisionByZero]): Int =
  Raise.catching[ArithmeticException] {
    a / b
  } { _ => DivisionByZero }
```

## Handlers

### Union Type Handler

Handle errors as union types:

```scala
import in.rcard.yaes.Raise.*

val result: Int | DivisionByZero = Raise.run {
  divide(10, 0)
}
```

### Either Handler

Transform errors into `Either` types:

```scala
import in.rcard.yaes.Raise.*

val result: Either[DivisionByZero, Int] = Raise.either {
  divide(10, 0)
}
```

### Option Handler

The `option` handler requires the block to raise `None` explicitly:

```scala
import in.rcard.yaes.Raise.*

def safeDivide(x: Int, y: Int)(using Raise[None.type]): Int =
  if (y == 0) then Raise.raise(None)
  else x / y

val result: Option[Int] = Raise.option {
  safeDivide(10, 0)
}
// result will be None
```

### Nullable Handler

The `nullable` handler requires the block to raise `null` explicitly:

```scala
import in.rcard.yaes.Raise.*

def safeDivide(x: Int, y: Int)(using Raise[Null]): Int =
  if (y == 0) then Raise.raise(null)
  else x / y

val result: Int | Null = Raise.nullable {
  safeDivide(10, 0)
}
// result will be null
```

## Error Tracing

The `traced` function adds tracing capabilities to error handling, capturing stack traces when errors occur. This is useful for debugging and logging error contexts:

```scala
import in.rcard.yaes.Raise.*

// Define a custom tracing strategy
given TraceWith[String] = trace => {
  println(s"Error occurred: ${trace.original}")
  trace.printStackTrace()
}

def riskyOperation(value: Int)(using Raise[String]): Int =
  if (value < 0) Raise.raise("Negative value not allowed")
  else value * 2

// Use traced to capture stack traces
val result = Raise.either {
  traced {
    riskyOperation(-5)
  }
}
// Prints error details and stack trace, then returns Left("Negative value not allowed")
```

### Default Tracing

A default tracing strategy is provided that simply prints the stack trace:

```scala
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Raise.given  // Import default tracing

val result = Raise.either {
  traced {
    Raise.raise("Something went wrong")
  }
}
// Automatically prints stack trace, then returns Left("Something went wrong")
```

### Custom Tracing Strategies

You can define custom tracing strategies for different error types:

```scala
import in.rcard.yaes.Raise.*

sealed trait AppError
case class DatabaseError(message: String) extends AppError
case class NetworkError(message: String) extends AppError

// Different tracing strategies for different error types
given TraceWith[DatabaseError] = trace => {
  // Log to database error system
  println(s"DB Error: ${trace.original.message}")
  trace.printStackTrace()
}

given TraceWith[NetworkError] = trace => {
  // Log to network monitoring system
  println(s"Network Error: ${trace.original.message}")
}

// Usage with specific error types
val dbResult = Raise.either {
  traced {
    Raise.raise(DatabaseError("Connection timeout"))
  }
}
```

**Note**: Tracing has performance implications since it creates full stack traces. Use it judiciously in production code.

## Error Composition

Combine multiple error types:

```scala
import in.rcard.yaes.Raise.*

sealed trait ValidationError
case object InvalidEmail extends ValidationError
case object InvalidAge extends ValidationError

def validateUser(email: String, age: Int)(using Raise[ValidationError]): User = {
  val validEmail = if (email.contains("@")) email 
                   else Raise.raise(InvalidEmail)
  val validAge = if (age >= 0) age 
                 else Raise.raise(InvalidAge)
  User(validEmail, validAge)
}
```

## Best Practices

- Use specific error types rather than generic exceptions
- Combine with other effects like `IO` for comprehensive error handling
- Handle errors at appropriate boundaries in your application
- Use union types for simple error handling, `Either` for more complex scenarios
