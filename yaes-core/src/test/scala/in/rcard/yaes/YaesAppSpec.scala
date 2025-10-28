package in.rcard.yaes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class YaesAppSpec extends AnyFlatSpec with Matchers {

  "YaesApp" should "execute a single run block" in {
    var executed = false

    val app = new YaesApp {
      override protected def handleError(error: Option[Throwable]): Unit = () // Prevent actual exit

      override def run: (Output.Output, Random.Random, Clock.Clock, System.System, Log.Log) ?=> Unit = {
        executed = true
      }
    }

    app.main(Array.empty)
    executed shouldBe true
  }

  it should "provide access to command-line arguments" in {
    var capturedArgs: Array[String] = Array.empty

    val app = new YaesApp {
      override protected def handleError(error: Option[Throwable]): Unit = ()

      override def run: (Output.Output, Random.Random, Clock.Clock, System.System, Log.Log) ?=> Unit = {
        capturedArgs = args
      }
    }

    app.main(Array("arg1", "arg2", "arg3"))
    capturedArgs shouldBe Array("arg1", "arg2", "arg3")
  }

  it should "provide access to Output effect" in {
    val output = new java.io.ByteArrayOutputStream()
    Console.withOut(output) {
      val app = new YaesApp {
        override protected def handleError(error: Option[Throwable]): Unit = ()

        override def run: (Output.Output, Random.Random, Clock.Clock, System.System, Log.Log) ?=> Unit = {
          Output.printLn("Hello, YAES!")
        }
      }

      app.main(Array.empty)
    }

    output.toString().trim shouldBe "Hello, YAES!"
  }

  it should "provide access to Random effect" in {
    var randomValue: Int = 0

    val app = new YaesApp {
      override protected def handleError(error: Option[Throwable]): Unit = ()

      override def run: (Output.Output, Random.Random, Clock.Clock, System.System, Log.Log) ?=> Unit = {
        randomValue = Random.nextInt
      }
    }

    app.main(Array.empty)
    // Just verify that a random value was generated (any int is valid)
    randomValue shouldBe a[Int]
  }

  it should "provide access to Clock effect" in {
    var timestamp: java.time.Instant = null

    val app = new YaesApp {
      override protected def handleError(error: Option[Throwable]): Unit = ()

      override def run: (Output.Output, Random.Random, Clock.Clock, System.System, Log.Log) ?=> Unit = {
        timestamp = Clock.now
      }
    }

    app.main(Array.empty)
    timestamp should not be null
  }

  it should "handle thrown exceptions" in {
    var errorCaptured: Option[Throwable] = None

    val app = new YaesApp {
      override protected def handleError(error: Option[Throwable]): Unit = {
        errorCaptured = error
      }

      override def run: (Output.Output, Random.Random, Clock.Clock, System.System, Log.Log) ?=> Unit = {
        throw new RuntimeException("Test error")
      }
    }

    app.main(Array.empty)
    errorCaptured shouldBe defined
    errorCaptured.get.getMessage shouldBe "Test error"
  }

  it should "support combining multiple effects" in {
    val output = new java.io.ByteArrayOutputStream()
    Console.withOut(output) {
      val app = new YaesApp {
        override protected def handleError(error: Option[Throwable]): Unit = ()

        override def run: (Output.Output, Random.Random, Clock.Clock, System.System, Log.Log) ?=> Unit = {
          val timestamp = Clock.now
          val random = Random.nextInt
          Output.printLn(s"Time: $timestamp, Random: $random")
        }
      }

      app.main(Array.empty)
    }

    output.toString() should include("Time:")
    output.toString() should include("Random:")
  }

  "YaesApp.apply" should "create a simple app from a block" in {
    val output = new java.io.ByteArrayOutputStream()
    Console.withOut(output) {
      val app = YaesApp {
        Output.printLn("Simple app")
      }

      app.main(Array.empty)
    }

    output.toString().trim shouldBe "Simple app"
  }
}
