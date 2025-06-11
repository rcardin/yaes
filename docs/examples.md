---
layout: default
title: "Examples"
---

# Practical Examples

## Coin Flip Game

A complete example combining multiple effects:

```scala
import in.rcard.yaes.Random.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Input.*
import in.rcard.yaes.Raise.*

def coinFlipGame(using Random, Output, Input, Raise[String]): String = {
  Output.printLn("Welcome to the Coin Flip Game!")
  Output.printLn("Guess: heads or tails?")
  
  val guess = Input.readLn()
  val flip = if (Random.nextBoolean) "heads" else "tails"
  
  Output.printLn(s"The coin landed on: $flip")
  
  if (guess.toLowerCase == flip) {
    Output.printLn("You won!")
    "win"
  } else {
    Output.printLn("You lost!")
    "lose"
  }
}

// Run the game
val result = Raise.option {
  Output.run {
    Input.run {
      Random.run {
        coinFlipGame
      }
    }
  }
}
```

## File Processing with Resource Management

```scala
import in.rcard.yaes.Resource.*
import in.rcard.yaes.IO.*
import in.rcard.yaes.Raise.*
import java.io.{FileInputStream, FileOutputStream}

def processFiles(inputPath: String, outputPath: String)(using Resource, IO): Unit = {
  val input = Resource.acquire(new FileInputStream(inputPath))
  val output = Resource.acquire(new FileOutputStream(outputPath))
  
  Resource.ensuring {
    println("File processing completed")
  }
  
  // Process files...
  val buffer = new Array[Byte](1024)
  var bytesRead = input.read(buffer)
  while (bytesRead != -1) {
    output.write(buffer, 0, bytesRead)
    bytesRead = input.read(buffer)
  }
}

// Run with automatic cleanup
Resource.run {
  IO.run {
    processFiles("input.txt", "output.txt")
  }
}
```

## Concurrent Web Scraping

```scala
import in.rcard.yaes.Async.*
import in.rcard.yaes.IO.*
import in.rcard.yaes.Log.*

def fetchUrl(url: String)(using IO, Log): String = {
  val logger = Log.getLogger("WebScraper")
  logger.info(s"Fetching: $url")
  
  // Simulate HTTP request
  Thread.sleep(1000)
  s"Content from $url"
}

def scrapeUrls(urls: List[String])(using Async, IO, Log): List[String] = {
  val fibers = urls.map { url =>
    Async.fork(s"fetch-$url") {
      fetchUrl(url)
    }
  }
  
  fibers.map(_.join())
}

// Run concurrent scraping
val results = Log.run {
  IO.run {
    Async.run {
      scrapeUrls(List(
        "https://example.com",
        "https://scala-lang.org",
        "https://github.com"
      ))
    }
  }
}
```

## Configuration Loading

```scala
import in.rcard.yaes.System.*
import in.rcard.yaes.Raise.*
import in.rcard.yaes.Log.*

case class AppConfig(
  host: String,
  port: Int,
  dbUrl: String,
  logLevel: String
)

def loadConfig(using System, Raise[String], Log): AppConfig = {
  val logger = Log.getLogger("Config")
  logger.info("Loading application configuration")
  
  val host = System.env[String]("HOST", "localhost")
  val port = System.env[Int]("PORT").getOrElse {
    Raise.raise("PORT environment variable is required")
  }
  val dbUrl = System.property[String]("db.url").getOrElse {
    Raise.raise("db.url system property is required")
  }
  val logLevel = System.env[String]("LOG_LEVEL", "INFO")
  
  AppConfig(host, port, dbUrl, logLevel)
}

// Load configuration
val config = Raise.either {
  Log.run {
    System.run {
      loadConfig
    }
  }
}
```

## Error Handling Pipeline

```scala
import in.rcard.yaes.Raise.*
import in.rcard.yaes.IO.*

sealed trait ValidationError
case object InvalidEmail extends ValidationError
case object InvalidAge extends ValidationError
case class DatabaseError(msg: String) extends ValidationError

case class User(email: String, age: Int)

def validateEmail(email: String)(using Raise[ValidationError]): String = {
  if (email.contains("@")) email
  else Raise.raise(InvalidEmail)
}

def validateAge(age: Int)(using Raise[ValidationError]): Int = {
  if (age >= 0 && age <= 120) age
  else Raise.raise(InvalidAge)
}

def saveUser(user: User)(using IO, Raise[ValidationError]): Long = {
  // Simulate database operation that might fail
  if (user.email.endsWith("@spam.com")) {
    Raise.raise(DatabaseError("Spam domain not allowed"))
  }
  42L // User ID
}

def createUser(email: String, age: Int)(using IO, Raise[ValidationError]): Long = {
  val validEmail = validateEmail(email)
  val validAge = validateAge(age)
  val user = User(validEmail, validAge)
  saveUser(user)
}

// Handle all errors
val result = Raise.either {
  IO.run {
    createUser("john@example.com", 25)
  }
}

result match {
  case Right(userId) => println(s"User created with ID: $userId")
  case Left(InvalidEmail) => println("Invalid email format")
  case Left(InvalidAge) => println("Invalid age")
  case Left(DatabaseError(msg)) => println(s"Database error: $msg")
}
```
