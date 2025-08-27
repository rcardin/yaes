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

## Best Practices

- Use specific error types rather than generic exceptions
- Combine with other effects like `IO` for comprehensive error handling
- Handle errors at appropriate boundaries in your application
- Use union types for simple error handling, `Either` for more complex scenarios
