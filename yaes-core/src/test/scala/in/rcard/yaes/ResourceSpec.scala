package in.rcard.yaes

import scala.concurrent.duration.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

class ResourceSpec extends AnyFlatSpec with Matchers {

  "install" should "acquire and release resources correctly if no error happens" in {
    val actualResource = Resource.run {
      val resource = Resource.install({
        ListBuffer("1")
      }) { res =>
        res += "3"
      }
      resource += "2"
      resource
    }

    actualResource shouldEqual List("1", "2", "3")
  }

  it should "release resources correctly if an error happens after the acquiring process" in {

    val results         = ListBuffer[String]()
    val actualException = intercept[RuntimeException] {
      Resource.run {
        val acquired = Resource.install({
          results += "1"
        }) { _ =>
          results += "3"
        }
        results += "2"
        throw new RuntimeException("An error occurred after acquiring the resource")
      }
    }

    actualException shouldBe a[RuntimeException]
    actualException.getMessage shouldEqual "An error occurred after acquiring the resource"
    results shouldEqual List("1", "2", "3")
  }

  it should "not call the release function if an error happens during the acquiring process" in {

    val results         = ListBuffer[String]()
    val actualException = intercept[RuntimeException] {
      Resource.run {
        Resource.install[String]({
          throw new RuntimeException("An error occurred during acquiring the resource")
        }) { res =>
          results += res
          results += "1"
        }
      }
    }

    actualException shouldBe a[RuntimeException]
    actualException.getMessage shouldEqual "An error occurred during acquiring the resource"
    results shouldEqual List()
  }

  it should "rethrow the exception if the resource release fails" in {
    val results         = ListBuffer[String]()
    val actualException = intercept[RuntimeException] {
      Resource.run {
        Resource.install({
          "1"
        }) { res =>
          results += res
          throw new RuntimeException("An error occurred during resource release")
        }
      }
    }

    actualException shouldBe a[RuntimeException]
    actualException.getMessage shouldEqual "An error occurred during resource release"
    results shouldEqual List("1")
  }

  it should "rethrow the original exception thrown during resource usage if also the release fails" in {
    val results          = ListBuffer[String]()
    val usageException   = new RuntimeException("Usage exception")
    val releaseException = new RuntimeException("Release exception")
    val actualException  = intercept[RuntimeException] {
      Resource.run {
        Resource.install[String]({
          results += "1"
          "Acquired"
        }) { _ =>
          results += "3"
          throw releaseException
        }
        results += "2"
        throw usageException
      }
    }

    actualException shouldBe a[RuntimeException]
    actualException.getMessage shouldEqual "Usage exception"

    results shouldEqual List("1", "2", "3")
  }

  it should "release resources in the reverse order of acquisition" in {
    val results = ListBuffer[String]()
    Resource.run {
      val res1 = Resource.install({
        results += "1"
        "Resource 1"
      }) { _ =>
        results += "6"
      }
      results += "2"
      val res2 = Resource.install({
        results += "3"
        "Resource 2"
      }) { _ =>
        results += "5"
      }
      results += "4"
    }

    results shouldEqual List("1", "2", "3", "4", "5", "6")
  }

  it should "release other resources if an error occurs during the release of a resource" in {
    val results         = ListBuffer[String]()
    val actualException = intercept[RuntimeException] {
      Resource.run {
        val res1 = Resource.install({
          results += "1"
          "Resource 1"
        }) { _ =>
          results += "5"
        }
        val res2 = Resource.install({
          results += "2"
          "Resource 2"
        }) { _ =>
          results += "4"
          throw new RuntimeException("Error during release of Resource 2")
        }
        results += "3"
      }
    }

    actualException shouldBe a[RuntimeException]
    actualException.getMessage shouldEqual "Error during release of Resource 2"

    results shouldEqual List("1", "2", "3", "4", "5")
  }

  it should "close the available resources if an error occurs during the acquiring process" in {
    val results         = ListBuffer[String]()
    val actualException = intercept[RuntimeException] {
      Resource.run {
        Resource.install({
          results += "1"
        }) { _ =>
          results += "3"
        }
        Resource.install({
          results += "2"
          throw new RuntimeException("Error during acquiring")
        }) { _ =>
          results += "Nope!"
        }
        results += "Nope!"
      }
    }

    actualException shouldBe a[RuntimeException]
    actualException.getMessage shouldEqual "Error during acquiring"
    results shouldEqual List("1", "2", "3")
  }

  it should "integrate with the Raise effect" in {
    val results     = ListBuffer[String]()
    val actualError =
      Raise.run {
        Resource.run {
          Resource.install({
            results += "1"
            "Resource 1"
          }) { _ =>
            results += "3"
          }
          results += "2"
          Raise.raise("An error occurred during resource usage")
        }
      }

    actualError shouldBe "An error occurred during resource usage"
    results shouldEqual List("1", "2", "3")
  }

  it should "release a resource even if the owner fiber is canceled" in {
    val results = ListBuffer[String]()
    Async.run {
      Resource.run {
        val fiber: Fiber[String] = Async.fork {
          val resource = Resource.install({
            Async.delay(200.millis)
            results += "1"
            "Resource 1"
          }) { _ =>
            results += "2"
          }
          Async.delay(1.second)
          resource
        }
        Async.delay(500.millis)
        fiber.cancel()
      }
    }

    results shouldEqual List("1", "2")
  }
}
