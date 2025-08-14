package in.rcard.yaes

import in.rcard.yaes.Yaes.Handler

object State {

  type State[S] = Yaes[Unsafe[S]]

  def get[S](using interpreter: State[S]): S = {
    interpreter.unsafe.run(StateOp.Get())
  }

  def set[S](value: S)(using interpreter: State[S]): S = {
    interpreter.unsafe.run(StateOp.Set(value))
  }

  def update[S](f: S => S)(using interpreter: State[S]): S = {
    interpreter.unsafe.run(StateOp.Update(f))
  }

  def use[S, A](f: S => A)(using interpreter: State[S]): A = {
    interpreter.unsafe.run(StateOp.Use(f))
  }

  enum StateOp[S, A] {
    case Get()             extends StateOp[S, S]
    case Set(value: S)     extends StateOp[S, S]
    case Update(f: S => S) extends StateOp[S, S]
    case Use(f: S => A)    extends StateOp[S, A]
  }

  def run[S, A](initialState: S)(block: State[S] ?=> A): (S, A) = {

    var currentState = initialState

    val handler = new Yaes.Handler[State.Unsafe[S], A, A] {

      override def handle(program: State[S] ?=> A): A = {
        val interpreter = new Unsafe[S] {

          override def run[A](op: StateOp[S, A]): A = op match {
            case StateOp.Get() =>
              currentState
            case StateOp.Set(value) =>
              val oldState = currentState
              currentState = value
              oldState
            case StateOp.Update(f) =>
              currentState = f(currentState)
              currentState
            case StateOp.Use(f) =>
              f(currentState)
          }
        }
        program(using Yaes(interpreter))
      }
    }

    val result = Yaes.handle(block)(using handler)
    (currentState, result)
  }

  trait Unsafe[S] {
    def run[A](op: StateOp[S, A]): A
  }

}
