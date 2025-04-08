package in.rcard.yaes

import java.time.Instant
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

class AlternateDefinition extends AsyncFlatSpec with Matchers {

  class Eff[-E](val run: scala.collection.mutable.Map[String, Any])
  object Eff {
    def access[E](using t: Tag[E])(using env: Eff[E]): E = {
      env.run.get(t.tag).get.asInstanceOf[E]
    }

    def provide[E](value: E)(using t: Tag[E])(using eff: Eff[?]): Unit = {
      eff.run.update(t.tag, value)
    }
  }

  trait Clock {
    def now: Instant
  }

  object Clock {
    def now(using clockEff: Eff[Clock]): Instant =
      Eff.access[Clock].now

    def run[E, A](fa: Eff[E & Clock] ?=> A)(using newEff: Eff[E]): A = {
      val clock = new Clock {
        def now = Instant.now()
      }

      Eff.provide[Clock](clock)
      fa(using newEff)
    }
  }

  trait Output {
    def println(s: String): Unit
  }
  object Output {
    def println(s: String)(using env: Eff[Output]): Unit =
      Eff.access[Output].println(s)

    def run[E, A](fa: Eff[E & Output] ?=> A)(using newEff: Eff[E]): A = {
      val output = new Output {
        def println(s: String): Unit = System.out.println(s)
      }

      Eff.provide[Output](output)
      fa(using newEff)
    }
  }

  "The new definition" should "work" in {
    val program: (Eff[Clock] & Eff[Output]) ?=> Instant = {
      val now: Instant = Clock.now
      Output.println("Current time: " + now)
      now
    }
    val actualResult: Eff[Any] ?=> Instant = Output.run { Clock.run { program } }

    actualResult(using new Eff(scala.collection.mutable.Map())) shouldBe a[Instant]
  }
}
