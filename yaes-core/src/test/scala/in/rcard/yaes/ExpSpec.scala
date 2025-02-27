package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.boundary.Label
import scala.util.boundary.break
import scala.util.boundary

class ExpSpec extends AnyFlatSpec with Matchers {

  trait Eff
  trait Eff1 extends Eff
  object Eff1 {
    def apply[A](block: => A): Eff1 ?=> A = block
    def run[A](block: Eff1 ?=> A): A = {
      given Eff1 = new Eff1 {}
      block
    }
  }
  trait Eff2 extends Eff
  object Eff2 {
    def apply[A](block: => A): Eff2 ?=> A = block
    def run[A](block: Eff2 ?=> A): A = {
      given Eff2 = new Eff2 {}
      block
    }
  }

  extension [F <: Eff, A](inline eff: F ?=> A) {
    inline def flatMap[B](inline f: A => F ?=> B): F ?=> B = f(eff)
    inline def map[B](inline f: A => B): F ?=> B           = eff.flatMap(a => f(a))
  }

  "New Eff" should "be able to flatMap" in {
    val p: (Eff1, Eff2) ?=> Int = for {
      a <- Eff1 { 42 }
      b <- Eff2 { 42 }
    } yield {
      a + b
    }

    Eff1.run { Eff2.run { p } } shouldEqual 84
  }

  it should "also be able to use direct style" in {
    val a: Eff1 ?=> Int         = Eff1 { 42 }
    val b: Eff2 ?=> Int         = Eff2 { 42 }
    val p: (Eff1, Eff2) ?=> Int = a + b

    Eff1.run { Eff2.run { p } } shouldEqual 84
  }

  trait Rai[E] {
    def raise(e: => E): Nothing
  }

  object Rai {
    def raise[E](e: => E)(using rai: Rai[E]): Nothing = rai.raise(e)

    def raiseToEither[E, A](using label: Label[Either[E, A]]): Rai[E] =
      Rai.apply(e => Left(e))

    def either[E, A](program: Rai[E] ?=> A): Either[E, A] = {
      boundary[Either[E, A]] {
        given rai: Rai[E] = raiseToEither
        Right(program)
      }
    }

    def apply[E, A](f: E => A)(using Label[A]): Rai[E] =
      new Rai[E] {
        def raise(error: => E): Nothing =
          break(f(error))
      }
  }

  "Rai" should "be able to raise an error as an either" in {
    val result: Either[String, Int] = Rai.either {
      Rai.raise("error")
    }

    result shouldBe Left("error")
  }
}
