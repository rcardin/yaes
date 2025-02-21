package in.rcard.yaes

import in.rcard.yaes.Effect.Handler

trait NonDeterministic {
  def nextInt(): Int
  def nextBoolean(): Boolean
  def nextDouble(): Double
  def nextLong(): Long
}

type Random = Effect[NonDeterministic]

object Random {

  def apply[A](block: => A): Random ?=> A = block

  def nextInt(using r: Random): Int         = r.sf.nextInt()
  def nextBoolean(using r: Random): Boolean = r.sf.nextBoolean()
  def nextDouble(using r: Random): Double   = r.sf.nextDouble()
  def nextLong(using r: Random): Long       = r.sf.nextLong()

  def run[A](block: Random ?=> A): A = {
    Effect.handle(block).`with`(Random.live)
  }

  val live: Handler[NonDeterministic] = new Handler[NonDeterministic] {
    val unsafe: NonDeterministic = new NonDeterministic {
      override def nextInt(): Int         = scala.util.Random.nextInt()
      override def nextLong(): Long       = scala.util.Random.nextLong()
      override def nextBoolean(): Boolean = scala.util.Random.nextBoolean()
      override def nextDouble(): Double   = scala.util.Random.nextDouble()
    }
  }
}
