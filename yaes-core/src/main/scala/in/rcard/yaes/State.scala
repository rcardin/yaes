package in.rcard.yaes

import in.rcard.yaes.Yaes.Handler

object State {

  type State[S] = Yaes[Unsafe[S]]

  def get[S](using interpreter: State[S]): S = {
    interpreter.unsafe.run(StateOp.Get())
  }

  def set[S](value: S)(using interpreter: State[S]): Unit = {
    interpreter.unsafe.run(StateOp.Set(value))
  }

  enum StateOp[S, A] {
    case Get()         extends StateOp[S, S]
    case Set(value: S) extends StateOp[S, Unit]
  }

  def run[S, A](initialState: S)(block: State[S] ?=> A): (S, A) = {
    val handler = new Yaes.Handler[State.Unsafe[S], A, (S, A)] {
      override def handle(program: State[S] ?=> A): (S, A) = {
        val interpreter = new Unsafe[S] {
          private var state: S = initialState

          override def run[A](op: StateOp[S, A]): A = op match {
            case StateOp.Get() =>
              state
              ().asInstanceOf[A]
            case StateOp.Set(value) =>
              state = value
              ().asInstanceOf[A]
          }
        }
        program(using Yaes(interpreter))
      }
    }

    Yaes.handle(block)(using handler)
  }

  trait Unsafe[S] {
    def run[A](op: StateOp[S, A]): A
  }

}
