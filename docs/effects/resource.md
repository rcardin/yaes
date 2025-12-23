
# Resource Effect

The `Resource` effect provides automatic resource management with guaranteed cleanup. It ensures that all acquired resources are properly released in LIFO (Last In, First Out) order, even when exceptions occur.

## Overview

The `Resource` effect is essential for managing files, database connections, network connections, and other resources that need explicit cleanup.

## Basic Usage

### Auto-Closeable Resources

For resources implementing `Closeable`:

```scala
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
  // Resources automatically closed here
}
```

## Resource Management Methods

### Custom Resource Management

Use `install` for custom acquisition and release:

```scala
import in.rcard.yaes.Resource.*

def processWithConnection()(using Resource): String = {
  val connection = Resource.install(openDatabaseConnection()) { conn =>
    conn.close()
    println("Database connection closed")
  }
  
  // Use connection safely
  connection.executeQuery("SELECT * FROM users")
}
```

### Cleanup Actions

Register cleanup actions with `ensuring`:

```scala
import in.rcard.yaes.Resource.*

def processData()(using Resource): Unit = {
  Resource.ensuring {
    println("Processing completed")
  }
  
  Resource.ensuring {
    println("Cleanup temporary files")
  }
  
  // Main processing logic
  // Cleanup actions run in reverse order
}
```

## Error Handling

Resources are cleaned up even when exceptions occur:

```scala
import in.rcard.yaes.Resource.*
import in.rcard.yaes.Raise.*

def riskyOperation()(using Resource, Raise[String]): String = {
  val resource = Resource.acquire(new FileInputStream("data.txt"))
  
  // This might fail
  if (Math.random() > 0.5) {
    Raise.raise("Random failure!")
  }
  
  "Success"
  // File is closed even if error is raised
}
```

## Running Resource-Managed Code

Use the `Resource.run` handler:

```scala
import in.rcard.yaes.Resource.*

val result = Resource.run {
  copyFile("source.txt", "target.txt")
  processWithConnection()
}
// All resources automatically cleaned up here
```

## Nested Resources

Resources can be nested and are cleaned up in LIFO order:

```scala
import in.rcard.yaes.Resource.*

def nestedResourceExample()(using Resource): Unit = {
  val outer = Resource.acquire(new FileInputStream("outer.txt"))
  println("Outer resource acquired")
  
  Resource.ensuring { println("Outer cleanup") }
  
  val inner = Resource.acquire(new FileInputStream("inner.txt"))
  println("Inner resource acquired")
  
  Resource.ensuring { println("Inner cleanup") }
  
  // Use both resources
}

Resource.run { nestedResourceExample() }
// Output:
// Outer resource acquired
// Inner resource acquired
// Inner cleanup
// Outer cleanup (LIFO order)
```

## Advanced Example

Combining multiple resource types:

```scala
import in.rcard.yaes.Resource.*
import java.io.*
import java.net.*

def downloadAndProcess(url: String, outputFile: String)(using Resource): Unit = {
  // Network connection
  val connection = Resource.install(new URL(url).openConnection()) { conn =>
    conn.asInstanceOf[HttpURLConnection].disconnect()
  }
  
  // Input stream
  val input = Resource.acquire(connection.getInputStream())
  
  // Output stream
  val output = Resource.acquire(new FileOutputStream(outputFile))
  
  // Cleanup notification
  Resource.ensuring {
    println(s"Download of $url completed")
  }
  
  // Transfer data
  input.transferTo(output)
}
```

## Key Features

- **LIFO Cleanup**: Resources cleaned up in reverse order of acquisition
- **Exception Safety**: Cleanup occurs even when exceptions are thrown  
- **Composable**: Can be combined with other effects
- **Flexible**: Supports both auto-closeable and custom resources
- **Reliable**: Guarantees cleanup execution
