
# Input & Output Effects

The `Input` and `Output` effects provide console I/O operations in a functional and testable manner.

## Input Effect

The `Input` effect handles reading from the console.

### Basic Usage

```scala
import in.rcard.yaes.Input.*
import in.rcard.yaes.Raise.*
import java.io.IOException

val userInput: (Input, Raise[IOException]) ?=> String = Input.readLn()
```

### Running Input Effects

```scala
import in.rcard.yaes.Input.*
import in.rcard.yaes.Raise.*
import java.io.IOException

val result: Either[IOException, String] = Raise.either {
  Input.run {
    Input.readLn()
  }
}
```

## Output Effect

The `Output` effect handles writing to the console.

### Basic Usage

```scala
import in.rcard.yaes.Output.*

val program: Output ?=> Unit = {
  Output.printLn("Hello, world!")
  Output.printLn("How are you today?")
}
```

### Error Output

Write to standard error:

```scala
import in.rcard.yaes.Output.*

val errorProgram: Output ?=> Unit = {
  Output.printErr("Error: Something went wrong!")
}
```

### Running Output Effects

```scala
import in.rcard.yaes.Output.*

Output.run {
  Output.printLn("This will be printed to console")
}
```

## Combining Input and Output

### Interactive Programs

```scala
import in.rcard.yaes.Input.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Raise.*
import java.io.IOException

def greetUser(using Input, Output, Raise[IOException]): Unit = {
  Output.printLn("What's your name?")
  val name = Input.readLn()
  Output.printLn(s"Hello, $name! Nice to meet you.")
}

// Run the interactive program
val result: Either[IOException, Unit] = Raise.either {
  Output.run {
    Input.run {
      greetUser
    }
  }
}
```

### Menu System

```scala
import in.rcard.yaes.Input.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Raise.*
import java.io.IOException

def showMenu(using Output): Unit = {
  Output.printLn("=== Main Menu ===")
  Output.printLn("1. Start game")
  Output.printLn("2. View scores") 
  Output.printLn("3. Exit")
  Output.printLn("Choose an option:")
}

def handleMenuChoice(choice: String)(using Output): String = choice match {
  case "1" => 
    Output.printLn("Starting game...")
    "game"
  case "2" => 
    Output.printLn("Viewing scores...")
    "scores"
  case "3" => 
    Output.printLn("Goodbye!")
    "exit"
  case _ => 
    Output.printLn("Invalid choice. Try again.")
    "invalid"
}

def menuLoop(using Input, Output, Raise[IOException]): String = {
  showMenu
  val choice = Input.readLn()
  handleMenuChoice(choice)
}
```

## Advanced Examples

### Form Input with Validation

```scala
import in.rcard.yaes.Input.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Raise.*
import java.io.IOException

case class UserInfo(name: String, email: String, age: Int)

def readUserInfo(using Input, Output, Raise[String | IOException]): UserInfo = {
  // Read name
  Output.printLn("Enter your name:")
  val name = Input.readLn()
  if (name.trim.isEmpty) Raise.raise("Name cannot be empty")
  
  // Read email
  Output.printLn("Enter your email:")
  val email = Input.readLn()
  if (!email.contains("@")) Raise.raise("Invalid email format")
  
  // Read age
  Output.printLn("Enter your age:")
  val ageStr = Input.readLn()
  val age = try {
    ageStr.toInt
  } catch {
    case _: NumberFormatException => Raise.raise("Age must be a number")
  }
  
  if (age < 0 || age > 120) Raise.raise("Age must be between 0 and 120")
  
  UserInfo(name.trim, email.trim, age)
}

// Usage
val userInfo = Raise.either {
  Output.run {
    Input.run {
      readUserInfo
    }
  }
}

userInfo match {
  case Right(info) => println(s"Welcome ${info.name}!")
  case Left(error) => println(s"Error: $error")
}
```

### Console-based Game

```scala
import in.rcard.yaes.Input.*
import in.rcard.yaes.Output.*
import in.rcard.yaes.Random.*
import in.rcard.yaes.Raise.*
import java.io.IOException

def guessingGame(using Input, Output, Random, Raise[IOException]): Unit = {
  val secret = Random.nextInt(100) + 1
  var attempts = 0
  var won = false
  
  Output.printLn("I'm thinking of a number between 1 and 100!")
  
  while (!won && attempts < 7) {
    Output.printLn(s"Attempt ${attempts + 1}/7 - Enter your guess:")
    val guessStr = Input.readLn()
    
    try {
      val guess = guessStr.toInt
      attempts += 1
      
      if (guess == secret) {
        Output.printLn(s"Congratulations! You guessed it in $attempts attempts!")
        won = true
      } else if (guess < secret) {
        Output.printLn("Too low!")
      } else {
        Output.printLn("Too high!")
      }
    } catch {
      case _: NumberFormatException =>
        Output.printLn("Please enter a valid number!")
    }
  }
  
  if (!won) {
    Output.printLn(s"Sorry! The number was $secret. Better luck next time!")
  }
}

// Run the game
Raise.either {
  Random.run {
    Output.run {
      Input.run {
        guessingGame
      }
    }
  }
}
```

## Implementation Details

### Input Effect
- Uses `scala.io.StdIn` under the hood
- Reading can throw `IOException`
- Requires `Raise[IOException]` effect

### Output Effect  
- Uses `scala.Console` under the hood
- Operations don't throw exceptions (silently ignore errors)
- No additional effect requirements

## Best Practices

- Always handle `IOException` when using `Input`
- Use `Output` for user feedback and debugging
- Combine with `Raise` for robust error handling
- Keep I/O operations at application boundaries
- Consider using higher-level effects for complex interactive programs
