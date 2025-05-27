package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ResourceSpec extends AnyFlatSpec with Matchers {

  "install" should "acquire and release resources correctly if no error happens" in {
    val results = scala.collection.mutable.ListBuffer[String]()
    val actualResource: String = Resource.run {
      Resource.install({
        "Acquired"
      }) { res =>
        results += res
        results += "Released"
      }
    }

    actualResource shouldEqual "Acquired"
    results shouldEqual List("Acquired", "Released")
  }
}
