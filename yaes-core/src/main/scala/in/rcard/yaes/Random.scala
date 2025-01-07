package in.rcard.yaes

trait NonDeterministic {
  def nextInt(): Int
  def nextBoolean(): Boolean
  def nextDouble(): Double
  def nextLong(): Long
}

class DefaultNonDeterministic extends NonDeterministic {
  def nextInt(): Int         = scala.util.Random.nextInt()
  def nextLong(): Long       = scala.util.Random.nextLong()
  def nextBoolean(): Boolean = scala.util.Random.nextBoolean()
  def nextDouble(): Double   = scala.util.Random.nextDouble()
}

type Random = Effect[NonDeterministic]

object Random {

  def apply[A](block: => A): Random ?=> A = block

  def nextInt(using r: Random): Int         = r.sf.nextInt()
  def nextBoolean(using r: Random): Boolean = r.sf.nextBoolean()
  def nextDouble(using r: Random): Double   = r.sf.nextDouble()
  def nextLong(using r: Random): Long       = r.sf.nextLong()

  def run[A](block: Random ?=> A): A = {
    block(using Effect(new DefaultNonDeterministic))
  }
}
