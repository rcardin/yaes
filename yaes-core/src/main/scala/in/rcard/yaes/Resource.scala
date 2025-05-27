package in.rcard.yaes

object Resource {

  type Resource = Yaes[Resource.Unsafe]

  def apply[A](block: => A)(using res: Resource): A = block

  def install[A](acquire: => A)(release: A => Unit)(using res: Resource): A = {
    res.unsafe.install(acquire)(release)
  }

  def run[A](block: Resource ?=> A): A = {
    val handler = new Yaes.Handler[Resource.Unsafe, A, A] {
      override def handle(program: Resource ?=> A): A = {
        program(using Yaes(Resource.unsafe))
      }

    }
    Yaes.handle(block)(using handler)
  }

  private val unsafe: Resource.Unsafe = new Resource.Unsafe {
    override def install[A](acquire: => A)(release: A => Unit): A = {
      var acquired: A = null.asInstanceOf[A]
      try {
        acquired = acquire
        return acquired
      } finally {
        if (acquired != null) {
          release(acquired)
        }
      }
    }
  }

  trait Unsafe {
    def install[A](acquire: => A)(release: A => Unit): A
  }
}
