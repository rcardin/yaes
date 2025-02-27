package in.rcard.yaes

trait Random extends Effect {
  def nextInt(): Int
  def nextBoolean(): Boolean
  def nextDouble(): Double
  def nextLong(): Long
}

object Random {

  def apply[A](block: => A): Random ?=> A = block

  def nextInt(using r: Random): Int         = r.nextInt()
  def nextBoolean(using r: Random): Boolean = r.nextBoolean()
  def nextDouble(using r: Random): Double   = r.nextDouble()
  def nextLong(using r: Random): Long       = r.nextLong()

  def run[A](block: Random ?=> A): A = {
    val handler = new Effect.Handler[Random, A, A] {
      override def handle(program: Random ?=> A): A = program(using Random.unsafe)
    }
    Effect.handle(block)(using handler)
  }

  private val unsafe: Random = new Random {
    override def nextInt(): Int         = scala.util.Random.nextInt()
    override def nextLong(): Long       = scala.util.Random.nextLong()
    override def nextBoolean(): Boolean = scala.util.Random.nextBoolean()
    override def nextDouble(): Double   = scala.util.Random.nextDouble()
  }
}
