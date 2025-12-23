
# State Effect

The `State[S]` effect enables stateful computations in a purely functional manner. It provides operations to get, set, update, and use state values within a controlled scope, allowing you to work with mutable state without compromising functional programming principles.

## Overview

The `State` effect manages a single piece of state of type `S` throughout a computation. All state operations are performed within the context of a `State.run` block, which provides isolation and ensures the state is properly managed.

**Note:** The State effect is not thread-safe. Use appropriate synchronization mechanisms when accessing state from multiple threads.

## Basic Usage

### Getting and Setting State

```scala
import in.rcard.yaes.State.*

val (finalState, result) = State.run(0) {
  val current = State.get[Int]
  State.set(current + 5)
  State.get[Int]
}
// finalState = 5, result = 5
```

### Updating State

```scala
import in.rcard.yaes.State.*

val (finalState, result) = State.run(10) {
  val doubled = State.update[Int](_ * 2)
  val tripled = State.update[Int](_ * 3)
  tripled
}
// finalState = 60, result = 60
```

### Using State Without Modification

```scala
import in.rcard.yaes.State.*

case class User(name: String, age: Int)

val (finalState, nameLength) = State.run(User("Alice", 30)) {
  State.use[User, Int](_.name.length)
}
// finalState = User("Alice", 30), nameLength = 5
```

## Advanced Examples

### Counter with Operations

```scala
import in.rcard.yaes.State.*

def increment(using State[Int]): Int = State.update(_ + 1)
def decrement(using State[Int]): Int = State.update(_ - 1)
def multiply(factor: Int)(using State[Int]): Int = State.update(_ * factor)

val (finalState, result) = State.run(5) {
  increment
  increment
  multiply(3)
  decrement
}
// finalState = 20, result = 20
```

### Managing Complex State

```scala
import in.rcard.yaes.State.*

case class GameState(score: Int, lives: Int, level: Int)

def addScore(points: Int)(using State[GameState]): GameState =
  State.update(state => state.copy(score = state.score + points))

def loseLife(using State[GameState]): GameState =
  State.update(state => state.copy(lives = state.lives - 1))

def nextLevel(using State[GameState]): GameState =
  State.update(state => state.copy(level = state.level + 1))

val (finalState, _) = State.run(GameState(0, 3, 1)) {
  addScore(100)
  addScore(250)
  loseLife
  nextLevel
  State.get[GameState]
}
// finalState = GameState(350, 2, 2)
```

### Combining with Other Effects

```scala
import in.rcard.yaes.State.*
import in.rcard.yaes.Random.*

def randomWalk(steps: Int)(using State[Int], Random): Int = {
  if (steps <= 0) State.get[Int]
  else {
    val direction = if (Random.nextBoolean) 1 else -1
    State.update(_ + direction)
    randomWalk(steps - 1)
  }
}

val (finalPosition, result) = State.run(0) {
  Random.run {
    randomWalk(10)
  }
}
```

## Available Operations

### `State.get[S]`
Retrieves the current state value without modifying it.

```scala
val currentValue = State.get[String]
```

### `State.set[S](value: S)`
Sets the state to a new value and returns the previous state value.

```scala
val previousValue = State.set("new value")
```

### `State.update[S](f: S => S)`
Updates the state using a transformation function and returns the new state value.

```scala
val newValue = State.update[Int](_ * 2)
```

### `State.use[S, A](f: S => A)`
Applies a function to the current state and returns the result without modifying the state.

```scala
val computed = State.use[String, Int](_.length)
```

### `State.run[S, A](initialState: S)(block: State[S] ?=> A)`
Runs a stateful computation with an initial state value, returning both the final state and the computation result.

```scala
val (finalState, result) = State.run(initialValue) {
  // stateful computation
}
```

## Best Practices

1. **Keep state types simple**: Use case classes or simple data types for better maintainability
2. **Minimize state mutations**: Prefer functional updates over direct mutations
3. **Use `State.use` for read-only operations**: When you only need to read from state without modifying it
4. **Thread safety**: Remember that State is not thread-safe - use appropriate synchronization for concurrent access
5. **Combine with other effects**: State works well with other λÆS effects like `Random`, `Raise`, and `IO`

## Thread Safety Considerations

The State effect implementation is not thread-safe. If you need concurrent access to state:

- Use separate `State.run` blocks for different threads
- Implement proper synchronization mechanisms around shared state
- Consider using atomic operations or concurrent data structures for the state type

```scala
// Safe: Each thread has its own state
val thread1Result = Future {
  State.run(0) { /* stateful computation */ }
}

val thread2Result = Future {
  State.run(0) { /* stateful computation */ }
}
```
