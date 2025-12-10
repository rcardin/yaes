package in.rcard.yaes.instances

import cats.ApplicativeError
import in.rcard.yaes.Raise
import in.rcard.yaes.instances.raise.given
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RaiseInstancesSpec extends AnyFlatSpec with Matchers {

  "implicit resolution" should "work for RaiseMonadError" in {
    type OrError[A] = Raise[String] ?=> A
    val actual: OrError[Int] = attemptDivideApplicativeError[OrError](30, 0)
    Raise.run { actual } shouldBe "divisor is zero"
  }
}

private def attemptDivideApplicativeError[F[_]](x: Int, y: Int)(implicit
    ae: ApplicativeError[F, String]
): F[Int] = {
  if (y == 0) ae.raiseError("divisor is zero")
  else {
    ae.pure(x / y)
  }
}
