---
layout: default
title: "Raise Effect"
---

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

Ignore error details and get `Option`:

```scala
import in.rcard.yaes.Raise.*

val result: Option[Int] = Raise.option {
  divide(10, 0)
}
```

### Nullable Handler

Get nullable results:

```scala
import in.rcard.yaes.Raise.*

val result: Int | Null = Raise.nullable {
  divide(10, 0)
}
```

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

## Error Mapping Strategy

The `MapError` strategy provides automatic error transformation using `given` instances. This allows for compositional error handling across different application layers:

```scala
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

// Usage example
val result: ServiceError | User = Raise.run {
  findUser(-1)
}
// result will be ValidationFailed("User not found")
```

### Advantages of MapError

- **Composability**: Errors are automatically transformed without explicit handling
- **Layer separation**: Each layer can define its own error types
- **Type safety**: Compile-time guarantees that error mappings are complete
- **Flexibility**: Multiple mapping strategies can be defined and composed

## Extension Methods

The `Raise` effect provides convenient extension methods to lift common Scala types into the `Raise` context:

### Either.value

Extract the value from an `Either`, raising the left value as an error if present:

```scala
import in.rcard.yaes.Raise.*

sealed trait ParseError
case class InvalidFormat(message: String) extends ParseError

def parseInt(input: String): Either[ParseError, Int] =
  try Right(input.toInt)
  catch case _: NumberFormatException => Left(InvalidFormat(s"'$input' is not a valid number"))

def processNumber(input: String)(using Raise[ParseError]): String = {
  val number = parseInt(input).value  // Extracts Int or raises ParseError
  s"The number is: $number"
}

// Usage example
val result = Raise.either {
  processNumber("abc")  // Will raise InvalidFormat error
}
// result will be Left(InvalidFormat("'abc' is not a valid number"))

val result2 = Raise.either {
  processNumber("42")   // Will extract the value 42
}
// result2 will be Right("The number is: 42")
```

### Option.value

Extract the value from an `Option`, raising `None` as an error if the option is empty:

```scala
import in.rcard.yaes.Raise.*

def findUser(id: Int): Option[User] =
  if (id > 0) Some(User(s"User$id")) else None

def getUserName(id: Int)(using Raise[None.type]): String = {
  val user = findUser(id).value  // Extracts User or raises None
  user.name
}

// Usage example
val result = Raise.either {
  getUserName(0)  // Will raise None
}
// result will be Left(None)

val result2 = Raise.either {
  getUserName(1)  // Will extract User(1)
}
// result2 will be Right("User1")
```

### Try.value

Extract the value from a `Try`, raising the contained `Throwable` as an error if the `Try` is a `Failure`:

```scala
import in.rcard.yaes.Raise.*
import scala.util.Try

def parseIntSafe(input: String): Try[Int] = Try(input.toInt)

def doubleNumber(input: String)(using Raise[Throwable]): Int = {
  val number = parseIntSafe(input).value  // Extracts Int or raises Throwable
  number * 2
}

// Usage example  
val result = Raise.either {
  doubleNumber("abc")  // Will raise NumberFormatException
}
// result will be Left(NumberFormatException)

val result2 = Raise.either {
  doubleNumber("21")   // Will extract 21 and double it
}
// result2 will be Right(42)
```

### Combining Extension Methods

These extension methods can be combined with other `Raise` operations for powerful error handling:

```scala
import in.rcard.yaes.Raise.*
import scala.util.Try

sealed trait ValidationError  
case class InvalidInput(message: String) extends ValidationError
case class ProcessingError(cause: Throwable) extends ValidationError

def processInputs(input1: String, input2: String)(using Raise[ValidationError]): Int = {
  // Transform Throwable to ValidationError using withError
  val num1 = Raise.withError[ValidationError, Throwable, Int](ProcessingError(_)) {
    Try(input1.toInt).value
  }
  
  // Use Either.value with custom error type  
  val parseResult = if (input2.nonEmpty) Right(input2.toInt) 
                   else Left(InvalidInput("Input cannot be empty"))
  
  val num2 = Raise.withError[ValidationError, InvalidInput, Int](identity) {
    parseResult.value
  }
  
  num1 + num2
}

// Usage example
val result = Raise.either {
  processInputs("10", "")  // Will raise InvalidInput
}
// result will be Left(InvalidInput("Input cannot be empty"))
```

## Best Practices

- Use specific error types rather than generic exceptions
- Combine with other effects like `IO` for comprehensive error handling
- Handle errors at appropriate boundaries in your application
- Use union types for simple error handling, `Either` for more complex scenarios
- Use extension methods to seamlessly integrate existing Scala types with the `Raise` effect
