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

      var innerState = initialState

      override def handle(program: State[S] ?=> A): (S, A) = {
        val interpreter = new Unsafe[S] {

          override def run[A](op: StateOp[S, A]): A = op match {
            case StateOp.Get() =>
              innerState
            case StateOp.Set(value) =>
              innerState = value
              ().asInstanceOf[A]
          }
        }
        val result = program(using Yaes(interpreter))
        (innerState, result)
      }
    }

    Yaes.handle(block)(using handler)
  }

  trait Unsafe[S] {
    def run[A](op: StateOp[S, A]): A
  }

}
