package example

import io.yaes.Raise

class NoOp {
  def run[A](block: (io.yaes.Sync, Raise[Throwable]) ?=> A): A = ???
}
