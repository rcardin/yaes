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

  it should "release resources correctly if an error happens after the acquiring process" in {

    val results = scala.collection.mutable.ListBuffer[String]()
    val actualException = intercept[RuntimeException] {
      Resource.run {
        val acquired = Resource.install({
          "Acquired"
        }) { res =>
          results += res
          results += "Released"
        }
        throw new RuntimeException("An error occurred after acquiring the resource")
      }
    }

    actualException shouldBe a[RuntimeException]
    actualException.getMessage shouldEqual "An error occurred after acquiring the resource"
    results shouldEqual List("Acquired", "Released")
  }

  it should "release resources correctly if an error happens during the acquiring process" in {

    val results = scala.collection.mutable.ListBuffer[String]()
    val actualException = intercept[RuntimeException] {
      Resource.run {
        Resource.install[String]({
          throw new RuntimeException("An error occurred during acquiring the resource")
        }) { res =>
          results += res
          results += "Released"
        }
      }
    }

    actualException shouldBe a[RuntimeException]
    actualException.getMessage shouldEqual "An error occurred during acquiring the resource"
    results shouldEqual List()
  }
}
